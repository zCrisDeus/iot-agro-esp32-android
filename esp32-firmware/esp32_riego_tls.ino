#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <time.h>

// ================= CREDENCIALES Y ENDPOINTS (NO SUBIR REALES A GIT) =================
const char* WIFI_SSID     = "TU_RED_WIFI";
const char* WIFI_PASSWORD = "TU_CLAVE_WIFI";

const char* FIREBASE_API_KEY = "TU_WEB_API_KEY";
const char* FIREBASE_DB_URL  = "https://iot-agro-esp32-default-rtdb.firebaseio.com";
const char* NODO_EMAIL       = "esp32-nodo01@agro.cl";
const char* NODO_PASSWORD    = "TU_PASSWORD_NODO";
const char* DEVICE_ID        = "esp32_nodo_01";

// ================= PINOUT HARDWARE ESP32 (ADC1 COMPATIBLE CON WI-FI) =================
const int PIN_SENSOR_HUMEDAD = 34; // ADC1_CH6 (0 - 4095)
const int PIN_LED_VERDE      = 25;
const int PIN_LED_AMARILLO   = 33;
const int PIN_LED_ROJO       = 32;
const int PIN_RELE_BOMBA     = 26;

// ================= ESTADO OT Y SEGURIDAD ISO 27400 =================
String idToken = "";
String modoOperacion = "AUTO";
int umbralHumedad = 35;
unsigned long frecuenciaMs = 2000;
bool bombaActiva = false;
unsigned long ultimoContactoOk = 0;
const unsigned long WATCHDOG_TIMEOUT_MS = 10000;

WiFiClientSecure secureClient;

bool autenticarFirebase() {
  if (WiFi.status() != WL_CONNECTED) return false;

  HTTPClient http;
  String url = String("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=") + FIREBASE_API_KEY;
  
  // En entorno productivo ISO 27400 se fija el certificado raíz con secureClient.setCACert(ROOT_CA)
  secureClient.setInsecure();

  if (!http.begin(secureClient, url)) return false;
  http.addHeader("Content-Type", "application/json");

  JsonDocument docReq;
  docReq["email"] = NODO_EMAIL;
  docReq["password"] = NODO_PASSWORD;
  docReq["returnSecureToken"] = true;

  String body;
  serializeJson(docReq, body);

  int code = http.POST(body);
  if (code == 200) {
    JsonDocument docRes;
    if (!deserializeJson(docRes, http.getString())) {
      idToken = docRes["idToken"].as<String>();
      ultimoContactoOk = millis();
      Serial.println("[AUTH TLS] Nodo ESP32 autenticado correctamente.");
      http.end();
      return true;
    }
  }
  Serial.printf("[ERROR AUTH] HTTP %d\n", code);
  http.end();
  return false;
}

bool leerEstadoRemoto() {
  HTTPClient http;
  String baseUrl = String(FIREBASE_DB_URL) + "/dispositivos/" + DEVICE_ID;

  // 1. Leer configuración (umbral y frecuencia)
  if (http.begin(secureClient, baseUrl + "/configuracion.json?auth=" + idToken)) {
    int code = http.GET();
    if (code == 401) {
      http.end();
      return autenticarFirebase();
    }
    if (code == 200) {
      JsonDocument cfg;
      if (!deserializeJson(cfg, http.getString())) {
        int nuevoUmbral = cfg["umbral_humedad"] | umbralHumedad;
        if (nuevoUmbral >= 0 && nuevoUmbral <= 100) {
          umbralHumedad = nuevoUmbral;
        }
        unsigned long nuevaFrec = cfg["frecuencia_ms"] | frecuenciaMs;
        if (nuevaFrec == 1000 || nuevaFrec == 2000 || nuevaFrec == 5000) {
          frecuenciaMs = nuevaFrec;
        }
      }
    } else {
      http.end();
      return false;
    }
    http.end();
  }

  // 2. Leer y sanitizar comando operativo (R, E, AUTO)
  if (http.begin(secureClient, baseUrl + "/comandos.json?auth=" + idToken)) {
    int code = http.GET();
    if (code == 200) {
      JsonDocument cmd;
      if (!deserializeJson(cmd, http.getString())) {
        String accion = cmd["accion"] | "";
        if (accion == "R" || accion == "E" || accion == "AUTO") {
          modoOperacion = accion;
        } else if (accion.length() > 0) {
          Serial.println("[SEGURIDAD OT] Comando no autorizado descartado.");
        }
      }
      ultimoContactoOk = millis();
      http.end();
      return true;
    }
    http.end();
  }
  return false;
}

void actualizarSalidasFisicas(String semaforo) {
  digitalWrite(PIN_LED_VERDE,    semaforo == "VERDE"    ? HIGH : LOW);
  digitalWrite(PIN_LED_AMARILLO, semaforo == "AMARILLO" ? HIGH : LOW);
  digitalWrite(PIN_LED_ROJO,     semaforo == "ROJO"     ? HIGH : LOW);
  digitalWrite(PIN_RELE_BOMBA,   bombaActiva            ? HIGH : LOW);
}

bool publicarTelemetria(int adc, int humedad, String semaforo, bool alertaIntegridad) {
  HTTPClient http;
  String url = String(FIREBASE_DB_URL) + "/dispositivos/" + DEVICE_ID + "/telemetria.json?auth=" + idToken;

  if (!http.begin(secureClient, url)) return false;
  http.addHeader("Content-Type", "application/json");

  time_t now;
  time(&now);

  JsonDocument payload;
  payload["adc"] = adc;
  payload["humedad"] = humedad;
  payload["semaforo"] = semaforo;
  payload["bomba_activa"] = bombaActiva;
  payload["alerta_integridad"] = alertaIntegridad;
  payload["modo"] = modoOperacion;
  payload["timestamp"] = (long)now;

  String jsonStr;
  serializeJson(payload, jsonStr);

  int code = http.PUT(jsonStr);
  http.end();

  if (code == 200) {
    ultimoContactoOk = millis();
    return true;
  }
  return false;
}

void setup() {
  Serial.begin(115200);
  analogReadResolution(12); // Resolución 0 - 4095

  pinMode(PIN_LED_VERDE, OUTPUT);
  pinMode(PIN_LED_AMARILLO, OUTPUT);
  pinMode(PIN_LED_ROJO, OUTPUT);
  pinMode(PIN_RELE_BOMBA, OUTPUT);
  digitalWrite(PIN_RELE_BOMBA, LOW);

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Conectando a Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWi-Fi conectado.");

  configTime(0, 0, "pool.ntp.org");
  autenticarFirebase();
}

void loop() {
  int adc = analogRead(PIN_SENSOR_HUMEDAD);
  bool alertaIntegridad = (adc <= 15 || adc >= 4080);
  int humedad = constrain(map(adc, 4095, 1200, 0, 100), 0, 100);

  bool enlaceOk = (WiFi.status() == WL_CONNECTED) && leerEstadoRemoto();

  // Watchdog Fail-Safe OT: apaga bomba si pierde enlace > 10s o si hay sabotaje de sonda
  if (alertaIntegridad || (!enlaceOk && (millis() - ultimoContactoOk >= WATCHDOG_TIMEOUT_MS))) {
    bombaActiva = false;
  } else if (modoOperacion == "E") {
    bombaActiva = false;
  } else if (modoOperacion == "R") {
    bombaActiva = true;
  } else {
    if (humedad < umbralHumedad) {
      bombaActiva = true;
    } else if (humedad >= min(100, umbralHumedad + 8)) {
      bombaActiva = false;
    }
  }

  String semaforo = "VERDE";
  if (alertaIntegridad || humedad < umbralHumedad) {
    semaforo = "ROJO";
  } else if (humedad < umbralHumedad + 12) {
    semaforo = "AMARILLO";
  }

  actualizarSalidasFisicas(semaforo);

  if (enlaceOk) {
    publicarTelemetria(adc, humedad, semaforo, alertaIntegridad);
  } else {
    Serial.println("[FAIL-SAFE OT] Sin enlace TLS; riego bloqueado por seguridad.");
  }

  delay(frecuenciaMs);
}