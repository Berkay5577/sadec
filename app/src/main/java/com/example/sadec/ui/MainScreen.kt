package com.example.sadec.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sadec.data.model.MenuItem
import com.example.sadec.data.model.Order
import com.example.sadec.ui.components.UpdateDialog
import com.example.sadec.ui.screens.*
import com.example.sadec.ui.theme.*
import com.example.sadec.ui.viewmodel.MainViewModel
import java.util.Calendar

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
    val restaurant by viewModel.restaurant.collectAsState()
    val pendingCount = orders.count { !it.isArchived && it.status == "pending" }
    val isWeeklyLockActive by viewModel.isWeeklyLockActive.collectAsState()
    val isStaffMode by viewModel.isStaffMode.collectAsState()
    var hasDownloadedWeeklyExcel by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // In-App Update States
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadStatusText by viewModel.downloadStatusText.collectAsState()

    // Check for update automatically
    LaunchedEffect(restaurant?.appUpdateInfo) {
        viewModel.checkAndPromptAutoUpdate(restaurant?.appUpdateInfo)
    }

    // Active unarchived tables count (unpaid orders only)
    val activeTablesCount = remember(tables, orders) {
        tables.count { table ->
            orders.any { ord -> ord.tableId == table.id && !ord.isArchived && ord.status != "cancelled" && !ord.isFullyPaid() }
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

                    // 2. Masalar (Sadece içinde ürün/sipariş olan açık masalar)
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
                                Icon(Icons.Default.TableBar, contentDescription = "Masalar")
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
                    if (isStaffMode) {
                        currentScreen = Screen.Settings
                    } else {
                        DashboardScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = Screen.Settings }
                        )
                    }
                }
            }
        }
    }

    // Sunday Midnight / New Week Mandatory Unbypassable Lock Dialog (Sadece Yönetici Modunda Çalışır)
    if (isWeeklyLockActive && !isStaffMode) {
        val cal = Calendar.getInstance()
        val currentWeekPeriod = "${cal.get(Calendar.YEAR)}-Hafta${cal.get(Calendar.WEEK_OF_YEAR)}"
        val unarchivedOrders = orders.filter { !it.isArchived }

        AlertDialog(
            onDismissRequest = { /* Geçilemez / Kapatılamaz */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = DangerRed, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Haftalık Kasa Kapatma", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ForestGreen)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "⚠️ Yeni haftaya girildi (Pazar gecesi 00:00 tamamlandı).",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB45309),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Uygulamayı kullanmaya devam edebilmek için geçen haftanın satış detaylarını (gün, saat, müşteri, masa, adet, tutar) içeren resmi Excel raporunu indirmeniz ZORUNLUDUR.",
                        fontSize = 12.sp,
                        color = Slate700,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        color = SoftMintGreen,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Dönem: $currentWeekPeriod", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                            val totalRevenue = unarchivedOrders.filter { it.status == "delivered" || it.items.any { i -> i.isPaid } }.sumOf { ord ->
                                if (ord.items.any { it.isPaid }) ord.items.filter { it.isPaid }.sumOf { it.unitPrice * it.quantity } else ord.totalPrice
                            }
                            Text("Arşivlenecek Ciro: ₺${"%.2f".format(totalRevenue)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                            Text("Toplam Adisyon: ${unarchivedOrders.size} Adet", fontSize = 12.sp, color = SageGreen)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step 1: Download Excel Button
                    Button(
                        onClick = {
                            val file = com.example.sadec.util.ExcelReportGenerator.generateAndShareExcelReport(
                                context = context,
                                orders = unarchivedOrders,
                                restaurantName = viewModel.restaurant.value?.name ?: "Sade.C Kahve Gerze",
                                weekPeriod = currentWeekPeriod,
                                onSuccess = {
                                    hasDownloadedWeeklyExcel = true
                                }
                            )
                            if (file != null) hasDownloadedWeeklyExcel = true
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (hasDownloadedWeeklyExcel) SageGreen else ForestGreen)
                    ) {
                        Icon(
                            imageVector = if (hasDownloadedWeeklyExcel) Icons.Default.CheckCircle else Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = WarmGold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasDownloadedWeeklyExcel) "1. Adım: Excel İndirildi ✅" else "1. Adım: Haftalık Raporu İndir (Excel) 📥",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Step 2: Reset & Unlock Button (Disabled until Step 1 complete)
                    Button(
                        onClick = {
                            viewModel.archiveWeeklyOrders(currentWeekPeriod) {
                                hasDownloadedWeeklyExcel = false
                            }
                        },
                        enabled = hasDownloadedWeeklyExcel,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarmGold,
                            disabledContainerColor = Slate500.copy(alpha = 0.2f)
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = if (hasDownloadedWeeklyExcel) ForestGreen else Slate500)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "2. Adım: Kasayı Sıfırla & Yeni Haftaya Başla",
                            fontWeight = FontWeight.Bold,
                            color = if (hasDownloadedWeeklyExcel) ForestGreen else Slate500
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    // In-App Update Modal Dialog
    val updateInfo = restaurant?.appUpdateInfo
    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo,
            isDownloading = isDownloadingUpdate,
            downloadProgress = downloadProgress,
            downloadStatusText = downloadStatusText,
            onDownloadAndInstall = {
                viewModel.downloadAndInstallUpdate(context, updateInfo.apkUrl)
            },
            onDismiss = {
                viewModel.dismissUpdateDialog(updateInfo.latestVersionCode)
            }
        )
    }
}
