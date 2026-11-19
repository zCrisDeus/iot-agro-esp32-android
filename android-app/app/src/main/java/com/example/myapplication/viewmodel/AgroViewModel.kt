package com.example.myapplication.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.myapplication.model.ConfiguracionNodo
import com.example.myapplication.model.SanitizadorIoT
import com.example.myapplication.model.TelemetriaNodo
import com.example.myapplication.security.SessionCryptoManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AgroViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance("https://iot-agro-esp32-default-rtdb.firebaseio.com")
    private val nodoRef = db.getReference("dispositivos/esp32_nodo_01")
    val cryptoManager = SessionCryptoManager(application.applicationContext)

    var usuarioAutenticado by mutableStateOf(auth.currentUser != null)
        private set
    var rolUsuario by mutableStateOf(cryptoManager.obtenerRol())
        private set
    var emailUsuario by mutableStateOf(cryptoManager.obtenerEmail())
        private set
    var cargandoLogin by mutableStateOf(false)
        private set
    var errorMensaje by mutableStateOf<String?>(null)
        private set
    var redDisponible by mutableStateOf(cryptoManager.hayConexionInternet())
        private set

    var telemetria by mutableStateOf(TelemetriaNodo())
        private set
    var configuracion by mutableStateOf(ConfiguracionNodo())
        private set
    var refrescoActivo by mutableStateOf(true)
        private set

    val consolaTramas = mutableStateListOf<String>()
    private var nodoListener: ValueEventListener? = null

    val esSupervisor: Boolean
        get() = rolUsuario == "supervisor"

    init {
        if (usuarioAutenticado) {
            iniciarEscuchaTiempoReal()
        }
    }

    fun verificarRed() {
        redDisponible = cryptoManager.hayConexionInternet()
    }

    fun iniciarSesion(email: String, password: String, onSuccess: () -> Unit) {
        verificarRed()
        if (!redDisponible) {
            errorMensaje = "Sin conexión a Internet. Verifica Wi-Fi o datos móviles."
            return
        }
        val correoLimpio = email.trim()
        if (correoLimpio.isEmpty() || password.isEmpty()) {
            errorMensaje = "Ingresa correo y contraseña."
            return
        }

        cargandoLogin = true
        errorMensaje = null

        auth.signInWithEmailAndPassword(correoLimpio, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                db.getReference("usuarios/$uid/rol").get()
                    .addOnSuccessListener { snapshot ->
                        val rol = snapshot.getValue(String::class.java) ?: "operador"
                        cryptoManager.guardarSesionCifrada(uid, correoLimpio, rol)
                        rolUsuario = rol
                        emailUsuario = correoLimpio
                        usuarioAutenticado = true
                        cargandoLogin = false
                        registrarLog("AUTH TLS OK -> $correoLimpio [Rol: ${rol.uppercase()}]")
                        iniciarEscuchaTiempoReal()
                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        cargandoLogin = false
                        errorMensaje = "No se pudo verificar rol RBAC: ${e.message}"
                    }
            }
            .addOnFailureListener {
                cargandoLogin = false
                errorMensaje = "Credenciales inválidas o usuario no autorizado."
            }
    }

    fun cerrarSesion(onLoggedOut: () -> Unit) {
        detenerEscuchaTiempoReal()
        auth.signOut()
        cryptoManager.limpiarSesion()
        usuarioAutenticado = false
        rolUsuario = "operador"
        emailUsuario = ""
        consolaTramas.clear()
        onLoggedOut()
    }

    fun alternarRefresco(activo: Boolean) {
        refrescoActivo = activo
        if (activo) iniciarEscuchaTiempoReal() else detenerEscuchaTiempoReal()
    }

    private fun iniciarEscuchaTiempoReal() {
        if (nodoListener != null) return
        nodoListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val telSnap = snapshot.child("telemetria")
                if (telSnap.exists()) {
                    val adcRaw = telSnap.child("adc").getValue(Int::class.java) ?: 0
                    val humRaw = telSnap.child("humedad").getValue(Int::class.java) ?: 0
                    val semRaw = telSnap.child("semaforo").getValue(String::class.java) ?: "VERDE"
                    val bomba = telSnap.child("bomba_activa").getValue(Boolean::class.java) ?: false
                    val integridad = telSnap.child("alerta_integridad").getValue(Boolean::class.java) ?: false
                    val modo = telSnap.child("modo").getValue(String::class.java) ?: "AUTO"
                    val ts = telSnap.child("timestamp").getValue(Long::class.java) ?: 0L

                    val adcSeguro = SanitizadorIoT.sanitizarAdc(adcRaw)
                    val humSegura = SanitizadorIoT.sanitizarUmbral(humRaw)
                    val semSeguro = if (semRaw in setOf("VERDE", "AMARILLO", "ROJO")) semRaw else "ROJO"

                    telemetria = TelemetriaNodo(
                        adc = adcSeguro,
                        humedad = humSegura,
                        semaforo = semSeguro,
                        bombaActiva = bomba,
                        alertaIntegridad = integridad,
                        modo = modo,
                        timestamp = ts
                    )
                    registrarLog("RX TLS <- ADC:$adcSeguro | Hum:$humSegura% | $semSeguro | Bomba:${if (bomba) "ON" else "OFF"}")
                }

                val cfgSnap = snapshot.child("configuracion")
                if (cfgSnap.exists()) {
                    val umbral = cfgSnap.child("umbral_humedad").getValue(Int::class.java) ?: 35
                    val frec = cfgSnap.child("frecuencia_ms").getValue(Int::class.java) ?: 2000
                    configuracion = ConfiguracionNodo(
                        umbralHumedad = SanitizadorIoT.sanitizarUmbral(umbral),
                        frecuenciaMs = if (SanitizadorIoT.esFrecuenciaValida(frec)) frec else 2000
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {
                registrarLog("ERR RTDB: ${error.message}")
            }
        }
        nodoRef.addValueEventListener(nodoListener!!)
    }

    private fun detenerEscuchaTiempoReal() {
        nodoListener?.let { nodoRef.removeEventListener(it) }
        nodoListener = null
    }

    fun enviarComando(accion: String) {
        if (!esSupervisor) {
            registrarLog("RBAC BLOQUEADO: Rol operador sin permiso de escritura.")
            return
        }
        if (!SanitizadorIoT.esComandoValido(accion)) return

        val payload = mapOf(
            "accion" to accion,
            "ultimo_emisor" to emailUsuario,
            "actualizado_en" to (System.currentTimeMillis() / 1000L)
        )
        nodoRef.child("comandos").setValue(payload)
            .addOnSuccessListener { registrarLog("TX CMD -> Accion '$accion' enviada por $emailUsuario") }
            .addOnFailureListener { e -> registrarLog("ERR TX CMD: ${e.message}") }
    }

    fun actualizarConfiguracion(nuevoUmbral: Int, nuevaFrecuenciaMs: Int) {
        if (!esSupervisor) {
            registrarLog("RBAC BLOQUEADO: Solo supervisor puede modificar parámetros.")
            return
        }
        val umbralSeguro = SanitizadorIoT.sanitizarUmbral(nuevoUmbral)
        val frecSegura = if (SanitizadorIoT.esFrecuenciaValida(nuevaFrecuenciaMs)) nuevaFrecuenciaMs else 2000

        val payload = mapOf(
            "umbral_humedad" to umbralSeguro,
            "frecuencia_ms" to frecSegura
        )
        nodoRef.child("configuracion").setValue(payload)
            .addOnSuccessListener { registrarLog("TX CFG -> Umbral:$umbralSeguro% | Muestreo:${frecSegura}ms") }
            .addOnFailureListener { e -> registrarLog("ERR TX CFG: ${e.message}") }
    }

    private fun registrarLog(linea: String) {
        val hora = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        consolaTramas.add(0, "[$hora] $linea")
        if (consolaTramas.size > 30) {
            consolaTramas.removeAt(consolaTramas.lastIndex)
        }
    }

    override fun onCleared() {
        detenerEscuchaTiempoReal()
        super.onCleared()
    }
}