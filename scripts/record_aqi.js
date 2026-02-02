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
    // 2. BEACON LOGIC
    const userDoc = await db.collection('users').doc(uid).get();
    const lastLoc = userDoc.exists ? userDoc.data().lastLocation : { lat: 13.0827, lon: 80.2707 };
    const { lat, lon } = lastLoc;

    const now = new Date();
    const istOffset = 5.5 * 60 * 60 * 1000;
    const nowIST = new Date(now.getTime() + istOffset);
    const dateStr = nowIST.toISOString().split('T')[0];
    const hour = nowIST.getUTCHours();

    // 3. FETCH RECENT HISTORY (For Prediction/Stagnancy)
    // We fetch the current day's records to check the trend
    const historySnap = await db.collection('users').doc(uid).collection('history').doc(dateStr).collection('hourly').get();
    const records = historySnap.docs.map(d => ({ hour: parseInt(d.id), aqi: d.data().aqi })).sort((a, b) => b.hour - a.hour);
    const lastAqi = records.length > 0 ? records[0].aqi : null;

    let aqiValue = null;
    let cityLabel = "Unknown Area";
    let source = "waqi";

    // 4. TIER 1: WAQI
    try {
      const res = await axios.get(`https://api.waqi.info/feed/geo:${lat};${lon}/?token=${waqiToken}`, { timeout: 10000 });
      if (res.data.status === "ok") {
        const waqiAqi = res.data.data.aqi;
        if (waqiAqi !== lastAqi) {
          aqiValue = waqiAqi;
          cityLabel = res.data.data.city.name;
          source = "waqi";
        } else {
          console.log("WAQI Stagnant. Moving to OWM Failover.");
        }
      }
    } catch (e) { console.log("WAQI Fail."); }

    // 5. TIER 2: OPENWEATHER
    if (!aqiValue && owKey) {
      try {
        const res = await axios.get(`https://api.openweathermap.org/data/2.5/air_pollution?lat=${lat}&lon=${lon}&appid=${owKey}`, { timeout: 10000 });
        if (res.data.list.length > 0) {
          const pm25 = res.data.list[0].components.pm2_5;
          aqiValue = calculateUsAqi(pm25);
          cityLabel = userDoc.exists && userDoc.data().cityName ? userDoc.data().cityName : "Local Area";
          source = "openweather";
        }
      } catch (e) { console.log("OWM Fail."); }
    }

    // 6. TIER 3: AUTONOMOUS PREDICTION (If both failed)
    if (!aqiValue && records.length >= 2) {
      console.log("Both APIs failed. Initiating Trend Prediction...");
      const latest = records[0].aqi;
      const previous = records[1].aqi;
      const trend = latest - previous;
      aqiValue = Math.max(0, Math.min(500, latest + Math.max(-25, Math.min(25, trend))));
      cityLabel = records[0].cityName || "Trend Estimate";
      source = "trend_prediction";
    }

    if (aqiValue) {
      const record = {
        aqi: aqiValue,
        cityName: cityLabel,
        dataSource: source,
        timestamp: admin.firestore.Timestamp.now()
      };

      await db.collection('users').doc(uid).collection('history').doc(dateStr).collection('hourly').doc(hour.toString()).set(record);
      console.log(`Recorded ${aqiValue} (${source}) for ${hour}:00 IST`);
    }

  } catch (error) {
    console.error("Critical Error:", error.message);
    process.exit(1);
  }
}

record();
