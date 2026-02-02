const admin = require('firebase-admin');
const axios = require('axios');

// 1. INITIALIZE FIREBASE
const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();
const uid = process.env.USER_UID;
const waqiToken = process.env.WAQI_TOKEN;
const owKey = process.env.OPENWEATHER_API_KEY;

// EPA Scientific Conversion Formula
function calculateUsAqi(pm25) {
    if (pm25 <= 12.0) return Math.round((50 / 12.0) * pm25);
    if (pm25 <= 35.4) return Math.round(((100 - 51) / (35.4 - 12.1)) * (pm25 - 12.1) + 51);
    if (pm25 <= 55.4) return Math.round(((150 - 101) / (55.4 - 35.5)) * (pm25 - 35.5) + 101);
    if (pm25 <= 150.4) return Math.round(((200 - 151) / (150.4 - 55.5)) * (pm25 - 55.5) + 151);
    if (pm25 <= 250.4) return Math.round(((300 - 201) / (250.4 - 150.5)) * (pm25 - 150.5) + 201);
    return Math.round(((500 - 301) / (500.4 - 250.5)) * (pm25 - 250.5) + 301);
}

async function record() {
  try {
    // 2. BEACON LOGIC: Follow the user's phone location
    const userDoc = await db.collection('users').doc(uid).get();
    const lastLoc = userDoc.exists ? userDoc.data().lastLocation : { lat: 13.0827, lon: 80.2707 };
    const { lat, lon } = lastLoc;

    let aqiValue = null;
    let cityLabel = "Unknown Area";
    let source = "waqi";

    // 3. TIER 1: WAQI
    try {
      const res = await axios.get(`https://api.waqi.info/feed/geo:${lat};${lon}/?token=${waqiToken}`, { timeout: 10000 });
      if (res.data.status === "ok") {
        aqiValue = res.data.data.aqi;
        cityLabel = res.data.data.city.name;
      }
    } catch (e) { console.log("WAQI Fail, moving to backup..."); }

    // 4. TIER 2: OPENWEATHER FAILOVER
    if (!aqiValue && owKey) {
      try {
        const res = await axios.get(`https://api.openweathermap.org/data/2.5/air_pollution?lat=${lat}&lon=${lon}&appid=${owKey}`, { timeout: 10000 });
        if (res.data.list.length > 0) {
          const pm25 = res.data.list[0].components.pm2_5;
          aqiValue = calculateUsAqi(pm25);
          cityLabel = "Cloud Backup (OWM)";
          source = "openweather";
        }
      } catch (e) { console.log("OWM Fail"); }
    }

    if (aqiValue) {
      const now = new Date();
      // Adjust to IST (+5.5) for the filename
      const istTime = new Date(now.getTime() + (5.5 * 60 * 60 * 1000));
      const dateStr = istTime.toISOString().split('T')[0];
      const hour = istTime.getUTCHours();

      const record = {
        aqi: aqiValue,
        cityName: cityLabel,
        dataSource: source,
        timestamp: admin.firestore.Timestamp.now()
      };

      // 5. SAVE TO FIREBASE
      await db.collection('users').doc(uid)
        .collection('history').document(dateStr)
        .collection('hourly').document(hour.toString())
        .set(record);

      console.log(`Successfully recorded ${aqiValue} from ${source} at ${hour}:00`);
    }

  } catch (error) {
    console.error("Critical Error:", error.message);
    process.exit(1);
  }
}

record();
