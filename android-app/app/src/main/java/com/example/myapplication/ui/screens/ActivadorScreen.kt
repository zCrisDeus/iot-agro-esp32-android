package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.viewmodel.AgroViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivadorScreen(
    viewModel: AgroViewModel,
    onVolver: () -> Unit
) {
    val cfg = viewModel.configuracion
    var sliderUmbral by remember(cfg.umbralHumedad) { mutableFloatStateOf(cfg.umbralHumedad.toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Control Activador OT") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!viewModel.esSupervisor) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Modo Operador (Solo Lectura): Los controles de riego y calibración están bloqueados por política RBAC.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Botones de control directo (R, E, AUTO)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.enviarComando("R") },
                    enabled = viewModel.esSupervisor,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Iniciar (R)")
                }

                Button(
                    onClick = { viewModel.enviarComando("E") },
                    enabled = viewModel.esSupervisor,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Paro (E)")
                }

                OutlinedButton(
                    onClick = { viewModel.enviarComando("AUTO") },
                    enabled = viewModel.esSupervisor,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("AUTO")
                }
            }

            // Slider de calibración de umbral
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Umbral Mínimo de Humedad: ${sliderUmbral.roundToInt()}%", fontWeight = FontWeight.Bold)
                    Slider(
                        value = sliderUmbral,
                        onValueChange = { sliderUmbral = it },
                        valueRange = 0f..100f,
                        enabled = viewModel.esSupervisor
                    )
                    Button(
                        onClick = { viewModel.actualizarConfiguracion(sliderUmbral.roundToInt(), cfg.frecuenciaMs) },
                        enabled = viewModel.esSupervisor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ENVIAR UMBRAL AL ESP32")
                    }
                }
            }

            // Consola de tramas TLS en tiempo real
            Text("Consola de Auditoría y Tramas TLS:", style = MaterialTheme.typography.labelLarge)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF121212))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(viewModel.consolaTramas) { linea ->
                    Text(
                        text = linea,
                        color = Color(0xFF80E27E),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}