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
      console.log(`Beacon found! Using GPS coordinates: ${lastLocation.lat}, ${lastLocation.lon}`);
      apiUrl = `https://api.waqi.info/feed/geo:${lastLocation.lat};${lastLocation.lon}/?token=${token}`;
    } else {
      console.log("No beacon found. Falling back to default IP-based hyperlocal feed.");
      apiUrl = `https://api.waqi.info/feed/here/?token=${token}`;
    }

    // 2. Fetch AQI
    const response = await axios.get(apiUrl);
    
    if (response.data.status !== "ok") {
      throw new Error(`API Error: ${response.data.data}`);
    }

    const data = response.data.data;
    const now = new Date();
    // Format: yyyy-MM-dd
    const dateStr = now.toISOString().split('T')[0];
    const hour = now.getHours();

    const record = {
      aqi: data.aqi,
      cityName: data.city.name,
      timestamp: admin.firestore.Timestamp.now(),
      date: dateStr
    };

    const dayDocRef = db.collection('users').doc(uid)
      .collection('history').doc(dateStr);

    // 3. Save the hourly snapshot
    await dayDocRef.collection('hourly').doc(hour.toString()).set(record);

    // 4. Update the daily maximum AQI (High-Water Mark logic)
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

    console.log(`Successfully recorded hyperlocal AQI: ${data.aqi} for ${data.city.name} at hour ${hour}`);
  } catch (error) {
    console.error("Critical Error during recording:", error.message);
    process.exit(1);
  }
}

record();
