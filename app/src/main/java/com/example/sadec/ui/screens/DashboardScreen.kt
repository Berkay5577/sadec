package com.example.sadec.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sadec.data.model.MenuItem
import com.example.sadec.data.model.Order
import com.example.sadec.data.model.OrderItem
import com.example.sadec.data.model.TableItem
import com.example.sadec.ui.theme.*
import com.example.sadec.ui.viewmodel.MainViewModel
import com.example.sadec.util.DailyZReportPdfGenerator
import com.example.sadec.util.ExcelReportGenerator
import com.example.sadec.util.WeeklyReportPdfGenerator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ProductSaleStat(
    val name: String,
    val totalQuantity: Int,
    val totalRevenue: Double
)

data class TableSaleStat(
    val label: String,
    val orderCount: Int,
    val totalRevenue: Double
)

data class DaySummaryStat(
    val dateKey: String,
    val dayName: String,
    val orderCount: Int,
    val itemsCount: Int,
    val cardRevenue: Double,
    val cashRevenue: Double,
    val transferRevenue: Double,
    val totalRevenue: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val orders by viewModel.orders.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()
    val tables by viewModel.tables.collectAsState()
    val restaurant by viewModel.restaurant.collectAsState()
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        "Özet",
        "Günler",
        "Masalar",
        "Adisyonlar"
    )
    var showManualCashSheet by remember { mutableStateOf(false) }

    // Weekly Period Label
    val cal = Calendar.getInstance()
    val currentWeekYear = cal.get(Calendar.YEAR)
    val currentWeekNum = cal.get(Calendar.WEEK_OF_YEAR)
    val currentWeekPeriod = "$currentWeekYear-Hafta$currentWeekNum"

    val sdfDateKey = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
    val todayDateStr = sdfDateKey.format(Date())

    // Dialog states
    var showWeeklyResetDialog by remember { mutableStateOf(false) }
    var hasDownloadedPdf by remember { mutableStateOf(false) }
    var showZReportConfirmDialog by remember { mutableStateOf(false) }

    // Date Filter Index: 0: Gün Özeti (Bugün), 1: Haftalık Özet, 2: Tüm Geçmiş
    var dateFilterIndex by remember { mutableStateOf(0) }

    val displayedOrders = remember(orders, dateFilterIndex) {
        when (dateFilterIndex) {
            0 -> orders.filter { !it.isArchived && !it.isDayClosed && it.status != "cancelled" }
            1 -> orders.filter { !it.isArchived && it.status != "cancelled" }
            else -> orders.filter { it.status != "cancelled" }
        }
    }

    // Active unarchived completed orders for this week
    val activeWeeklyOrders = remember(orders) {
        orders.filter { !it.isArchived && it.status != "cancelled" && (it.status == "delivered" || it.items.any { item -> item.isPaid }) }
    }

    // Completed / Paid orders in the active filter
    val completedOrders = remember(displayedOrders) {
        displayedOrders.filter { it.status == "delivered" || it.items.any { item -> item.isPaid } }
    }

    // Total Net Revenue calculation
    val totalRevenue = remember(completedOrders) {
        completedOrders.sumOf { order ->
            val paidItems = order.items.filter { it.isPaid }
            if (paidItems.isNotEmpty()) paidItems.sumOf { it.effectivePrice() }
            else order.totalPrice
        }
    }

    // Payment Methods Breakdown
    val cardTotal = remember(completedOrders) { completedOrders.sumOf { it.cardPaidAmount() } }
    val cashTotal = remember(completedOrders) { completedOrders.sumOf { it.cashPaidAmount() } }
    val transferTotal = remember(completedOrders) { completedOrders.sumOf { it.transferPaidAmount() } }
    val complimentaryTotal = remember(completedOrders) { completedOrders.sumOf { it.complimentaryAmount() } }

    val totalItemsSold = remember(completedOrders) {
        completedOrders.sumOf { order ->
            order.items.sumOf { it.quantity }
        }
    }

    val avgPerTable = remember(completedOrders, totalRevenue) {
        if (completedOrders.isNotEmpty()) totalRevenue / completedOrders.size else 0.0
    }

    // Product Sales Stats
    val productStats = remember(completedOrders) {
        val map = mutableMapOf<String, Pair<Int, Double>>()
        completedOrders.forEach { order ->
            order.items.forEach { item ->
                val prev = map.getOrDefault(item.name, Pair(0, 0.0))
                map[item.name] = Pair(
                    prev.first + item.quantity,
                    prev.second + item.effectivePrice()
                )
            }
        }
        map.map { (name, pair) ->
            ProductSaleStat(name = name, totalQuantity = pair.first, totalRevenue = pair.second)
        }.sortedByDescending { it.totalQuantity }
    }

    // Table Sales Stats
    val tableStats = remember(completedOrders) {
        val map = mutableMapOf<String, Pair<Int, Double>>()
        completedOrders.forEach { order ->
            val label = order.tableLabel.ifBlank { "Diğer" }
            val prev = map.getOrDefault(label, Pair(0, 0.0))
            val orderTotal = if (order.items.any { it.isPaid }) order.paidAmount() else order.totalPrice
            map[label] = Pair(
                prev.first + 1,
                prev.second + orderTotal
            )
        }
        map.map { (label, pair) ->
            TableSaleStat(label = label, orderCount = pair.first, totalRevenue = pair.second)
        }.sortedByDescending { it.totalRevenue }
    }

    // Day-by-Day Stats for the Active Week
    val weeklyDayStats = remember(activeWeeklyOrders) {
        val sdfDay = SimpleDateFormat("EEEE", Locale("tr", "TR"))
        val grouped = activeWeeklyOrders.groupBy { order ->
            order.createdAt?.let { sdfDateKey.format(it) } ?: "Tarihsiz"
        }
        grouped.map { (dKey, dOrders) ->
            val sampleDate = dOrders.firstOrNull()?.createdAt
            val dayName = sampleDate?.let { sdfDay.format(it).uppercase(Locale("tr")) } ?: ""
            DaySummaryStat(
                dateKey = dKey,
                dayName = dayName,
                orderCount = dOrders.size,
                itemsCount = dOrders.sumOf { it.items.sumOf { i -> i.quantity } },
                cardRevenue = dOrders.sumOf { it.cardPaidAmount() },
                cashRevenue = dOrders.sumOf { it.cashPaidAmount() },
                transferRevenue = dOrders.sumOf { it.transferPaidAmount() },
                totalRevenue = dOrders.sumOf { it.paidAmount().let { p -> if (p > 0) p else it.totalPrice } }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = {
                    Column {
                        Text(
                            text = "Kasa & Finansal Dashboard",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 17.sp
                        )
                        Text(
                            text = restaurant?.name ?: "Sade.C Kahve Gerze",
                            color = WarmGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        ExcelReportGenerator.generateAndShareExcelReport(
                            context = context,
                            orders = activeWeeklyOrders,
                            restaurantName = restaurant?.name ?: "Sade.C Kahve Gerze",
                            weekPeriod = currentWeekPeriod
                        )
                    }) {
                        Surface(
                            color = SageGreen,
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.FileDownload, contentDescription = "Excel", tint = WarmGold, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    IconButton(onClick = {
                        WeeklyReportPdfGenerator.generateAndShareWeeklyReport(
                            context = context,
                            orders = activeWeeklyOrders,
                            restaurantName = restaurant?.name ?: "Sade.C Kahve Gerze",
                            weekPeriod = currentWeekPeriod
                        )
                    }) {
                        Surface(
                            color = SageGreen,
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ForestGreen,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFFBF8F3))
        ) {
            // 🌟 MODERN SEGMENTED PERIOD CONTROL
            Surface(
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    // Segmented Button Bar
                    Surface(
                        color = Color(0xFFF1F5F3),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                Pair("Bugün", 0),
                                Pair("Bu Hafta", 1),
                                Pair("Geçmiş", 2)
                            ).forEach { (title, idx) ->
                                val isSelected = dateFilterIndex == idx
                                Surface(
                                    color = if (isSelected) ForestGreen else Color.Transparent,
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { dateFilterIndex = idx }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = title,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) WarmGold else Slate500,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Sabit 4'lü Tab Bar (Sadece Temiz Metinler)
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = ForestGreen,
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val isTabSelected = selectedTab == index
                            Tab(
                                selected = isTabSelected,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = title,
                                        fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.5.sp,
                                        color = if (isTabSelected) ForestGreen else Slate500,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> SalesAnalyticsTab(
                    dateFilterIndex = dateFilterIndex,
                    totalRevenue = totalRevenue,
                    orderCount = completedOrders.size,
                    totalItemsSold = totalItemsSold,
                    avgPerTable = avgPerTable,
                    cardTotal = cardTotal,
                    cashTotal = cashTotal,
                    transferTotal = transferTotal,
                    complimentaryTotal = complimentaryTotal,
                    productStats = productStats,
                    onExportExcel = {
                        ExcelReportGenerator.generateAndShareExcelReport(
                            context = context,
                            orders = activeWeeklyOrders,
                            restaurantName = restaurant?.name ?: "Sade.C Kahve Gerze",
                            weekPeriod = currentWeekPeriod
                        )
                    },
                    onExportPdf = {
                        WeeklyReportPdfGenerator.generateAndShareWeeklyReport(
                            context = context,
                            orders = activeWeeklyOrders,
                            restaurantName = restaurant?.name ?: "Sade.C Kahve Gerze",
                            weekPeriod = currentWeekPeriod
                        )
                    },
                    onOpenZReportConfirm = {
                        showZReportConfirmDialog = true
                    },
                    onTriggerWeeklyReset = {
                        showWeeklyResetDialog = true
                        hasDownloadedPdf = false
                    },
                    onOpenManualCash = {
                        showManualCashSheet = true
                    }
                )
                1 -> DayByDayWeeklyTab(
                    weeklyDayStats = weeklyDayStats,
                    totalWeeklyRevenue = activeWeeklyOrders.sumOf { it.paidAmount().let { p -> if (p > 0) p else it.totalPrice } }
                )
                2 -> TableAnalyticsTab(
                    totalRevenue = totalRevenue,
                    tableStats = tableStats
                )
                3 -> SalesHistoryTab(
                    completedOrders = completedOrders
                )
                4 -> ManualCashEntryTab(
                    menuItems = menuItems,
                    tables = tables,
                    onSaveManualOrder = { order ->
                        viewModel.createManualOrder(order)
                    }
                )
            }
        }

        // 🌙 GÜN SONU Z-RAPORU & GÜN KAPANIŞI ONAY DİYALOĞU
        if (showZReportConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showZReportConfirmDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = ForestGreen, shape = CircleShape, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🌙", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Gün Sonu Z-Raporu & Kapanış",
                            fontWeight = FontWeight.Bold,
                            color = ForestGreen,
                            fontSize = 17.sp
                        )
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Bugünün Z-Raporunu PDF olarak çıkartıp bugünkü kasayı kapatmayı onaylıyor musunuz?",
                            fontSize = 13.sp,
                            color = Slate500,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            color = SoftMintGreen,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Bugünün Kasa Mutabakatı ($todayDateStr):", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = ForestGreen)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("• Net Günlük Ciro: ₺${"%.2f".format(totalRevenue)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WarmGold)
                                Text("• 💳 Kredi Kartı: ₺${"%.2f".format(cardTotal)}", fontSize = 12.5.sp, color = ForestGreen)
                                Text("• 💵 Nakit Kasa: ₺${"%.2f".format(cashTotal)}", fontSize = 12.5.sp, color = Color(0xFF166534))
                                if (transferTotal > 0) Text("• 📲 Havale/EFT: ₺${"%.2f".format(transferTotal)}", fontSize = 12.5.sp, color = Color(0xFF1E40AF))
                                if (complimentaryTotal > 0) Text("• 🎁 İkram/İndirim: ₺${"%.2f".format(complimentaryTotal)}", fontSize = 12.5.sp, color = Color(0xFF92400E))
                                Text("• Toplam Adisyon: ${completedOrders.size} Adet ($totalItemsSold Ürün)", fontSize = 12.sp, color = SageGreen)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("ℹ️", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Onaylandığında; Gün Özeti ve günün masa analizi yeni güne sıfırlanır. Tüm veriler Haftalık Raporda ve arşivde eksiksiz olarak kalmaya devam eder.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF92400E),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            DailyZReportPdfGenerator.generateAndShareDailyZReportPdf(
                                context = context,
                                orders = completedOrders,
                                productStats = productStats,
                                tableStats = tableStats,
                                restaurantName = restaurant?.name ?: "Sade.C Kahve Gerze"
                            )
                            viewModel.closeDailyZReport(todayDateStr) {
                                showZReportConfirmDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Z-Raporunu Al & Günü Kapat ✨", color = WarmGold, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showZReportConfirmDialog = false }) {
                        Text("Vazgeç", color = Slate500)
                    }
                }
            )
        }

        // 🛡️ HAFTALIK KASA KAPATMA DİYALOĞU
        if (showWeeklyResetDialog) {
            AlertDialog(
                onDismissRequest = { showWeeklyResetDialog = false },
                title = {
                    Text(
                        text = "Haftalık Kasa Kapatma & Sıfırlama 📊",
                        fontWeight = FontWeight.Bold,
                        color = ForestGreen
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Bu haftanın satışlarını arşivleyip kasayı yeni haftaya sıfırlamadan önce Excel raporunu indirmeniz zorunludur.",
                            fontSize = 13.sp,
                            color = Slate500,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        val weeklyNet = activeWeeklyOrders.sumOf { it.paidAmount().let { p -> if (p > 0) p else it.totalPrice } }
                        Surface(
                            color = SoftMintGreen,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Bu Haftanın Genel Kasa Özeti:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ForestGreen)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("• Toplam Haftalık Ciro: ₺${"%.2f".format(weeklyNet)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WarmGold)
                                Text("• Kredi Kartı: ₺${"%.2f".format(activeWeeklyOrders.sumOf { it.cardPaidAmount() })}", fontSize = 12.sp, color = ForestGreen)
                                Text("• Nakit: ₺${"%.2f".format(activeWeeklyOrders.sumOf { it.cashPaidAmount() })}", fontSize = 12.sp, color = ForestGreen)
                                Text("• Toplam Adisyon: ${activeWeeklyOrders.size} Adet", fontSize = 12.sp, color = SageGreen)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                ExcelReportGenerator.generateAndShareExcelReport(
                                    context = context,
                                    orders = activeWeeklyOrders,
                                    restaurantName = restaurant?.name ?: "Sade.C Kahve Gerze",
                                    weekPeriod = currentWeekPeriod,
                                    onSuccess = { hasDownloadedPdf = true }
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (hasDownloadedPdf) SageGreen else ForestGreen)
                        ) {
                            Icon(
                                imageVector = if (hasDownloadedPdf) Icons.Default.CheckCircle else Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = WarmGold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasDownloadedPdf) "1. Adım: Excel İndirildi ✅" else "1. Adım: Raporu İndir (Excel) 📥",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                viewModel.archiveWeeklyOrders(currentWeekPeriod) {
                                    showWeeklyResetDialog = false
                                    hasDownloadedPdf = false
                                }
                            },
                            enabled = hasDownloadedPdf,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WarmGold,
                                disabledContainerColor = Slate500.copy(alpha = 0.2f)
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = if (hasDownloadedPdf) ForestGreen else Slate500)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "2. Adım: Kasayı Sıfırla & Yeni Haftaya Başla",
                                fontWeight = FontWeight.Bold,
                                color = if (hasDownloadedPdf) ForestGreen else Slate500
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showWeeklyResetDialog = false }) {
                        Text("Vazgeç & Kapat", color = Slate500)
                    }
                }
            )
        }

        // ➕ MANUEL SATIŞ BOTTOM SHEET
        if (showManualCashSheet) {
            ModalBottomSheet(
                onDismissRequest = { showManualCashSheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                ManualCashEntryTab(
                    menuItems = menuItems,
                    tables = tables,
                    onSaveManualOrder = { order ->
                        viewModel.createManualOrder(order)
                        showManualCashSheet = false
                    }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 0: SALES & PRODUCT ANALYTICS (PREMIUM REDESIGNED)
// -------------------------------------------------------------
@Composable
fun SalesAnalyticsTab(
    dateFilterIndex: Int,
    totalRevenue: Double,
    orderCount: Int,
    totalItemsSold: Int,
    avgPerTable: Double,
    cardTotal: Double,
    cashTotal: Double,
    transferTotal: Double,
    complimentaryTotal: Double,
    productStats: List<ProductSaleStat>,
    onExportExcel: () -> Unit,
    onExportPdf: () -> Unit,
    onOpenZReportConfirm: () -> Unit,
    onTriggerWeeklyReset: () -> Unit,
    onOpenManualCash: () -> Unit
) {
    val periodLabel = when (dateFilterIndex) {
        0 -> "BUGÜNÜN NET CİROSU"
        1 -> "BU HAFTANIN GENEL CİROSU"
        else -> "TÜM GEÇMİŞ DÖNEM CİROSU"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 🌟 1. HERO FINANCIAL HEADER CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = ForestGreen),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ForestGreen, Color(0xFF264A3D), Color(0xFF1E3A2F))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = periodLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                            Surface(
                                color = WarmGold.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(6.dp).background(WarmGold, CircleShape))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Canlı Kasa",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WarmGold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Big Revenue Text
                        Text(
                            text = "₺${"%.2f".format(totalRevenue)}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WarmGold,
                            letterSpacing = (-0.5).sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Embedded Sub-metrics in pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)) {
                                    Text("Adisyon", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("$orderCount Adet", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Surface(
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)) {
                                    Text("Satılan Ürün", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("$totalItemsSold Ürün", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Surface(
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)) {
                                    Text("Ort. Masa", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("₺${"%.0f".format(avgPerTable)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WarmGold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 💳 2. PAYMENT METHODS BREAKDOWN
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ÖDEME & TAHSİLAT DAĞILIMI",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = ForestGreen
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Kredi Kartı
                        Surface(
                            color = Color(0xFFF0FDF4),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("💳", fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Kredi Kartı", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = ForestGreen)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("₺${"%.2f".format(cardTotal)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                            }
                        }

                        // Nakit
                        Surface(
                            color = Color(0xFFF0FDF4),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("💵", fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Nakit Kasa", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF166534))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("₺${"%.2f".format(cashTotal)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Havale / FAST
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFDBEAFE)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📲", fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Havale/EFT", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E40AF))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("₺${"%.2f".format(transferTotal)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                            }
                        }

                        // İkram & İndirim
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🎁", fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("İkram/İndirim", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF92400E))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("₺${"%.2f".format(complimentaryTotal)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                            }
                        }
                    }
                }
            }
        }

        // 🌟 3. OFFICIAL ACTIONS & REPORTING CENTER
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "RESMİ KASA İŞLEMLERİ & RAPORLAR",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = ForestGreen
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // PRIMARY HERO BUTTON: Gün Sonu Z-Raporu
                    Button(
                        onClick = onOpenZReportConfirm,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = WarmGold, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🌙 Gün Sonu Z-Raporu Al & Günü Kapat (PDF)",
                            color = WarmGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary Actions: Excel & PDF
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onExportExcel,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Haftalık Excel", color = ForestGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = onExportPdf,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Haftalık PDF", color = ForestGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Manuel Satış Girişi Butonu
                    Button(
                        onClick = onOpenManualCash,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SageGreen)
                    ) {
                        Icon(Icons.Default.PointOfSale, contentDescription = null, tint = WarmGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("➕ Manuel Kasa Satışı Yap", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Weekly Reset Button
                    Surface(
                        color = Color(0xFFFBF8F3),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, WarmGold.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTriggerWeeklyReset() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📅", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Haftalık Kasa Kapatma & Arşivleme", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                                    Text("Her Pazar gecesi haftayı kapatmak için kullanılır", fontSize = 10.5.sp, color = Slate500)
                                }
                            }
                            Text("Kapat →", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarmGold)
                        }
                    }
                }
            }
        }

        // 🌟 4. PRODUCT SALES BREAKDOWN (Only visible when there are sales)
        if (productStats.isNotEmpty()) {
            item {
                Text(
                    text = "EN ÇOK SATAN ÜRÜNLER",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = ForestGreen
                )
            }

            items(productStats) { stat ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stat.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ForestGreen
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Toplam ${stat.totalQuantity} Adet Satıldı",
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        }

                        Surface(
                            color = SoftMintGreen,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "₺${"%.2f".format(stat.totalRevenue)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ForestGreen,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: DAY-BY-DAY WEEKLY REVENUE TAB
// -------------------------------------------------------------
@Composable
fun DayByDayWeeklyTab(
    weeklyDayStats: List<DaySummaryStat>,
    totalWeeklyRevenue: Double
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "GÜN GÜN HAFTALIK KASA VE CİRO DÖKÜMÜ",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = ForestGreen
            )
        }

        if (weeklyDayStats.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📅", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Bu hafta henüz tamamlanmış günlük satış kaydı bulunmuyor.", color = Slate500, fontSize = 13.sp)
                        }
                    }
                }
            }
        } else {
            items(weeklyDayStats) { stat ->
                val percentage = if (totalWeeklyRevenue > 0) ((stat.totalRevenue / totalWeeklyRevenue) * 100).toInt() else 0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = SoftMintGreen,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${stat.dateKey} ${stat.dayName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ForestGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Text(
                                text = "₺${"%.2f".format(stat.totalRevenue)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = WarmGold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Stats Row
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = Color(0xFFF1F5F3),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Adisyon", fontSize = 10.sp, color = Slate500)
                                    Text("${stat.orderCount}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                                }
                            }
                            Surface(
                                color = Color(0xFFF1F5F3),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Ürün", fontSize = 10.sp, color = Slate500)
                                    Text("${stat.itemsCount}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SageGreen)
                                }
                            }
                            Surface(
                                color = SoftMintGreen,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Kart", fontSize = 10.sp, color = ForestGreen)
                                    Text("₺${"%.2f".format(stat.cardRevenue)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                                }
                            }
                            Surface(
                                color = Color(0xFFF0FDF4),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Nakit", fontSize = 10.sp, color = Color(0xFF166534))
                                    Text("₺${"%.2f".format(stat.cashRevenue)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { if (totalWeeklyRevenue > 0) (stat.totalRevenue / totalWeeklyRevenue).toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = ForestGreen,
                            trackColor = ForestGreen.copy(alpha = 0.1f)
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Haftalık cironun %$percentage'si bu günde gerçekleşti.",
                            fontSize = 10.5.sp,
                            color = Slate500
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: TABLE ANALYTICS
// -------------------------------------------------------------
@Composable
fun TableAnalyticsTab(
    totalRevenue: Double,
    tableStats: List<TableSaleStat>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "MASALARA GÖRE CİRO VE YOĞUNLUK",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = ForestGreen
            )
        }

        if (tableStats.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📍", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Masa bazlı satış kaydı bulunmuyor.", color = Slate500, fontSize = 13.sp)
                        }
                    }
                }
            }
        } else {
            items(tableStats) { stat ->
                val percentage = if (totalRevenue > 0) ((stat.totalRevenue / totalRevenue) * 100).toInt() else 0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = SoftMintGreen,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "📍 ${stat.label}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = ForestGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${stat.orderCount} Sipariş", fontSize = 12.sp, color = Slate500)
                            }

                            Text(
                                text = "₺${"%.2f".format(stat.totalRevenue)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = WarmGold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { if (totalRevenue > 0) (stat.totalRevenue / totalRevenue).toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = ForestGreen,
                            trackColor = ForestGreen.copy(alpha = 0.1f)
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Toplam cironun %$percentage'si bu masadan elde edildi.",
                            fontSize = 10.5.sp,
                            color = Slate500
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: CHRONOLOGICAL SALES HISTORY
// -------------------------------------------------------------
@Composable
fun SalesHistoryTab(
    completedOrders: List<Order>
) {
    val sdfDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr"))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "TAMAMLANAN TÜM ADİSYONLAR (${completedOrders.size})",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = ForestGreen
            )
        }

        if (completedOrders.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📜", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Tamamlanmış satış kaydı bulunmuyor.", color = Slate500, fontSize = 13.sp)
                        }
                    }
                }
            }
        } else {
            items(completedOrders) { order ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = SoftMintGreen,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = order.tableLabel.ifBlank { "Masa" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = ForestGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "👤 ${order.customerName.ifBlank { "Misafir" }}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ForestGreen
                                )
                            }

                            Text(
                                text = "₺${"%.2f".format(order.paidAmount().let { if (it > 0) it else order.totalPrice })}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = WarmGold
                            )
                        }

                        val dateStr = order.createdAt?.let { sdfDate.format(it) } ?: ""
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🕒 $dateStr", fontSize = 11.sp, color = Slate500)

                            val methodTag = when {
                                order.cardPaidAmount() > 0 -> "💳 Kredi Kartı"
                                order.cashPaidAmount() > 0 -> "💵 Nakit"
                                order.transferPaidAmount() > 0 -> "📲 Havale"
                                order.complimentaryAmount() > 0 -> "🎁 İkram"
                                else -> "💳 Ödendi"
                            }
                            Surface(
                                color = ForestGreen.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(methodTag, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = ForestGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = ForestGreen.copy(alpha = 0.08f))

                        // Items list
                        order.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${item.quantity}x ${item.name}",
                                        fontSize = 12.sp,
                                        color = ForestGreen
                                    )
                                    if (item.isComplimentary) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("(İKRAM 🎁)", fontSize = 10.sp, color = Color(0xFFB45309), fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(
                                    text = "₺${"%.2f".format(item.effectivePrice())}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (item.isComplimentary) SuccessGreen else ForestGreen
                                )
                            }
                        }

                        if (order.note.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Not: ${order.note}",
                                fontSize = 11.sp,
                                color = Color(0xFFD97706),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 4: MANUAL CASH ENTRY (ELDEN KASA GİRİŞİ)
// -------------------------------------------------------------
@Composable
fun ManualCashEntryTab(
    menuItems: List<MenuItem>,
    tables: List<TableItem>,
    onSaveManualOrder: (Order) -> Unit
) {
    var customerNameInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTableId by remember { mutableStateOf("table-kasa") }
    var selectedTableLabel by remember { mutableStateOf("KASA") }
    var selectedPaymentMethod by remember { mutableStateOf("cash") }
    val cart = remember { mutableStateListOf<OrderItem>() }

    val filteredMenuItems = remember(menuItems, searchQuery) {
        if (searchQuery.isBlank()) menuItems
        else menuItems.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Manuel Kasa Satış Girişi", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ForestGreen)
                    Text("Garson veya kasiyer elden nakit/kart satışı doğrudan sisteme işleyebilir.", fontSize = 12.sp, color = Slate500)

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = customerNameInput,
                        onValueChange = { customerNameInput = it },
                        label = { Text("Müşteri Adı (İsteğe Bağlı)") },
                        placeholder = { Text("Örn: Ahmet Bey") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Ödeme Yöntemi:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("cash", "💵 Nakit", "cash"),
                            Triple("card", "💳 Kart", "card"),
                            Triple("transfer", "📲 Havale", "transfer"),
                            Triple("complimentary", "🎁 İkram", "complimentary")
                        ).forEach { (id, label, _) ->
                            FilterChip(
                                selected = selectedPaymentMethod == id,
                                onClick = { selectedPaymentMethod = id },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ForestGreen, selectedLabelColor = WarmGold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Masa Seçin:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tables.take(4).forEach { table ->
                            FilterChip(
                                selected = selectedTableId == table.id,
                                onClick = {
                                    selectedTableId = table.id
                                    selectedTableLabel = table.label
                                },
                                label = { Text(table.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ForestGreen, selectedLabelColor = WarmGold)
                            )
                        }
                    }
                }
            }
        }

        item {
            Column {
                Text(
                    text = "MENÜDEN ÜRÜN SEÇİN",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = ForestGreen
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Ürün veya kahve ara...", fontSize = 13.sp, color = Slate500) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ForestGreen) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Temizle", tint = Slate500)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = ForestGreen.copy(alpha = 0.2f),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (filteredMenuItems.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "'$searchQuery' ile eşleşen ürün bulunamadı." else "Menüde ürün bulunmuyor.",
                            color = Slate500,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        items(filteredMenuItems) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ForestGreen)
                        Text("₺${"%.2f".format(item.price)}", fontSize = 13.sp, color = WarmGold, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val existing = cart.indexOfFirst { it.menuItemId == item.id }
                            if (existing > -1) {
                                val cur = cart[existing]
                                cart[existing] = cur.copy(quantity = cur.quantity + 1)
                            } else {
                                cart.add(
                                    OrderItem(
                                        menuItemId = item.id,
                                        name = item.name,
                                        quantity = 1,
                                        unitPrice = item.price,
                                        isPaid = true,
                                        paymentMethod = selectedPaymentMethod
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("+ Ekle", color = WarmGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (cart.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftMintGreen)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Seçilen Ürünler (Sepet)", fontWeight = FontWeight.Bold, color = ForestGreen)
                        Spacer(modifier = Modifier.height(8.dp))

                        cart.forEach { cItem ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${cItem.quantity}x ${cItem.name}", fontSize = 13.sp, color = ForestGreen)
                                Text("₺${"%.2f".format(cItem.unitPrice * cItem.quantity)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WarmGold)
                            }
                        }

                        val totalCart = cart.sumOf { it.unitPrice * it.quantity }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = ForestGreen.copy(alpha = 0.2f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Toplam:", fontWeight = FontWeight.Bold, color = ForestGreen)
                            Text("₺${"%.2f".format(totalCart)}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = WarmGold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val newOrder = Order(
                                    tableId = selectedTableId.ifBlank { "table-kasa" },
                                    tableLabel = selectedTableLabel.ifBlank { "KASA" },
                                    customerName = customerNameInput.ifBlank { "Elden Müşteri" },
                                    status = "delivered",
                                    paymentMethod = selectedPaymentMethod,
                                    items = cart.map {
                                        it.copy(
                                            isPaid = true,
                                            paymentMethod = selectedPaymentMethod,
                                            paidAt = System.currentTimeMillis()
                                        )
                                    },
                                    totalPrice = totalCart
                                )
                                onSaveManualOrder(newOrder)
                                cart.clear()
                                customerNameInput = ""
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                        ) {
                            Text("Satışı Tamamla & Kasaya İşle 💳", color = WarmGold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
