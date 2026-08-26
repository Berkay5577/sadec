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
import androidx.compose.ui.unit.sp
import com.example.sadec.data.model.MenuItem
import com.example.sadec.data.model.Order
import com.example.sadec.ui.screens.*
import com.example.sadec.ui.theme.ForestGreen
import com.example.sadec.ui.theme.SageGreen
import com.example.sadec.ui.theme.Slate500
import com.example.sadec.ui.theme.WarmGold
import com.example.sadec.ui.viewmodel.MainViewModel

sealed class Screen {
    object Orders : Screen()
    object ActiveTables : Screen()
    object Menu : Screen()
    object QrMenu : Screen()
    object Settings : Screen()
    data class OrderDetail(val order: Order) : Screen()
    data class CategoryDetail(val category: com.example.sadec.data.model.Category) : Screen()
    data class AddEditProduct(val item: MenuItem?, val defaultCategoryId: String = "") : Screen()
    object Dashboard : Screen()
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSignOut: () -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Orders) }
    val orders by viewModel.orders.collectAsState()
    val tables by viewModel.tables.collectAsState()
    val pendingCount = orders.count { !it.isArchived && it.status == "pending" }
    val context = LocalContext.current

    // Active unarchived tables count
    val activeTablesCount = remember(tables, orders) {
        tables.count { table ->
            orders.any { ord -> ord.tableId == table.id && !ord.isArchived && ord.status != "cancelled" && (!ord.isFullyPaid() || ord.status == "pending" || ord.status == "preparing" || ord.status == "ready") }
        }
    }

    // Listen UI message toasts
    LaunchedEffect(Unit) {
        viewModel.uiMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val isRootScreen = currentScreen is Screen.Orders || currentScreen is Screen.ActiveTables || currentScreen is Screen.Menu || currentScreen is Screen.QrMenu || currentScreen is Screen.Settings

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        bottomBar = {
            if (isRootScreen) {
                NavigationBar(
                    containerColor = ForestGreen,
                    tonalElevation = 8.dp
                ) {
                    val navItemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WarmGold,
                        selectedTextColor = Color.White,
                        indicatorColor = SageGreen,
                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                        unselectedTextColor = Color.White.copy(alpha = 0.6f)
                    )

                    // 1. Siparişler (Canlı Akış)
                    NavigationBarItem(
                        selected = currentScreen is Screen.Orders,
                        onClick = { currentScreen = Screen.Orders },
                        colors = navItemColors,
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (pendingCount > 0) {
                                        Badge(containerColor = WarmGold) {
                                            Text("$pendingCount", color = ForestGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = "Siparişler")
                            }
                        },
                        label = { Text("Siparişler", fontSize = 11.sp, fontWeight = if (currentScreen is Screen.Orders) FontWeight.Bold else FontWeight.Normal) }
                    )

                    // 2. Açık Masalar (Sadece içinde ürün/sipariş olan masalar)
                    NavigationBarItem(
                        selected = currentScreen is Screen.ActiveTables,
                        onClick = { currentScreen = Screen.ActiveTables },
                        colors = navItemColors,
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (activeTablesCount > 0) {
                                        Badge(containerColor = WarmGold) {
                                            Text("$activeTablesCount", color = ForestGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.TableBar, contentDescription = "Açık Masalar")
                            }
                        },
                        label = { Text("Masalar", fontSize = 11.sp, fontWeight = if (currentScreen is Screen.ActiveTables) FontWeight.Bold else FontWeight.Normal) }
                    )

                    // 3. Menü (Kategoriler & Ürünler)
                    NavigationBarItem(
                        selected = currentScreen is Screen.Menu,
                        onClick = { currentScreen = Screen.Menu },
                        colors = navItemColors,
                        icon = { Icon(Icons.Default.RestaurantMenu, contentDescription = "Menü") },
                        label = { Text("Menü", fontSize = 11.sp, fontWeight = if (currentScreen is Screen.Menu) FontWeight.Bold else FontWeight.Normal) }
                    )

                    // 4. QR Menü (Masa QR Kodları & Ekleme)
                    NavigationBarItem(
                        selected = currentScreen is Screen.QrMenu,
                        onClick = { currentScreen = Screen.QrMenu },
                        colors = navItemColors,
                        icon = { Icon(Icons.Default.QrCode2, contentDescription = "QR Menü") },
                        label = { Text("QR Menü", fontSize = 11.sp, fontWeight = if (currentScreen is Screen.QrMenu) FontWeight.Bold else FontWeight.Normal) }
                    )

                    // 5. Ayarlar
                    NavigationBarItem(
                        selected = currentScreen is Screen.Settings,
                        onClick = { currentScreen = Screen.Settings },
                        colors = navItemColors,
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Ayarlar") },
                        label = { Text("Ayarlar", fontSize = 11.sp, fontWeight = if (currentScreen is Screen.Settings) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            when (val screen = currentScreen) {
                is Screen.Orders -> {
                    OrdersScreen(
                        viewModel = viewModel,
                        onOrderClick = { currentScreen = Screen.OrderDetail(it) }
                    )
                }
                is Screen.ActiveTables -> {
                    ActiveTablesScreen(
                        viewModel = viewModel
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
                        onCategoryClick = { currentScreen = Screen.CategoryDetail(it) }
                    )
                }
                is Screen.CategoryDetail -> {
                    CategoryDetailScreen(
                        category = screen.category,
                        viewModel = viewModel,
                        onBack = { currentScreen = Screen.Menu },
                        onAddProduct = { currentScreen = Screen.AddEditProduct(null, screen.category.id) },
                        onEditProduct = { currentScreen = Screen.AddEditProduct(it, screen.category.id) }
                    )
                }
                is Screen.AddEditProduct -> {
                    AddEditProductScreen(
                        editingItem = screen.item,
                        defaultCategoryId = screen.defaultCategoryId,
                        viewModel = viewModel,
                        onBack = {
                            val targetCat = viewModel.categories.value.find { it.id == screen.defaultCategoryId }
                            currentScreen = if (targetCat != null) Screen.CategoryDetail(targetCat) else Screen.Menu
                        }
                    )
                }
                is Screen.QrMenu -> {
                    TableManagementScreen(viewModel = viewModel)
                }
                is Screen.Settings -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateToDashboard = { currentScreen = Screen.Dashboard },
                        onSignOut = onSignOut
                    )
                }
                is Screen.Dashboard -> {
                    DashboardScreen(
                        viewModel = viewModel,
                        onBack = { currentScreen = Screen.Settings }
                    )
                }
            }
        }
    }
}
