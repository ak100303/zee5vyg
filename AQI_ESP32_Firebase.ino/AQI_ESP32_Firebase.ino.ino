#include <WiFi.h>
#include <Firebase_ESP_Client.h> // Install: "Firebase Arduino Client Library for ESP8266 and ESP32" by Mobizt
#include <DHT.h>                 // Install: "DHT sensor library" by Adafruit
#include <Wire.h>
#include <Adafruit_GFX.h>        // Install: "Adafruit GFX Library" by Adafruit
#include <Adafruit_SSD1306.h>    // Install: "Adafruit SSD1306" by Adafruit

// Provide the token generation process info and helpers
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"

// ==========================================
// 1. WIFI CREDENTIALS
// ==========================================
#define WIFI_SSID "ajay"
#define WIFI_PASSWORD "h7e6in@123"

// ==========================================
// 2. FIREBASE CREDENTIALS
// ==========================================
// Get this from Firebase Console: Project Settings -> General -> Web API Key
#define API_KEY "AIzaSyC3AxaygHx6ycahoXhtXeoM22bx5rxkVuA"

// Get this from Firebase Console: Project Settings -> General -> Project ID
#define PROJECT_ID "aqi-history-f9056" // e.g., my-aqi-app-12345

// Define Firebase Data objects
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

// ==========================================
// 3. SENSOR PINS Configuration
// ==========================================
#define DHTPIN 4           // Digital Pin 4 attached to DHT11 data pin
#define DHTTYPE DHT11      // We are using DHT11
#define MQ135_PIN 34       // Analog Pin 34 attached to MQ135 A0

DHT dht(DHTPIN, DHTTYPE);

// ==========================================
// 4. DISPLAY DEFAULT CONFIGURATION
// ==========================================
#define SCREEN_WIDTH 128 // OLED display width, in pixels
#define SCREEN_HEIGHT 64 // OLED display height, in pixels
#define OLED_RESET    -1 // Reset pin # (or -1 if sharing Arduino reset pin)
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

unsigned long dataMillis = 0;

void setup()
{
    Serial.begin(115200);
    dht.begin();
    
    // Initialize OLED display (Address 0x3C is standard for 128x64 OLEDs)
    if(!display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) { 
        Serial.println("SSD1306 allocation failed");
    } else {
        display.clearDisplay();
        display.setTextSize(1);
        display.setTextColor(WHITE);
        display.setCursor(0, 10);
        display.println("AQI Monitor Booting...");
        display.display();
    }

    // Connect to Wi-Fi
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    Serial.print("Connecting to Wi-Fi");
    while (WiFi.status() != WL_CONNECTED)
    {
        Serial.print(".");
        delay(300);
    }
    Serial.println();
    Serial.print("Connected with IP: ");
    Serial.println(WiFi.localIP());
    Serial.println();

    Serial.printf("Firebase Client v%s\n\n", FIREBASE_CLIENT_VERSION);

    // Assign the API key
    config.api_key = API_KEY;

    // We will use Anonymous Auth. 
    // IMPORTANT: Make sure to enable Anonymous Provider in Firebase Console (Authentication -> Sign-in method)
    // If you prefer to skip Auth entirely (if rules are completely public), you can leave this out, 
    // but anonymous auth is safer and standard for IoT devices writing to Firestore.
    Serial.println("Signing in anonymously...");
    if (Firebase.signUp(&config, &auth, "", "")) {
        Serial.println("Successfully signed in anonymously!");
    } else {
        Serial.printf("Failed to sign in anonymously: %s\n", config.signer.signupError.message.c_str());
    }

    config.token_status_callback = tokenStatusCallback; // Assign the callback for token generation

    // Initialize Firebase setup
    Firebase.begin(&config, &auth);
    Firebase.reconnectWiFi(true);
}

void loop()
{
    // Firebase.ready() manages auth token refreshes automatically.
    // We send an update every 10 seconds.
    if (Firebase.ready() && (millis() - dataMillis > 10000 || dataMillis == 0))
    {
        dataMillis = millis();

        // 1. Read DHT11 Temp & Humidity
        float h = dht.readHumidity();
        float t = dht.readTemperature();
        
        // 2. Read MQ135 (Needs calibration! Using analog map for placeholder mapping)
        int analogValue = analogRead(MQ135_PIN); 
        // A simple raw map. 400 is standard clean outdoor air.
        float ppm = map(analogValue, 0, 4095, 400, 2000); 
        
        // 3. Calculate AQI (Using Android app's formula)
        int aqi = 0;
        if (ppm <= 600) aqi = ppm / 12;
        else if (ppm <= 1000) aqi = 51 + (ppm - 600) / 8;
        else aqi = 101 + (ppm - 1000) / 10;
        if(aqi > 500) aqi = 500;

        if (isnan(h) || isnan(t)) {
            Serial.println("Failed to read from DHT sensor!");
            return;
        }

        Serial.printf("Temp: %.1f°C | Hum: %.1f%% | PPM: %.1f | AQI: %d\n", t, h, ppm, aqi);

        // --- UPDATE OLED DISPLAY ---
        display.clearDisplay();
        display.setTextSize(2);
        display.setCursor(0, 0);
        display.print("AQI: ");
        display.println(aqi);

        display.setTextSize(1);
        display.setCursor(0, 24);
        display.print("Temp: "); display.print(t, 1); display.println(" C");
        
        display.setCursor(0, 36);
        display.print("Hum : "); display.print(h, 1); display.println(" %");

        display.setCursor(0, 48);
        display.print("PPM : "); display.print(ppm, 0); 

        display.display();
        // ---------------------------

        // 4. Construct the Firestore Document structure
        FirebaseJson content;
        
        // Note: Firestore requires specific type declarations in its JSON.
        content.set("fields/ppm/doubleValue", ppm);
        content.set("fields/temp/doubleValue", t);
        content.set("fields/humi/doubleValue", h);
        content.set("fields/aqi/integerValue", aqi); // Using integerValue for Long/Int

        // Target path the Android App is listening to: users/esp32_device/iot_devices/station_01
        String documentPath = "users/esp32_device/iot_devices/station_01";
        
        Serial.print("Uploading to Firestore... ");
        
        // 5. Commit write to Firestore
        // patchDocument will automatically update the fields or create the entire document tree if it's missing!
        if (Firebase.Firestore.patchDocument(&fbdo, PROJECT_ID, "", documentPath.c_str(), content.raw(), "ppm,temp,humi,aqi")) {
            Serial.println("SUCCESS!");
        } else {
            Serial.println("FAILED");
            Serial.println("REASON: " + fbdo.errorReason());
        }
    }
}
