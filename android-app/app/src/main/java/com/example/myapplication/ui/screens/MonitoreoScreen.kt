package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.viewmodel.AgroViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoreoScreen(
    viewModel: AgroViewModel,
    onIrControl: () -> Unit,
    onLogout: () -> Unit
) {
    val tel = viewModel.telemetria
    val cfg = viewModel.configuracion

    val colorSemaforo = when (tel.semaforo) {
        "VERDE" -> Color(0xFF2E7D32)
        "AMARILLO" -> Color(0xFFF9A825)
        else -> Color(0xFFC62828)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telemetría ESP32") },
                actions = {
                    IconButton(onClick = { viewModel.verificarRed() }) {
                        Icon(
                            imageVector = if (viewModel.redDisponible) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = "Estado de red",
                            tint = if (viewModel.redDisponible) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = { viewModel.cerrarSesion(onLogout) }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Badge de sesión RBAC
            AssistChip(
                onClick = {},
                label = { Text("Usuario: ${viewModel.emailUsuario} · Rol: ${viewModel.rolUsuario.uppercase()}") }
            )

            // Alerta crítica OT de sabotaje físico (Tampering ADC)
            if (tel.alertaIntegridad) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("ALERTA DE INTEGRIDAD DE SONDA", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Lectura ADC saturada (${tel.adc}/4095). Posible desconexión o sabotaje; riego bloqueado por seguridad.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Tarjeta principal de Semáforo y Telemetría
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(colorSemaforo)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("SEMÁFORO: ${tel.semaforo}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("ADC 12-bit: ${tel.adc} / 4095  |  Humedad: ${tel.humedad}%", style = MaterialTheme.typography.titleMedium)
                    Text("Umbral configurado: ${cfg.umbralHumedad}%  ·  Modo: ${tel.modo}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (tel.bombaActiva) "BOMBA DE RIEGO: ENCENDIDA" else "BOMBA DE RIEGO: APAGADA",
                        fontWeight = FontWeight.Bold,
                        color = if (tel.bombaActiva) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Control de refresco y frecuencia de muestreo
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Refresco Automático RTDB", fontWeight = FontWeight.Medium)
                        Switch(checked = viewModel.refrescoActivo, onCheckedChange = { viewModel.alternarRefresco(it) })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Frecuencia de muestreo ESP32:", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1000 to "1s", 2000 to "2s", 5000 to "5s").forEach { (ms, etiqueta) ->
                            FilterChip(
                                selected = cfg.frecuenciaMs == ms,
                                onClick = {
                                    if (viewModel.esSupervisor) {
                                        viewModel.actualizarConfiguracion(cfg.umbralHumedad, ms)
                                    }
                                },
                                enabled = viewModel.esSupervisor,
                                label = { Text(etiqueta) }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onIrControl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("IR A CONTROL ACTIVADOR", fontWeight = FontWeight.Bold)
            }
        }
    }
}