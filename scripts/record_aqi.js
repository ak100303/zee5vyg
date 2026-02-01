import axios from "axios";

const LAT = 13.0827;
const LON = 80.2707;

async function fetchWAQI() {
  const url = `https://api.waqi.info/feed/geo:${LAT};${LON}/?token=${process.env.WAQI_TOKEN}`;
  const res = await axios.get(url, { timeout: 8000 });

  if (res.data.status !== "ok" || res.data.data.aqi === "-" ) {
    throw new Error("WAQI unavailable");
  }

  return {
    aqi: res.data.data.aqi,
    source: "WAQI",
    ts: new Date().toISOString()
  };
}

async function fetchOpenWeather() {
  const url =
    `https://api.openweathermap.org/data/2.5/air_pollution` +
    `?lat=${LAT}&lon=${LON}&appid=${process.env.OPENWEATHER_API_KEY}`;

  const res = await axios.get(url, { timeout: 8000 });

  return {
    aqi: res.data.list[0].main.aqi, // OpenWeather AQI scale (1–5)
    source: "OpenWeather",
    ts: new Date().toISOString()
  };
}

async function getAQI() {
  try {
    console.log("Trying WAQI...");
    return await fetchWAQI();
  } catch (e) {
    console.warn("WAQI failed → switching to OpenWeather");
    return await fetchOpenWeather();
  }
}

(async () => {
  const data = await getAQI();
  console.log("AQI recorded:", data);

  // save to Firebase here
})();
