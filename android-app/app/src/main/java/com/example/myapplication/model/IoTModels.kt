package com.example.myapplication.model

data class TelemetriaNodo(
    val adc: Int = 0,
    val humedad: Int = 0,
    val semaforo: String = "VERDE",
    val bombaActiva: Boolean = false,
    val alertaIntegridad: Boolean = false,
    val modo: String = "AUTO",
    val timestamp: Long = 0L
)

data class ConfiguracionNodo(
    val umbralHumedad: Int = 35,
    val frecuenciaMs: Int = 2000
)

object SanitizadorIoT {
    private val COMANDOS_PERMITIDOS = setOf("R", "E", "AUTO")
    private val FRECUENCIAS_PERMITIDAS = setOf(1000, 2000, 5000)

    fun esComandoValido(cmd: String): Boolean = cmd in COMANDOS_PERMITIDOS
    fun esFrecuenciaValida(ms: Int): Boolean = ms in FRECUENCIAS_PERMITIDAS
    fun sanitizarUmbral(valor: Int): Int = valor.coerceIn(0, 100)
    fun sanitizarAdc(adc: Int): Int = adc.coerceIn(0, 4095)
}