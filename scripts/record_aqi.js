const admin = require('firebase-admin');
const axios = require('axios');

// 1. INITIALIZE FIREBASE
if (!admin.apps.length) {
  const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
}

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

    const now = new Date();
    const istOffset = 5.5 * 60 * 60 * 1000;
    const nowIST = new Date(now.getTime() + istOffset);
    const dateStr = nowIST.toISOString().split('T')[0];
    const hour = nowIST.getUTCHours();

    // 3. GET LAST RECORD (For Stagnancy Check)
    const lastHour = hour > 0 ? (hour - 1).toString() : "23";
    const lastDate = hour > 0 ? dateStr : new Date(nowIST.getTime() - 86400000).toISOString().split('T')[0];
    const lastDoc = await db.collection('users').doc(uid).collection('history').doc(lastDate).collection('hourly').doc(lastHour).get();
    const lastAqi = lastDoc.exists ? lastDoc.data().aqi : null;

    let aqiValue = null;
    let cityLabel = "Unknown Area";
    let source = "waqi";

    // 4. TIER 1: WAQI
    try {
      const res = await axios.get(`https://api.waqi.info/feed/geo:${lat};${lon}/?token=${waqiToken}`, { timeout: 10000 });
      if (res.data.status === "ok") {
        const waqiAqi = res.data.data.aqi;
        // STAGNANCY CHECK: If same as last hour, force switch to backup
        if (waqiAqi !== lastAqi) {
          aqiValue = waqiAqi;
          cityLabel = res.data.data.city.name;
          source = "waqi";
        } else {
          console.log("WAQI Stagnant (Same as last hour). Moving to backup...");
        }
      }
    } catch (e) { console.log("WAQI Fail, moving to backup..."); }

    // 5. TIER 2: OPENWEATHER FAILOVER
    if (!aqiValue && owKey) {
      try {
        const res = await axios.get(`https://api.openweathermap.org/data/2.5/air_pollution?lat=${lat}&lon=${lon}&appid=${owKey}`, { timeout: 10000 });
        if (res.data.list.length > 0) {
          const pm25 = res.data.list[0].components.pm2_5;
          aqiValue = calculateUsAqi(pm25);
          cityLabel = userDoc.exists && userDoc.data().cityName ? userDoc.data().cityName : "Local Area";
          source = "openweather";
        }
      } catch (e) { console.log("OWM Fail"); }
    }

    if (aqiValue) {
      const record = {
        aqi: aqiValue,
        cityName: cityLabel,
        dataSource: source,
        timestamp: admin.firestore.Timestamp.now()
      };

      await db.collection('users').doc(uid).collection('history').doc(dateStr).collection('hourly').doc(hour.toString()).set(record);
      console.log(`Successfully recorded ${aqiValue} from ${source} at ${hour}:00 IST`);
    }

  } catch (error) {
    console.error("Critical Error:", error.message);
    process.exit(1);
  }
}

record();
