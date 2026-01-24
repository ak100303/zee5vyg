const functions = require('firebase-functions');
const admin = require('firebase-admin');
const axios = require('axios');

admin.initializeApp();
const db = admin.firestore();

/**
 * Scheduled Function: Runs every hour on the dot.
 * Cloud Scheduler handles the timing, logic handles the IST recording.
 */
exports.recordAqiHourly = functions.pubsub
    .schedule('0 * * * *') // Runs at the start of every hour
    .timeZone('Asia/Kolkata') // Set Scheduler to IST
    .onRun(async (context) => {
        // 1. Get secrets from Firebase config
        // Note: You must run `firebase functions:config:set waqi.token="..." user.uid="..."`
        const config = functions.config();
        
        if (!config.waqi || !config.waqi.token || !config.user || !config.user.uid) {
            console.error("Missing Firebase configuration. Run: firebase functions:config:set waqi.token=\"...\" user.uid=\"...\"");
            return null;
        }

        const token = config.waqi.token;
        const uid = config.user.uid;

        try {
            // 2. BEACON LOGIC: Read user's last known location from Firestore
            const userDoc = await db.collection('users').doc(uid).get();
            const lastLocation = userDoc.exists ? userDoc.data().lastLocation : null;

            let apiUrl;
            if (lastLocation && lastLocation.lat && lastLocation.lon) {
                console.log(`Beacon found! Using GPS coordinates: ${lastLocation.lat}, ${lastLocation.lon}`);
                apiUrl = `https://api.waqi.info/feed/geo:${lastLocation.lat};${lastLocation.lon}/?token=${token}`;
            } else {
                console.log("No beacon found. Falling back to default IP-based hyperlocal feed.");
                apiUrl = `https://api.waqi.info/feed/here/?token=${token}`;
            }

            // 3. Fetch data from WAQI API
            const response = await axios.get(apiUrl);
            if (response.data.status !== "ok") {
                throw new Error(`WAQI API Error: ${response.data.data}`);
            }

            const data = response.data.data;

            // 4. IST Timezone Logic (UTC + 5:30)
            const now = new Date();
            const istOffset = 5.5 * 60 * 60 * 1000;
            const nowIST = new Date(now.getTime() + istOffset);
            
            const year = nowIST.getUTCFullYear();
            const month = String(nowIST.getUTCMonth() + 1).padStart(2, '0');
            const day = String(nowIST.getUTCDate()).padStart(2, '0');
            const dateStr = `${year}-${month}-${day}`;
            const hour = nowIST.getUTCHours();

            const record = {
                aqi: data.aqi,
                cityName: data.city.name,
                timestamp: admin.firestore.Timestamp.now(),
                date: dateStr
            };

            const dayDocRef = db.collection('users').doc(uid).collection('history').document(dateStr);

            // 5. Save Hourly Snapshot
            await dayDocRef.collection('hourly').doc(hour.toString()).set(record);

            // 6. Update Daily Max (using Transaction for safety)
            await db.runTransaction(async (transaction) => {
                const doc = await transaction.get(dayDocRef);
                const currentMax = doc.exists ? (doc.data().aqi || 0) : 0;
                
                if (data.aqi > currentMax) {
                    transaction.set(dayDocRef, {
                        aqi: data.aqi,
                        cityName: data.city.name,
                        date: dateStr
                    }, { merge: true });
                }
            });

            console.log(`Successfully recorded IST hour [${hour}] for ${data.city.name} with AQI: ${data.aqi}`);
            return null;

        } catch (error) {
            console.error("Critical Cloud Function Error:", error.message);
            return null;
        }
    });
