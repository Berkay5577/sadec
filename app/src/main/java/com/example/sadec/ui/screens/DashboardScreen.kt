package com.example.sadec.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sadec.data.model.MenuItem
import com.example.sadec.data.model.Order
import com.example.sadec.data.model.OrderItem
import com.example.sadec.ui.theme.*
import com.example.sadec.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ProductSaleStat(
    val name: String,
    val totalQuantity: Int,
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

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("📊 Satış Raporu", "➕ Elle Satış Girişi", "📜 Geçmiş Satışlar")

    // Date Filter for Reports (0: Tüm Zamanlar, 1: Bugün, 2: Bu Hafta)
    var dateFilterIndex by remember { mutableStateOf(0) }

    // Filter orders based on date selection
    val calendar = Calendar.getInstance()
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    val weekStart = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -7)
    }.time

    val filteredOrders = remember(orders, dateFilterIndex) {
        orders.filter { order ->
            if (order.status == "cancelled") return@filter false
            val createdAt = order.createdAt ?: return@filter true
            when (dateFilterIndex) {
                1 -> createdAt.after(todayStart)
                2 -> createdAt.after(weekStart)
                else -> true
            }
        }
    }

    // Calculations for Report
    val totalTurnover = remember(filteredOrders) {
        filteredOrders.sumOf { it.totalPrice }
    }

    val totalItemCount = remember(filteredOrders) {
        filteredOrders.sumOf { order -> order.items.sumOf { it.quantity } }
    }

    // Product breakdown stats
    val productStats = remember(filteredOrders) {
        val map = mutableMapOf<String, Pair<Int, Double>>()
        filteredOrders.forEach { order ->
            order.items.forEach { item ->
                val current = map[item.name] ?: Pair(0, 0.0)
                map[item.name] = Pair(
                    current.first + item.quantity,
                    current.second + (item.unitPrice * item.quantity)
                )
            }
        }
        map.map { (name, pair) ->
            ProductSaleStat(name = name, totalQuantity = pair.first, totalRevenue = pair.second)
        }.sortedByDescending { it.totalQuantity }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = { Text("Satış Dashboard & Raporlar", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
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
            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = ForestGreen
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

            when (selectedTab) {
                0 -> SalesReportTab(
                    totalTurnover = totalTurnover,
                    totalOrderCount = filteredOrders.size,
                    totalItemCount = totalItemCount,
                    productStats = productStats,
                    dateFilterIndex = dateFilterIndex,
                    onDateFilterChange = { dateFilterIndex = it }
                )
                1 -> ManualSaleEntryTab(
                    menuItems = menuItems,
                    onConfirmSale = { items, tableLabel, customerName, note ->
                        viewModel.addManualSale(items, tableLabel, customerName, note)
                        selectedTab = 0 // Go to report tab
                    }
                )
                2 -> SalesHistoryTab(orders = orders)
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: SATIŞ RAPORLARI & ÜRÜN ADET BAZLI ANALİZ
// -------------------------------------------------------------
@Composable
fun SalesReportTab(
    totalTurnover: Double,
    totalOrderCount: Int,
    totalItemCount: Int,
    productStats: List<ProductSaleStat>,
    dateFilterIndex: Int,
    onDateFilterChange: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Date Filter Pills
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf("Tüm Zamanlar", "Bugün", "Son 7 Gün")
                filters.forEachIndexed { index, label ->
                    val selected = dateFilterIndex == index
                    FilterChip(
                        selected = selected,
                        onClick = { onDateFilterChange(index) },
                        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ForestGreen,
                            selectedLabelColor = WarmGold
                        )
                    )
                }
            }
        }

        // Summary Metric Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ForestGreen)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Toplam Ciro", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        Text(
                            text = "₺${"%.2f".format(totalTurnover)}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarmGold
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftMintGreen)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Satılan Ürün", fontSize = 12.sp, color = SageGreen)
                        Text(
                            text = "$totalItemCount Adet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreen
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Sipariş", fontSize = 12.sp, color = Slate500)
                        Text(
                            text = "$totalOrderCount",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreen
                        )
                    }
                }
            }
        }

        // Product Sales Breakdown Header
        item {
            Text(
                text = "📦 Ürün Bazlı Satış Miktarları & Gelir",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ForestGreen,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (productStats.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Bu zaman aralığında henüz satış kaydı bulunmuyor.", color = Slate500)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stat.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = ForestGreen
                            )
                            Text(
                                text = "Toplam Ciro: ₺${"%.2f".format(stat.totalRevenue)}",
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        }

                        // Quantity Badge (e.g. x14 Adet)
                        Surface(
                            color = SoftMintGreen,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "x${stat.totalQuantity} Adet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ForestGreen,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: ELLE SATIŞ GİRİŞİ (KASA / POS SATIŞI)
// -------------------------------------------------------------
@Composable
fun ManualSaleEntryTab(
    menuItems: List<MenuItem>,
    onConfirmSale: (items: List<OrderItem>, tableLabel: String, customerName: String, note: String) -> Unit
) {
    // Current selected items in manual cart
    val manualCart = remember { mutableStateMapOf<String, Int>() } // menuItemId -> quantity
    var customItemName by remember { mutableStateOf("") }
    var customItemPrice by remember { mutableStateOf("") }
    var customItemQty by remember { mutableStateOf("1") }
    val extraCustomItems = remember { mutableStateListOf<OrderItem>() }

    var selectedTable by remember { mutableStateOf("KASA / ELDEN") }
    var customerName by remember { mutableStateOf("") }
    var saleNote by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val tableOptions = listOf("KASA / ELDEN", "BAR", "İÇ 1", "İÇ 2", "DIŞ 1", "DIŞ 2", "DIŞ 3", "DIŞ 4", "Y1", "Y2")

    // Filter menu items by search
    val filteredMenuItems = remember(menuItems, searchQuery) {
        if (searchQuery.isBlank()) menuItems else menuItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // Calculate total price
    val standardTotal = manualCart.entries.sumOf { (itemId, qty) ->
        val item = menuItems.find { it.id == itemId }
        (item?.price ?: 0.0) * qty
    }
    val customTotal = extraCustomItems.sumOf { it.unitPrice * it.quantity }
    val grandTotal = standardTotal + customTotal

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Table & Customer Name Row
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SoftMintGreen)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Masa & Müşteri Seçimi", fontWeight = FontWeight.Bold, color = ForestGreen, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Table selector pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val commonTables = listOf("KASA / ELDEN", "BAR", "İÇ 1", "DIŞ 1")
                        commonTables.forEach { tbl ->
                            FilterChip(
                                selected = selectedTable == tbl,
                                onClick = { selectedTable = tbl },
                                label = { Text(tbl, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ForestGreen,
                                    selectedLabelColor = WarmGold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("Müşteri Adı (İsteğe bağlı)") },
                        placeholder = { Text("Örn: Ahmet Bey") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Search in Sade.C Menu
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Menüden Ürün Ara & Ekle") },
                placeholder = { Text("Espresso, San Sebastian, Poğaça...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ForestGreen) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Standard Menu Items with Quick Steppers
        items(filteredMenuItems.take(15)) { item ->
            val count = manualCart[item.id] ?: 0
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Bold, color = ForestGreen)
                        Text("₺${"%.2f".format(item.price)}", color = WarmGold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    // Stepper Controls (- / count / +)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (count > 0) {
                            IconButton(
                                onClick = {
                                    if (count == 1) manualCart.remove(item.id) else manualCart[item.id] = count - 1
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Azalt", tint = ForestGreen)
                            }

                            Text(
                                text = "$count",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                color = ForestGreen
                            )
                        }

                        IconButton(
                            onClick = { manualCart[item.id] = count + 1 },
                            modifier = Modifier
                                .size(34.dp)
                                .background(ForestGreen, CircleShape)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Ekle", tint = WarmGold, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        // Custom Item Entry Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("➕ Menü Dışı Özel Ürün Ekle", fontWeight = FontWeight.Bold, color = ForestGreen)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customItemName,
                            onValueChange = { customItemName = it },
                            label = { Text("Ürün Adı") },
                            modifier = Modifier.weight(2f),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = customItemPrice,
                            onValueChange = { customItemPrice = it },
                            label = { Text("Fiyat (₺)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val price = customItemPrice.toDoubleOrNull() ?: 0.0
                            val qty = customItemQty.toIntOrNull() ?: 1
                            if (customItemName.isNotBlank() && price > 0) {
                                extraCustomItems.add(
                                    OrderItem(name = customItemName.trim(), quantity = qty, unitPrice = price)
                                )
                                customItemName = ""
                                customItemPrice = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SageGreen)
                    ) {
                        Text("Listeye Ekle", color = Color.White)
                    }
                }
            }
        }

        // Display Custom Items Added
        if (extraCustomItems.isNotEmpty()) {
            items(extraCustomItems) { customItem ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftMintGreen)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${customItem.quantity}x ${customItem.name} (₺${"%.2f".format(customItem.unitPrice)})", fontWeight = FontWeight.Bold, color = ForestGreen)
                        IconButton(onClick = { extraCustomItems.remove(customItem) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Order Note
        item {
            OutlinedTextField(
                value = saleNote,
                onValueChange = { saleNote = it },
                label = { Text("Satış / Kasa Notu (İsteğe bağlı)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Grand Total & Submit Sale Button
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ForestGreen)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Toplam Tutar:", color = Color.White, fontSize = 16.sp)
                        Text(
                            text = "₺${"%.2f".format(grandTotal)}",
                            color = WarmGold,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val items = mutableListOf<OrderItem>()
                            manualCart.forEach { (itemId, qty) ->
                                val item = menuItems.find { it.id == itemId }
                                if (item != null && qty > 0) {
                                    items.add(OrderItem(menuItemId = item.id, name = item.name, quantity = qty, unitPrice = item.price))
                                }
                            }
                            items.addAll(extraCustomItems)

                            if (items.isNotEmpty()) {
                                onConfirmSale(items, selectedTable, customerName, saleNote)
                            }
                        },
                        enabled = grandTotal > 0,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WarmGold)
                    ) {
                        Text("Kasadan Satışı Tamamla 💸", color = ForestGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: GEÇMİŞ SATIŞLAR (SİPARİŞ ARŞİVİ)
// -------------------------------------------------------------
@Composable
fun SalesHistoryTab(orders: List<Order>) {
    val timeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val completedOrders = remember(orders) {
        orders.filter { it.status == "delivered" }
    }

    if (completedOrders.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(64.dp), tint = Slate500)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Henüz tamamlanmış geçmiş satış bulunmuyor.", color = Slate500, fontWeight = FontWeight.Medium)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(completedOrders) { order ->
                val formattedTime = order.createdAt?.let { timeFormat.format(it) } ?: ""

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                                        text = order.tableLabel.ifBlank { "Kasa" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = ForestGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                if (order.customerName.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("👤 ${order.customerName}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ForestGreen)
                                }
                            }

                            Text(
                                text = "₺${"%.2f".format(order.totalPrice)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = WarmGold
                            )
                        }

                        if (formattedTime.isNotBlank()) {
                            Text(text = formattedTime, fontSize = 11.sp, color = Slate500, modifier = Modifier.padding(top = 4.dp))
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                        // Order Items List
                        order.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${item.quantity}x ${item.name}",
                                    fontSize = 13.sp,
                                    color = ForestGreen
                                )
                                Text(
                                    text = "₺${"%.2f".format(item.unitPrice * item.quantity)}",
                                    fontSize = 13.sp,
                                    color = Slate500
                                )
                            }
                        }

                        if (order.note.isNotBlank()) {
                            Text(
                                text = "Not: ${order.note}",
                                fontSize = 11.sp,
                                color = SageGreen,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
