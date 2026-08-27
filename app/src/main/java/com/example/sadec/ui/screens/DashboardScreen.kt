package com.example.sadec.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
                    cardTotal = cardTotal,
                    cashTotal = cashTotal,
                    transferTotal = transferTotal,
                    complimentaryTotal = complimentaryTotal,
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
                    onShareDailyZReport = {
                        shareDailyZReport(
                            context = context,
                            restaurantName = restaurant?.name ?: "Sade.C Kahve Gerze",
                            todayOrders = completedOrders,
                            productStats = productStats
                        )
                    },
                    onTriggerWeeklyReset = {
                        showWeeklyResetDialog = true
                        hasDownloadedPdf = false
                    }
                )
                1 -> TableAnalyticsTab(
                    totalRevenue = totalRevenue,
                    tableStats = tableStats
                )
                2 -> SalesHistoryTab(
                    completedOrders = completedOrders
                )
                3 -> ManualCashEntryTab(
                    menuItems = menuItems,
                    tables = tables,
                    onSaveManualOrder = { order ->
                        viewModel.createManualOrder(order)
                    }
                )
            }
        }

        // 🛡️ HAFTALIK KASA KAPATMA VE RAPOR İNDİRME ZORUNLU DİYALOĞU
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

                        Surface(
                            color = SoftMintGreen,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Bu Haftanın Kasa Özeti:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ForestGreen)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("• Net Ciro: ₺${"%.2f".format(totalRevenue)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WarmGold)
                                Text("• Kredi Kartı: ₺${"%.2f".format(cardTotal)}", fontSize = 12.sp, color = ForestGreen)
                                Text("• Nakit: ₺${"%.2f".format(cashTotal)}", fontSize = 12.sp, color = ForestGreen)
                                Text("• Toplam Adisyon: ${completedOrders.size} Adet", fontSize = 12.sp, color = SageGreen)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Step 1: Download Excel Button
                        Button(
                            onClick = {
                                com.example.sadec.util.ExcelReportGenerator.generateAndShareExcelReport(
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
}

// -------------------------------------------------------------
// TAB 1: SALES & PRODUCT ANALYTICS
// -------------------------------------------------------------
@Composable
fun SalesAnalyticsTab(
    totalRevenue: Double,
    orderCount: Int,
    totalItemsSold: Int,
    cardTotal: Double,
    cashTotal: Double,
    transferTotal: Double,
    complimentaryTotal: Double,
    productStats: List<ProductSaleStat>,
    onExportExcel: () -> Unit,
    onExportPdf: () -> Unit,
    onShareDailyZReport: () -> Unit,
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

        // 💳 ÖDEME YÖNTEMLERİ DAĞILIMI (KASA AYRIMI)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("ÖDEME YÖNTEMLERİ DAĞILIMI (KASA)", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = ForestGreen)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Kredi Kartı
                        Surface(
                            color = SoftMintGreen,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("💳", fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Kredi Kartı", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ForestGreen)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("₺${"%.2f".format(cardTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                            }
                        }

                        // Nakit
                        Surface(
                            color = Color(0xFFF0FDF4),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("💵", fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Nakit Kasa", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF166534))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("₺${"%.2f".format(cashTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Havale / EFT
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📲", fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Havale/EFT", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E40AF))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("₺${"%.2f".format(transferTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                            }
                        }

                        // İkram / İndirim
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🎁", fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("İkram/İndirim", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF92400E))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("₺${"%.2f".format(complimentaryTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                            }
                        }
                    }
                }
            }
        }

        // Report & Quick Action Buttons Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SoftMintGreen),
                border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📊 Resmi Raporlama & Z-Raporu", fontWeight = FontWeight.Bold, color = ForestGreen, fontSize = 14.sp)
                    Text("Tüm satış dökümlerini Excel/PDF indirin veya WhatsApp'tan tek tıkla Gün Sonu Z-Raporu gönderin.", fontSize = 11.sp, color = SageGreen)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Row 1: Excel & PDF
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onExportExcel,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
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
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF İndir", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 2: Z-Raporu Paylaş & Haftayı Kapat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onShareDailyZReport,
                            modifier = Modifier.weight(1.2f).height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text("🌙 Z-Raporu Paylaş 📤", color = WarmGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onTriggerWeeklyReset,
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WarmGold),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
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
// TAB 2: TABLE ANALYTICS
// -------------------------------------------------------------
@Composable
fun TableAnalyticsTab(
    totalRevenue: Double,
    tableStats: List<TableSaleStat>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "MASALARA GÖRE CİRO VE YOĞUNLUK",
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
                        Text("Masa bazlı satış kaydı bulunmuyor.", color = Slate500, fontSize = 13.sp)
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
    var selectedTableId by remember { mutableStateOf("table-kasa") }
    var selectedTableLabel by remember { mutableStateOf("KASA") }
    var selectedPaymentMethod by remember { mutableStateOf("cash") } // "cash", "card", "transfer", "complimentary"
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
                        ).forEach { (id, label, method) ->
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

// 🌙 GÜN SONU HIZLI Z-RAPORU PAYLAŞMA
fun shareDailyZReport(
    context: Context,
    restaurantName: String,
    todayOrders: List<Order>,
    productStats: List<ProductSaleStat>
) {
    val sdfDate = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
    val sdfTime = SimpleDateFormat("HH:mm", Locale("tr", "TR"))
    val dateStr = sdfDate.format(Date())
    val timeStr = sdfTime.format(Date())

    val totalNet = todayOrders.sumOf { it.paidAmount().let { p -> if (p > 0) p else it.totalPrice } }
    val cardTotal = todayOrders.sumOf { it.cardPaidAmount() }
    val cashTotal = todayOrders.sumOf { it.cashPaidAmount() }
    val transferTotal = todayOrders.sumOf { it.transferPaidAmount() }
    val compTotal = todayOrders.sumOf { it.complimentaryAmount() }
    val totalItems = todayOrders.sumOf { it.items.sumOf { i -> i.quantity } }
    val topProducts = if (productStats.isNotEmpty()) {
        productStats.take(5).joinToString("\n") { "• ${it.name}: ${it.totalQuantity} adet (₺${"%.2f".format(it.totalRevenue)})" }
    } else {
        "• Satış kaydı bulunmuyor"
    }

    val reportText = """
☕ *${restaurantName.uppercase()} — GÜN SONU Z-RAPORU* 🌙
📅 Tarih: $dateStr • Saat: $timeStr
━━━━━━━━━━━━━━━━━━━━━━━━━━
💰 *NET GÜNLÜK CİRO:* ₺${"%.2f".format(totalNet)}

💳 *Kredi Kartı / POS:* ₺${"%.2f".format(cardTotal)}
💵 *Nakit Kasa:* ₺${"%.2f".format(cashTotal)}
📲 *Havale / EFT / FAST:* ₺${"%.2f".format(transferTotal)}
🎁 *İkram / İndirim:* ₺${"%.2f".format(compTotal)}
━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 *Toplam Adisyon:* ${todayOrders.size} Adet
☕ *Satılan Ürün:* $totalItems Adet

🔥 *Günün En Çok Satanları:*
$topProducts
━━━━━━━━━━━━━━━━━━━━━━━━━━
Hayırlı işler ve bereketli kazançlar dileriz! 🌿✨
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "$restaurantName Gün Sonu Raporu ($dateStr)")
        putExtra(Intent.EXTRA_TEXT, reportText)
    }
    context.startActivity(Intent.createChooser(intent, "Gün Sonu Z-Raporunu Paylaş (WhatsApp / SMS)"))
}
