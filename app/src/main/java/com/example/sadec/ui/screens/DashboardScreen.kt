package com.example.sadec.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sadec.data.model.MenuItem
import com.example.sadec.data.model.Order
import com.example.sadec.data.model.OrderItem
import com.example.sadec.ui.theme.*
import com.example.sadec.ui.viewmodel.MainViewModel
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
    val tabs = listOf("📊 Satış Analizi", "🏢 Masa Analizi", "📜 Satış Geçmişi", "➕ Kasa Girişi")

    // Weekly Period Label (e.g. 2026-W35)
    val cal = Calendar.getInstance()
    val currentWeekYear = cal.get(Calendar.YEAR)
    val currentWeekNum = cal.get(Calendar.WEEK_OF_YEAR)
    val currentWeekPeriod = "$currentWeekYear-Hafta$currentWeekNum"

    // Active unarchived completed orders for this week
    val activeWeeklyOrders = remember(orders) {
        orders.filter { !it.isArchived && it.status != "cancelled" && (it.status == "delivered" || it.items.any { item -> item.isPaid }) }
    }

    // Weekly Close & Mandatory Download State
    var showWeeklyResetDialog by remember { mutableStateOf(false) }
    var hasDownloadedPdf by remember { mutableStateOf(false) }

    // Date Filter for Reports (0: Bu Hafta (Aktif Kasa), 1: Bugün, 2: Tüm Zamanlar / Arşiv Dahil)
    var dateFilterIndex by remember { mutableStateOf(0) }

    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    val displayedOrders = remember(orders, dateFilterIndex) {
        when (dateFilterIndex) {
            0 -> orders.filter { !it.isArchived && it.status != "cancelled" }
            1 -> orders.filter { order ->
                if (order.status == "cancelled") false
                else (order.createdAt?.after(todayStart) ?: true)
            }
            else -> orders.filter { it.status != "cancelled" }
        }
    }

    // Completed / Paid orders in the active filter
    val completedOrders = remember(displayedOrders) {
        displayedOrders.filter { it.status == "delivered" || it.items.any { item -> item.isPaid } }
    }

    // Total Revenue calculation
    val totalRevenue = remember(completedOrders) {
        completedOrders.sumOf { order ->
            val paidItems = order.items.filter { it.isPaid }
            if (paidItems.isNotEmpty()) paidItems.sumOf { it.unitPrice * it.quantity }
            else order.totalPrice
        }
    }

    val totalItemsSold = remember(completedOrders) {
        completedOrders.sumOf { order ->
            order.items.sumOf { it.quantity }
        }
    }

    // Product Sales Stats
    val productStats = remember(completedOrders) {
        val map = mutableMapOf<String, Pair<Int, Double>>()
        completedOrders.forEach { order ->
            order.items.forEach { item ->
                val prev = map.getOrDefault(item.name, Pair(0, 0.0))
                map[item.name] = Pair(
                    prev.first + item.quantity,
                    prev.second + (item.unitPrice * item.quantity)
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
            val orderTotal = if (order.items.any { it.isPaid }) order.items.filter { it.isPaid }.sumOf { it.unitPrice * it.quantity } else order.totalPrice
            map[label] = Pair(
                prev.first + 1,
                prev.second + orderTotal
            )
        }
        map.map { (label, pair) ->
            TableSaleStat(label = label, orderCount = pair.first, totalRevenue = pair.second)
        }.sortedByDescending { it.totalRevenue }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = {
                    Text("Satış Dashboard & Raporlar", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        com.example.sadec.util.ExcelReportGenerator.generateAndShareExcelReport(
                            context = context,
                            orders = completedOrders,
                            restaurantName = restaurant?.name ?: "Sade.C Kahve Gerze",
                            weekPeriod = currentWeekPeriod
                        )
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Excel İndir", tint = WarmGold)
                    }

                    IconButton(onClick = {
                        WeeklyReportPdfGenerator.generateAndShareWeeklyReport(
                            context = context,
                            orders = completedOrders,
                            restaurantName = restaurant?.name ?: "Sade.C Kahve Gerze",
                            weekPeriod = currentWeekPeriod
                        )
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF İndir", tint = Color.White)
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
        ) {
            // Filter Chips Bar (Dönem Seçimi)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("📅 Bu Hafta (Aktif Kasa)", "☀️ Bugün", "🗄️ Tüm Geçmiş").forEachIndexed { idx, title ->
                    FilterChip(
                        selected = dateFilterIndex == idx,
                        onClick = { dateFilterIndex = idx },
                        label = { Text(title, fontSize = 12.sp, fontWeight = if (dateFilterIndex == idx) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ForestGreen,
                            selectedLabelColor = WarmGold
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            // Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = ForestGreen,
                edgePadding = 16.dp,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) ForestGreen else Slate500
                            )
                        }
                    )
                }
            }

            HorizontalDivider(color = ForestGreen.copy(alpha = 0.1f))

            when (selectedTab) {
                0 -> SalesAnalyticsTab(
                    totalRevenue = totalRevenue,
                    orderCount = completedOrders.size,
                    totalItemsSold = totalItemsSold,
                    productStats = productStats,
                    onExportExcel = {
                        com.example.sadec.util.ExcelReportGenerator.generateAndShareExcelReport(
                            context = context,
                            orders = completedOrders,
                            restaurantName = restaurant?.name ?: "Sade.C Kahve Gerze",
                            weekPeriod = currentWeekPeriod
                        )
                    },
                    onExportPdf = {
                        WeeklyReportPdfGenerator.generateAndShareWeeklyReport(
                            context = context,
                            orders = completedOrders,
                            restaurantName = restaurant?.name ?: "Sade.C Kahve Gerze",
                            weekPeriod = currentWeekPeriod
                        )
                    },
                    onTriggerWeeklyReset = {
                        hasDownloadedPdf = false
                        showWeeklyResetDialog = true
                    }
                )
                1 -> TableAnalyticsTab(
                    tableStats = tableStats,
                    totalRevenue = totalRevenue
                )
                2 -> SalesHistoryTab(
                    completedOrders = completedOrders
                )
                3 -> ManualOrderEntryTab(
                    menuItems = menuItems,
                    tables = tables,
                    viewModel = viewModel
                )
            }
        }
    }

    // Mandatory Weekly Reset & Excel Download Dialog
    if (showWeeklyResetDialog) {
        AlertDialog(
            onDismissRequest = { /* Zorunlu / dismiss edilemez */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = WarmGold, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Haftalık Kasa Kapatma", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ForestGreen)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Bu haftanın kasasını sıfırlayıp yeni haftaya başlamak üzeresiniz.",
                        fontWeight = FontWeight.SemiBold,
                        color = ForestGreen
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "⚠️ Veri kaybını önlemek ve muhasebe kayıtlarınızı korumak için, siparişlerin tüm detaylarını (gün, saat, masa, müşteri, adet, tutar) içeren resmi Excel raporunu indirmeniz ZORUNLUDUR.",
                        fontSize = 12.sp,
                        color = Color(0xFFB45309),
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
                            Text("Kapatılacak Ciro: ₺${"%.2f".format(totalRevenue)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                            Text("Tamamlanan Adisyon: ${completedOrders.size} Adet", fontSize = 12.sp, color = SageGreen)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step 1: Download Excel Button
                    Button(
                        onClick = {
                            val file = com.example.sadec.util.ExcelReportGenerator.generateAndShareExcelReport(
                                context = context,
                                orders = activeWeeklyOrders,
                                restaurantName = restaurant?.name ?: "Sade.C Kahve Gerze",
                                weekPeriod = currentWeekPeriod,
                                onSuccess = {
                                    hasDownloadedPdf = true
                                }
                            )
                            if (file != null) hasDownloadedPdf = true
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

                    // Step 2: Reset Week Button (Disabled until Step 1 complete)
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
}

// -------------------------------------------------------------
// TAB 1: SALES & PRODUCT ANALYTICS
// -------------------------------------------------------------
@Composable
fun SalesAnalyticsTab(
    totalRevenue: Double,
    orderCount: Int,
    totalItemsSold: Int,
    productStats: List<ProductSaleStat>,
    onExportExcel: () -> Unit,
    onExportPdf: () -> Unit,
    onTriggerWeeklyReset: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // KPI Summary Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ForestGreen)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("TOPLAM CİRO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("₺${"%.2f".format(totalRevenue)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WarmGold)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("ADİSYON", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$orderCount Adet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("SATILAN ÜRÜN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$totalItemsSold Adet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SageGreen)
                    }
                }
            }
        }

        // Report & Weekly Close Action Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SoftMintGreen),
                border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("📊 Resmi Kasa & Satış Raporu", fontWeight = FontWeight.Bold, color = ForestGreen, fontSize = 14.sp)
                            Text("Tüm sipariş detaylarını (gün, saat, müşteri, masa, adet, tutar) içeren rapor dökümü alın.", fontSize = 11.sp, color = SageGreen)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onExportExcel,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = WarmGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel İndir", color = WarmGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onExportPdf,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF İndir", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onTriggerWeeklyReset,
                            modifier = Modifier.weight(1.2f).height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WarmGold),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Haftayı Kapat 📅", color = ForestGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section Title: En Çok Satan Ürünler
        item {
            Text(
                text = "ÜRÜN BAZLI SATIŞ ADETLERİ & CİRO",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Slate500
            )
        }

        if (productStats.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Bu dönemde henüz tamamlanmış ürün satışı bulunmuyor.", color = Slate500, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(productStats) { stat ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(ForestGreen, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${stat.totalQuantity}",
                                    color = WarmGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(stat.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ForestGreen)
                                Text("${stat.totalQuantity} porsiyon satıldı", fontSize = 11.sp, color = Slate500)
                            }
                        }

                        Text(
                            text = "₺${"%.2f".format(stat.totalRevenue)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFFB45309)
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
    tableStats: List<TableSaleStat>,
    totalRevenue: Double
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "MASA BAZLI TOPLAM GELİR DAĞILIMI",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Slate500
            )
        }

        if (tableStats.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Henüz masa satış verisi bulunmuyor.", color = Slate500, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(tableStats) { stat ->
                val percentage = if (totalRevenue > 0) ((stat.totalRevenue / totalRevenue) * 100).toInt() else 0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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

                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress Bar representing percentage of total
                        LinearProgressIndicator(
                            progress = { if (totalRevenue > 0) (stat.totalRevenue / totalRevenue).toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = ForestGreen,
                            trackColor = ForestGreen.copy(alpha = 0.1f)
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Toplam cironun %$percentage'si bu masadan elde edildi.",
                            fontSize = 10.sp,
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "TAMAMLANAN TÜM ADİSYONLAR (${completedOrders.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Slate500
            )
        }

        if (completedOrders.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Tamamlanmış satış kaydı bulunmuyor.", color = Slate500, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(completedOrders) { order ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                text = "₺${"%.2f".format(order.totalPrice)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = WarmGold
                            )
                        }

                        val dateStr = order.createdAt?.let { sdfDate.format(it) } ?: ""
                        Text(text = "🕒 $dateStr", fontSize = 11.sp, color = Slate500, modifier = Modifier.padding(top = 2.dp))

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = ForestGreen.copy(alpha = 0.08f))

                        // Items list
                        order.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${item.quantity}x ${item.name}",
                                    fontSize = 12.sp,
                                    color = ForestGreen
                                )
                                Text(
                                    text = "₺${"%.2f".format(item.unitPrice * item.quantity)}",
                                    fontSize = 12.sp,
                                    color = Slate500
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 4: MANUAL ORDER ENTRY (KASA SATIŞI)
// -------------------------------------------------------------
@Composable
fun ManualOrderEntryTab(
    menuItems: List<MenuItem>,
    tables: List<com.example.sadec.data.model.TableItem>,
    viewModel: MainViewModel
) {
    var selectedTableId by remember { mutableStateOf(tables.firstOrNull()?.id ?: "") }
    var selectedTableLabel by remember { mutableStateOf(tables.firstOrNull()?.label ?: "KASA") }
    var customerNameInput by remember { mutableStateOf("") }
    val cart = remember { mutableStateListOf<OrderItem>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
            Text("MENÜDEN ÜRÜN SEÇİN", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Slate500)
        }

        items(menuItems) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                cart.add(OrderItem(menuItemId = item.id, name = item.name, quantity = 1, unitPrice = item.price, isPaid = true))
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
                    shape = RoundedCornerShape(16.dp),
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
                                // Save as completed paid order
                                val newOrder = Order(
                                    tableId = selectedTableId.ifBlank { "table-kasa" },
                                    tableLabel = selectedTableLabel.ifBlank { "KASA" },
                                    customerName = customerNameInput.ifBlank { "Elden Müşteri" },
                                    status = "delivered",
                                    items = cart.map { it.copy(isPaid = true, paidAt = System.currentTimeMillis()) },
                                    totalPrice = totalCart
                                )
                                viewModel.createManualOrder(newOrder)
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
