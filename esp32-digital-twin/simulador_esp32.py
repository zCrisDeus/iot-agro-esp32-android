import os
import time
import random
import threading
import requests
from dotenv import load_dotenv

load_dotenv()

API_KEY = os.getenv("FIREBASE_API_KEY", "").strip()
DB_URL = os.getenv("FIREBASE_DB_URL", "").strip().rstrip("/")
NODO_EMAIL = os.getenv("NODO_EMAIL", "").strip()
NODO_PASSWORD = os.getenv("NODO_PASSWORD", "").strip()
DEVICE_ID = os.getenv("DEVICE_ID", "esp32_nodo_01").strip()

COMANDOS_VALIDOS = {"R", "E", "AUTO"}
FRECUENCIAS_VALIDAS = {1000, 2000, 5000}
WATCHDOG_TIMEOUT_SEG = 10.0


class ESP32DigitalTwin:
    def __init__(self):
        self.id_token = None
        self.refresh_token = None
        self.humedad = 48.0
        self.bomba_activa = False
        self.modo = "AUTO"
        self.umbral_humedad = 35
        self.frecuencia_ms = 2000
        self.simular_sabotaje = False
        self.simular_caida_red = False
        self.ultimo_contacto_ok = time.time()
        self.ejecutando = True

    def autenticar(self) -> bool:
        url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={API_KEY}"
        payload = {"email": NODO_EMAIL, "password": NODO_PASSWORD, "returnSecureToken": True}
        try:
            resp = requests.post(url, json=payload, timeout=5)
            resp.raise_for_status()
            data = resp.json()
            self.id_token = data["idToken"]
            self.refresh_token = data["refreshToken"]
            self.ultimo_contacto_ok = time.time()
            print(f"[AUTH TLS] Nodo '{DEVICE_ID}' autenticado como {NODO_EMAIL}")
            return True
        except requests.RequestException as e:
            print(f"[ERROR AUTH] Fallo de autenticación TLS: {e}")
            return False

    def renovar_token(self) -> bool:
        if not self.refresh_token:
            return self.autenticar()
        url = f"https://securetoken.googleapis.com/v1/token?key={API_KEY}"
        try:
            resp = requests.post(url, data={"grant_type": "refresh_token", "refresh_token": self.refresh_token}, timeout=5)
            resp.raise_for_status()
            data = resp.json()
            self.id_token = data["id_token"]
            self.refresh_token = data["refresh_token"]
            return True
        except requests.RequestException:
            return self.autenticar()

    def leer_estado_remoto(self) -> bool:
        if self.simular_caida_red:
            raise ConnectionError("Simulación de corte Wi-Fi activa")
        base = f"{DB_URL}/dispositivos/{DEVICE_ID}"
        resp_cfg = requests.get(f"{base}/configuracion.json?auth={self.id_token}", timeout=4)
        if resp_cfg.status_code == 401:
            self.renovar_token()
            resp_cfg = requests.get(f"{base}/configuracion.json?auth={self.id_token}", timeout=4)
        resp_cfg.raise_for_status()

        resp_cmd = requests.get(f"{base}/comandos.json?auth={self.id_token}", timeout=4)
        resp_cmd.raise_for_status()

        cfg = resp_cfg.json() or {}
        cmd = resp_cmd.json() or {}

        nuevo_umbral = cfg.get("umbral_humedad")
        if isinstance(nuevo_umbral, (int, float)) and 0 <= nuevo_umbral <= 100:
            self.umbral_humedad = int(nuevo_umbral)

        nueva_frec = cfg.get("frecuencia_ms")
        if nueva_frec in FRECUENCIAS_VALIDAS:
            self.frecuencia_ms = int(nueva_frec)

        accion = cmd.get("accion")
        if accion in COMANDOS_VALIDOS:
            self.modo = accion
        elif accion is not None:
            print(f"[SEGURIDAD OT] Comando no autorizado descartado: {accion}")

        self.ultimo_contacto_ok = time.time()
        return True

    def calcular_adc_y_integridad(self):
        if self.simular_sabotaje:
            return 4092, int(round(self.humedad)), True
        if self.bomba_activa:
            self.humedad = min(95.0, self.humedad + random.uniform(2.5, 4.0))
        else:
            self.humedad = max(10.0, self.humedad - random.uniform(0.6, 1.4))
        hum_int = int(round(self.humedad))
        adc = max(0, min(4095, int(4095 - (hum_int / 100.0) * 2895)))
        return adc, hum_int, (adc >= 4080 or adc <= 15)

    def evaluar_logica_ot(self, humedad: int, alerta_integridad: bool):
        if alerta_integridad:
            self.bomba_activa = False
            return
        if self.modo == "E":
            self.bomba_activa = False
        elif self.modo == "R":
            self.bomba_activa = True
        elif self.modo == "AUTO":
            if humedad < self.umbral_humedad:
                self.bomba_activa = True
            elif humedad >= min(100, self.umbral_humedad + 8):
                self.bomba_activa = False

    def calcular_semaforo(self, humedad: int, alerta_integridad: bool) -> str:
        if alerta_integridad or humedad < self.umbral_humedad:
            return "ROJO"
        if humedad < self.umbral_humedad + 12:
            return "AMARILLO"
        return "VERDE"

    def publicar_telemetria(self, adc: int, humedad: int, semaforo: str, alerta_integridad: bool):
        if self.simular_caida_red:
            raise ConnectionError("Simulación de corte Wi-Fi activa")
        url = f"{DB_URL}/dispositivos/{DEVICE_ID}/telemetria.json?auth={self.id_token}"
        payload = {
            "adc": adc,
            "humedad": humedad,
            "semaforo": semaforo,
            "bomba_activa": self.bomba_activa,
            "alerta_integridad": alerta_integridad,
            "modo": self.modo,
            "timestamp": int(time.time()),
        }
        resp = requests.put(url, json=payload, timeout=4)
        resp.raise_for_status()
        self.ultimo_contacto_ok = time.time()

    def bucle_principal(self):
        if not all([API_KEY, DB_URL, NODO_EMAIL, NODO_PASSWORD]):
            print("[ERROR] Faltan variables en el archivo .env")
            return
        if not self.autenticar():
            return

        print("\n=== CONTROLES EN VIVO PARA DEMOSTRACIÓN ===")
        print("  [t] + Enter -> Activar/Desactivar Sabotaje de Sonda (Tampering ADC)")
        print("  [w] + Enter -> Simular Corte/Recuperación Wi-Fi (Watchdog Fail-Safe 10s)")
        print("  [q] + Enter -> Salir\n")

        while self.ejecutando:
            try:
                self.leer_estado_remoto()
                adc, hum, alerta = self.calcular_adc_y_integridad()
                self.evaluar_logica_ot(hum, alerta)
                semaforo = self.calcular_semaforo(hum, alerta)
                self.publicar_telemetria(adc, hum, semaforo, alerta)
                estado_bomba = "ENCENDIDA" if self.bomba_activa else "APAGADA"
                print(
                    f"[TX TLS] ADC:{adc:4d} | Hum:{hum:3d}% (Umbral:{self.umbral_humedad}%) | "
                    f"Semáforo:{semaforo:8s} | Modo:{self.modo:4s} | Bomba:{estado_bomba} | Tampering:{alerta}"
                )
            except Exception as e:
                sin_conexion = time.time() - self.ultimo_contacto_ok
                print(f"[WARN RED] Sin enlace ({sin_conexion:.1f}s): {e}")
                if sin_conexion >= WATCHDOG_TIMEOUT_SEG and self.bomba_activa:
                    self.bomba_activa = False
                    print("[FAIL-SAFE OT] Timeout > 10s: Bomba APAGADA automáticamente.")
            time.sleep(self.frecuencia_ms / 1000.0)


def escuchar_teclado(nodo: ESP32DigitalTwin):
    while nodo.ejecutando:
        cmd = input().strip().lower()
        if cmd == "t":
            nodo.simular_sabotaje = not nodo.simular_sabotaje
            print(f"\n>>> [SIMULACIÓN] Sabotaje de sonda: {'ACTIVADO' if nodo.simular_sabotaje else 'DESACTIVADO'}\n")
        elif cmd == "w":
            nodo.simular_caida_red = not nodo.simular_caida_red
            print(f"\n>>> [SIMULACIÓN] Corte de Wi-Fi: {'ACTIVADO' if nodo.simular_caida_red else 'RESTAURADO'}\n")
        elif cmd == "q":
            nodo.ejecutando = False
            print("\n>>> Deteniendo gemelo digital ESP32...")


if __name__ == "__main__":
    twin = ESP32DigitalTwin()
    threading.Thread(target=escuchar_teclado, args=(twin,), daemon=True).start()
    twin.bucle_principal()
