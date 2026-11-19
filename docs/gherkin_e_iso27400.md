# Historias de Usuario (Gherkin) y Matriz de Cumplimiento ISO 27400

## 1. Historias de Usuario y Criterios de Aceptación

### HU-01: Autenticación Segura y Persistencia Cifrada
* **Como** usuario del sistema agrícola (Supervisor u Operador),
* **Quiero** iniciar sesión con credenciales validadas en la nube y resguardar mi sesión localmente mediante cifrado de hardware,
* **Para** evitar accesos no autorizados o extracción de datos de sesión desde el almacenamiento del dispositivo.

```gherkin
Feature: Autenticación y Gestión de Sesión Segura
  Scenario: Inicio de sesión exitoso con rol Supervisor
    Given que el dispositivo tiene conectividad a Internet
    And el usuario se encuentra en la pantalla "LoginScreen"
    When ingresa el correo "supervisor@agro.cl" y su contraseña válida
    And pulsa el botón "INGRESAR"
    Then Firebase Auth valida las credenciales sobre TLS
    And el sistema consulta su rol en "/usuarios/{uid}/rol"
    And almacena el perfil cifrado con AES-256 GCM mediante Android Keystore
    And navega a "MonitoreoScreen" habilitando los controles de actuación.

  Scenario: Rechazo de credenciales inválidas
    Given que el usuario está en "LoginScreen"
    When ingresa una contraseña incorrecta y pulsa "INGRESAR"
    Then el sistema bloquea el acceso y muestra un mensaje de error genérico sin filtrar detalles internos.
```

---

### HU-02: Control de Acceso Basado en Roles (RBAC) Multi-Dispositivo
* **Como** administrador del sistema IoT,
* **Quiero** restringir los comandos de riego y calibración exclusivamente al perfil Supervisor tanto en la interfaz móvil como en las reglas de base de datos,
* **Para** impedir que un Operador altere parámetros críticos de riego.

```gherkin
Feature: Segregación de Funciones por Rol (RBAC)
  Scenario: Operador intenta operar en modo solo lectura
    Given que un usuario ha iniciado sesión como "operador@agro.cl" en el Dispositivo A
    When visualiza "MonitoreoScreen" y navega hacia "ActivadorScreen"
    Then los botones "Iniciar (R)", "Paro (E)", "AUTO" y el slider de umbral aparecen deshabilitados
    And cualquier petición de escritura hacia "/comandos" es rechazada por Firebase Rules.

  Scenario: Sincronización en tiempo real entre Supervisor y Operador
    Given que el Supervisor está activo en el Dispositivo B y el Operador en el Dispositivo A
    When el Supervisor cambia el umbral de humedad al 45% y envía la configuración
    Then Firebase RTDB propaga el cambio en menos de 1 segundo al nodo ESP32 y al Dispositivo A.
```

---

### HU-03: Protección OT ante Sabotaje de Sonda y Corte de Enlace (Fail-Safe)
* **Como** responsable de infraestructura hídrica,
* **Quiero** que el nodo ESP32 detenga automáticamente la bomba si detecta valores ADC fuera de rango físico o pérdida de comunicación superior a 10 segundos,
* **Para** evitar inundaciones o daños en la bomba por fallas de red o manipulación física del sensor.

```gherkin
Feature: Resiliencia y Fail-Safe Operativo (OT)
  Scenario: Detección de sabotaje o desconexión de sonda (Tampering ADC)
    Given que el nodo ESP32 está regando en modo "AUTO" o "R"
    When la lectura del conversor ADC de 12 bits alcanza >= 4080 o <= 15
    Then el nodo activa "alerta_integridad = true"
    And apaga inmediatamente el relé de la bomba de riego
    And la aplicación Android despliega la tarjeta roja "ALERTA DE INTEGRIDAD DE SONDA".

  Scenario: Corte de conectividad Wi-Fi prolongado (Watchdog 10s)
    Given que la bomba de riego se encuentra encendida
    When se interrumpe la conexión HTTPS con Firebase durante más de 10 segundos
    Then el temporizador Watchdog fuerza el apagado automático de la bomba hasta restablecer el enlace.
```

---

## 2. Matriz de Cumplimiento de Seguridad (Norma ISO/IEC 27400)

| Dominio / Control ISO 27400 | Riesgo Mitigado | Implementación Técnica en el Proyecto | Evidencia en Código |
| :--- | :--- | :--- | :--- |
| **Confidencialidad en Tránsito** | Interceptación de credenciales y telemetría (*sniffing*). | Comunicación exclusiva sobre HTTPS/TLS 1.3; bloqueo explícito de tráfico HTTP plano (`usesCleartextTraffic="false"`). | `android-app/app/src/main/AndroidManifest.xml`, `simulador_esp32.py` |
| **Almacenamiento Seguro en Cliente** | Extracción de tokens o roles desde dispositivos robados o rooteados. | Cifrado de preferencias de sesión con `MasterKey` (`AES256_GCM` y `AES256_SIV`) respaldado por Android Keystore y `allowBackup="false"`. | `security/SessionCryptoManager.kt` |
| **Control de Acceso (RBAC)** | Ejecución no autorizada de actuadores físicos por usuarios de consulta. | Doble barrera: deshabilitación en capa UI (`esSupervisor`) y validación estricta en servidor mediante `database.rules.json`. | `firebase/database.rules.json`, `viewmodel/AgroViewModel.kt` |
| **Integridad y Sanitización de Datos** | Inyección de comandos malformados o desbordamiento de rangos ADC. | Lista blanca de comandos (`R`, `E`, `AUTO`), frecuencias permitidas (`1000`, `2000`, `5000` ms) y acotamiento numérico (`0..4095`, `0..100`) en cliente, nodo y reglas `.validate`. | `model/IoTModels.kt`, `firebase/database.rules.json` |
| **Disponibilidad y Fail-Safe OT** | Desborde de riego por caída de red o desconexión física del sensor. | *Watchdog* de 10 segundos que desenergiza el relé ante pérdida de enlace y bloqueo inmediato por saturación ADC (`alerta_integridad`). | `esp32-digital-twin/simulador_esp32.py`, `esp32-firmware/esp32_riego_tls.ino` |
| **Gestión de Secretos** | Exposición pública de API Keys o credenciales en control de versiones. | Exclusión mediante `.gitignore` de `.env`, `google-services.json` y entornos virtuales, entregando plantillas `.env.example`. | `.gitignore`, `esp32-digital-twin/.env.example` |