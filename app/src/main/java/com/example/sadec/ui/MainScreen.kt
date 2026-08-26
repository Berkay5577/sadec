package com.example.sadec.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sadec.data.model.MenuItem
import com.example.sadec.data.model.Order
import com.example.sadec.ui.screens.*
import com.example.sadec.ui.theme.OrangePrimary
import com.example.sadec.ui.viewmodel.MainViewModel

sealed class Screen {
    object Orders : Screen()
    data class OrderDetail(val order: Order) : Screen()
    object Menu : Screen()
    data class AddEditProduct(val item: MenuItem?) : Screen()
    object Tables : Screen()
    object Settings : Screen()
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSignOut: () -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Orders) }
    val orders by viewModel.orders.collectAsState()
    val pendingCount = orders.count { it.status == "pending" }
    val context = LocalContext.current

    // Listen UI message toasts
    LaunchedEffect(Unit) {
        viewModel.uiMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val isRootScreen = currentScreen is Screen.Orders || currentScreen is Screen.Menu || currentScreen is Screen.Tables || currentScreen is Screen.Settings

    Scaffold(
        bottomBar = {
            if (isRootScreen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentScreen is Screen.Orders,
                        onClick = { currentScreen = Screen.Orders },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (pendingCount > 0) {
                                        Badge(containerColor = OrangePrimary) {
                                            Text("$pendingCount", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = "Siparişler")
                            }
                        },
                        label = { Text("Siparişler") }
                    )

                    NavigationBarItem(
                        selected = currentScreen is Screen.Menu,
                        onClick = { currentScreen = Screen.Menu },
                        icon = { Icon(Icons.Default.RestaurantMenu, contentDescription = "Menü") },
                        label = { Text("Menü") }
                    )

                    NavigationBarItem(
                        selected = currentScreen is Screen.Tables,
                        onClick = { currentScreen = Screen.Tables },
                        icon = { Icon(Icons.Default.QrCode2, contentDescription = "Masalar") },
                        label = { Text("Masalar") }
                    )

                    NavigationBarItem(
                        selected = currentScreen is Screen.Settings,
                        onClick = { currentScreen = Screen.Settings },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Ayarlar") },
                        label = { Text("Ayarlar") }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val screen = currentScreen) {
                is Screen.Orders -> {
                    OrdersScreen(
                        viewModel = viewModel,
                        onOrderClick = { currentScreen = Screen.OrderDetail(it) }
                    )
                }
                is Screen.OrderDetail -> {
                    OrderDetailScreen(
                        order = screen.order,
                        viewModel = viewModel,
                        onBack = { currentScreen = Screen.Orders }
                    )
                }
                is Screen.Menu -> {
                    MenuManagementScreen(
                        viewModel = viewModel,
                        onAddProduct = { currentScreen = Screen.AddEditProduct(null) },
                        onEditProduct = { currentScreen = Screen.AddEditProduct(it) }
                    )
                }
                is Screen.AddEditProduct -> {
                    AddEditProductScreen(
                        editingItem = screen.item,
                        viewModel = viewModel,
                        onBack = { currentScreen = Screen.Menu }
                    )
                }
                is Screen.Tables -> {
                    TableManagementScreen(viewModel = viewModel)
                }
                is Screen.Settings -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        onSignOut = onSignOut
                    )
                }
            }
        }
    }
}
