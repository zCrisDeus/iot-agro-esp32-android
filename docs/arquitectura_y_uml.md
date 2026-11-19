# Arquitectura del Sistema y Modelado UML

## 1. Justificación de Migración Tecnológica (Arduino + HC-05 a ESP32 + TLS)
La arquitectura inicial basada en Arduino UNO y Bluetooth HC-05 presentaba tres limitaciones críticas para un entorno de agricultura de precisión distribuido:
1. **Alcance físico restringido:** Bluetooth clásico limita la operación a ~10 metros sin obstáculos, impidiendo la supervisión remota multi-dispositivo.
2. **Ausencia de control de acceso granular (RBAC):** El enlace serial SPP no diferencia perfiles de operador y supervisor.
3. **Vulnerabilidad en tránsito:** Las tramas Bluetooth sin cifrado de capa de aplicación quedan expuestas a interceptación e inyección de comandos.

La migración hacia **ESP32 + Firebase Realtime Database + Android (Kotlin/Compose)** resuelve estas brechas mediante transporte cifrado **HTTPS/TLS 1.3**, autenticación basada en tokens JWT y validación de esquema en servidor.

---

## 2. Diagrama de Arquitectura y Componentes

```mermaid
flowchart LR
    subgraph Campo["Capa Física / Edge (OT)"]
        ESP["Nodo ESP32 / Gemelo Digital Python\n(ADC 12-bit + Relé + Watchdog 10s)"]
    end

    subgraph Nube["Backend Cloud (Firebase - Plan Spark)"]
        AUTH["Firebase Authentication\n(Email/Password + JWT)"]
        RTDB["Firebase Realtime Database\n(Reglas RBAC + Validación de Esquema)"]
    end

    subgraph Movil["Clientes Android (Kotlin + Jetpack Compose)"]
        SUP["Dispositivo 1: Supervisor\n(Control R/E/AUTO + Calibración)"]
        OPE["Dispositivo 2: Operador\n(Monitoreo Solo Lectura)"]
    end

    ESP <-->|"HTTPS / TLS 1.3 (REST + Token)"| AUTH
    ESP <-->|"HTTPS / TLS 1.3 (Telemetría / Comandos)"| RTDB
    SUP <-->|"TLS + Keystore AES-256"| AUTH
    SUP <-->|"WebSockets TLS en Tiempo Real"| RTDB
    OPE <-->|"TLS + Keystore AES-256"| AUTH
    OPE <-->|"WebSockets TLS (Solo Lectura)"| RTDB
```

---

## 3. Diagrama de Casos de Uso por Rol (RBAC)

```mermaid
flowchart TB
    Supervisor(["Supervisor de Riego"])
    Operador(["Operador de Campo"])
    NodoIoT(["Nodo ESP32 / Gemelo Digital"])

    subgraph Sistema["Sistema IoT Agro ESP32"]
        UC1["CU-01: Autenticarse con credenciales TLS"]
        UC2["CU-02: Visualizar telemetría, semáforo y alertas"]
        UC3["CU-03: Activar/Detener riego remoto (R / E / AUTO)"]
        UC4["CU-04: Calibrar umbral de humedad y frecuencia"]
        UC5["CU-05: Publicar telemetría ADC 12-bit"]
        UC6["CU-06: Ejecutar Fail-Safe por corte de red o sabotaje"]
    end

    Supervisor --> UC1
    Supervisor --> UC2
    Supervisor --> UC3
    Supervisor --> UC4

    Operador --> UC1
    Operador --> UC2

    NodoIoT --> UC1
    NodoIoT --> UC5
    NodoIoT --> UC6
```

---

## 4. Diagrama de Secuencia: Control Remoto y Respuesta OT

```mermaid
sequenceDiagram
    autonumber
    actor Sup as Supervisor (Android)
    participant KS as Android Keystore (AES-256)
    participant FB as Firebase RTDB (Reglas RBAC)
    participant ESP as Nodo ESP32 / Gemelo Digital

    Sup->>KS: Recupera sesión y rol cifrado
    Sup->>FB: PUT /comandos {accion: "R", ultimo_emisor}
    FB->>FB: Valida auth.uid y rol === "supervisor"
    FB-->>Sup: 200 OK (Escritura autorizada)
    ESP->>FB: GET /comandos.json?auth=JWT
    FB-->>ESP: {accion: "R"}
    ESP->>ESP: Sanitiza comando y activa Relé Bomba
    ESP->>FB: PUT /telemetria {adc, humedad, bomba_activa: true}
    FB-->>Sup: Push en tiempo real (UI actualizada < 1s)
```