const admin = require('firebase-admin');
const axios = require('axios');

// Initialize Firebase using the secret service account from GitHub Actions
const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();
const uid = process.env.USER_UID;
const token = process.env.WAQI_TOKEN;

async function record() {
  try {
    // 1. BEACON LOGIC: Read the last known GPS coordinates from the user's profile
    const userDoc = await db.collection('users').doc(uid).get();
    const lastLocation = userDoc.exists ? userDoc.data().lastLocation : null;

    let apiUrl;
    if (lastLocation && lastLocation.lat && lastLocation.lon) {
      console.log(`Beacon found! Using coordinates: ${lastLocation.lat}, ${lastLocation.lon}`);
      apiUrl = `https://api.waqi.info/feed/geo:${lastLocation.lat};${lastLocation.lon}/?token=${token}`;
    } else {
      console.log("No beacon found. Falling back to IP-based location.");
      apiUrl = `https://api.waqi.info/feed/here/?token=${token}`;
    }

    // 2. Fetch AQI
    const response = await axios.get(apiUrl);
    if (response.data.status !== "ok") throw new Error(`API Error: ${response.data.data}`);

    const data = response.data.data;

    // --- BULLETPROOF IST TIMEZONE FIX ---
    // IST is UTC + 5:30. We calculate this manually for 100% reliability.
    const now = new Date();
    const istOffset = 5.5 * 60 * 60 * 1000;
    const nowIST = new Date(now.getTime() + istOffset);
    
    // Extract IST components using UTC methods on the offset date
    const year = nowIST.getUTCFullYear();
    const month = String(nowIST.getUTCMonth() + 1).padStart(2, '0');
    const day = String(nowIST.getUTCDate()).padStart(2, '0');
    const dateStr = `${year}-${month}-${day}`;
    const hour = nowIST.getUTCHours();

    console.log(`Current Server UTC: ${now.toUTCString()}`);
    console.log(`Calculated Local IST: ${dateStr} ${hour}:00`);

    const record = {
      aqi: data.aqi,
      cityName: data.city.name,
      timestamp: admin.firestore.Timestamp.now(),
      date: dateStr
    };

    const dayDocRef = db.collection('users').doc(uid).collection('history').doc(dateStr);

    // 3. Save the hourly snapshot (using the IST hour)
    await dayDocRef.collection('hourly').doc(hour.toString()).set(record);

    // 4. Update Daily Max (High-Water Mark)
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
    console.log("Workflow trigger:", process.env.GITHUB_EVENT_NAME);

    console.log(`Successfully recorded slot [${hour}] for ${data.city.name} with AQI: ${data.aqi}`);
  } catch (error) {
    console.error("Critical Error:", error.message);
    process.exit(1);
  }
}

record();
