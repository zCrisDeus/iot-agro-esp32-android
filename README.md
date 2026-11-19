# Sistema IoT de Monitoreo y Control de Riego ESP32 (ISO 27400)

Proyecto académico para la asignatura **Aplicaciones IoT (TI3042 - Unidad 2)**. Implementa una arquitectura distribuida segura entre un nodo **ESP32** (con gemelo digital en Python y firmware C++ equivalente), **Firebase Realtime Database + Authentication** y una aplicación móvil nativa en **Android (Kotlin + Jetpack Compose)** con soporte multi-dispositivo y control de acceso basado en roles (**RBAC**).

---

## Estructura del Monorepo

```text
iot-agro-esp32-android/
├── android-app/               # App móvil nativa en Kotlin + Jetpack Compose + Material 3
├── esp32-digital-twin/        # Gemelo digital ESP32 en Python (telemetría TLS, tampering y watchdog)
├── esp32-firmware/            # Sketch C++ (.ino) listo para placa física ESP32 (ADC1 + TLS)
├── firebase/                  # Reglas de seguridad RBAC y validación de esquema (database.rules.json)
└── docs/                      # Diagramas UML (Mermaid), Historias Gherkin y Matriz ISO 27400
```

---

## Requisitos Previos

* **Android Studio** (Ladybug o superior) con SDK 34+ y emulador o dispositivo físico Android.
* **Python 3.10+** para ejecutar el gemelo digital del nodo ESP32.
* Cuenta de **Firebase** (Plan Spark sin costo) con **Authentication** (Email/Contraseña) y **Realtime Database** habilitados.

---

## Guía de Ejecución Rápida

### 1. Configuración de Firebase
1. Crear un proyecto en Firebase Console y habilitar **Authentication (Correo/Contraseña)** y **Realtime Database**.
2. Registrar los 3 usuarios de prueba y asignar sus UIDs bajo el nodo `/usuarios/{uid}/rol`:
   * `supervisor@agro.cl` → `"supervisor"` (lectura y control total).
   * `operador@agro.cl` → `"operador"` (solo lectura).
   * `esp32-nodo01@agro.cl` → `"nodo_iot"` (escritura exclusiva de telemetría).
3. Publicar el contenido de `firebase/database.rules.json` en la pestaña **Reglas** de Realtime Database.

### 2. Arranque del Gemelo Digital ESP32
```powershell
cd esp32-digital-twin
copy .env.example .env   # Completar FIREBASE_API_KEY, FIREBASE_DB_URL y credenciales del nodo
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python simulador_esp32.py
```
**Controles interactivos en consola durante la simulación:**
* `t` + `Enter`: Simula sabotaje o desconexión física de sonda (`ADC = 4092`, activa alarma y bloquea bomba).
* `w` + `Enter`: Simula caída de red Wi-Fi (activa *Watchdog Fail-Safe* tras 10 segundos).
* `q` + `Enter`: Detiene la simulación.

### 3. Ejecución de la App Android
1. Descargar `google-services.json` desde Firebase Console y ubicarlo en `android-app/app/google-services.json`.
2. Abrir `android-app/` en Android Studio, sincronizar Gradle y ejecutar en emulador o teléfono físico (`app-debug.apk`).

---

## Documentación Técnica
* [Arquitectura del Sistema y Diagramas UML](docs/arquitectura_y_uml.md)
* [Historias de Usuario (Gherkin) y Matriz ISO 27400](docs/gherkin_e_iso27400.md)