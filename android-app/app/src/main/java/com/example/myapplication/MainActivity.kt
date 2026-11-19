package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.screens.ActivadorScreen
import com.example.myapplication.ui.screens.LoginScreen
import com.example.myapplication.ui.screens.MonitoreoScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.viewmodel.AgroViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val agroViewModel: AgroViewModel = viewModel()
                val inicio = if (agroViewModel.usuarioAutenticado) "monitoreo" else "login"

                NavHost(navController = navController, startDestination = inicio) {
                    composable("login") {
                        LoginScreen(
                            viewModel = agroViewModel,
                            onLoginSuccess = {
                                navController.navigate("monitoreo") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("monitoreo") {
                        MonitoreoScreen(
                            viewModel = agroViewModel,
                            onIrControl = { navController.navigate("activador") },
                            onLogout = {
                                navController.navigate("login") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("activador") {
                        ActivadorScreen(
                            viewModel = agroViewModel,
                            onVolver = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}