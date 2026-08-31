package com.example.sadec.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sadec.data.model.MenuItem
import com.example.sadec.data.model.Order
import com.example.sadec.data.model.OrderItem
import com.example.sadec.data.model.TableItem
import com.example.sadec.ui.theme.*
import com.example.sadec.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveTablesScreen(
    viewModel: MainViewModel
) {
    val tables by viewModel.tables.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()

    // Active unarchived orders with unpaid items (kalan hesabı 0'dan büyük olan aktif siparişler)
    val activeOrders = remember(orders) {
        orders.filter { !it.isArchived && it.status != "cancelled" && !it.isFullyPaid() && it.remainingAmount() > 0.001 }
    }

    // ONLY tables that have active unpaid orders with a positive remaining amount
    val activeTables = remember(tables, activeOrders) {
        tables.filter { table ->
            val tableOrders = activeOrders.filter { ord -> ord.tableId == table.id }
            tableOrders.isNotEmpty() && tableOrders.sumOf { it.remainingAmount() } > 0.001
        }
    }

    val totalRemainingAmount = remember(activeOrders) {
        activeOrders.sumOf { it.remainingAmount() }
    }

    // State for Transfer Modal
    var tableToTransfer by remember { mutableStateOf<TableItem?>(null) }
    // State for Payment Method Modal (item or full table)
    var paymentTarget by remember { mutableStateOf<PaymentTarget?>(null) }
    // State for Discount / Complimentary Modal
    var discountTarget by remember { mutableStateOf<DiscountTarget?>(null) }
    // State for Item Edit Modal (ürün ve fiyat düzenleme)
    var itemToEdit by remember { mutableStateOf<ItemEditTarget?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = {
                    Column {
                        Text("Masalar & Adisyonlar", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text = if (activeTables.isNotEmpty()) "${activeTables.size} Masada Açık Hesap • Kalan: ₺${"%.2f".format(totalRemainingAmount)}" else "Şu an açık masa yok",
                            fontSize = 12.sp,
                            color = WarmGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ForestGreen,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    if (activeTables.isNotEmpty()) {
                        Surface(
                            color = ForestGreen,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text(
                                text = "🟢 ${activeTables.size} Aktif",
                                color = WarmGold,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (activeTables.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(SoftMintGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ForestGreen,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Şu an açık masa bulunmuyor ✨",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreen
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Müşteriler QR kod okutup masaya sipariş verdiğinde masa burada anında belirecektir. Hesabı tamamen kapatılan masalar bu sayfadan otomatik olarak kalkar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "AÇIK HESAPLI MASALAR (${activeTables.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Slate500,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                items(activeTables, key = { it.id }) { table ->
                    val tableOrders = activeOrders.filter { it.tableId == table.id }
                    val tableRemaining = tableOrders.sumOf { it.remainingAmount() }

                    ActiveTableOrderCard(
                        table = table,
                        orders = tableOrders,
                        tableRemaining = tableRemaining,
                        onOpenTransfer = { tableToTransfer = table },
                        onPayItemClick = { orderId, itemIdx, item ->
                            paymentTarget = PaymentTarget.Item(orderId, itemIdx, item.name, item.effectivePrice())
                        },
                        onPayFullTableClick = {
                            paymentTarget = PaymentTarget.FullTable(table.id, table.label, tableRemaining)
                        },
                        onDiscountClick = { orderId, itemIdx, item ->
                            discountTarget = DiscountTarget(orderId, itemIdx, item.name, item.unitPrice * item.quantity, item.isComplimentary, item.discountAmount)
                        },
                        onEditItemClick = { orderId, itemIdx, item ->
                            itemToEdit = ItemEditTarget(orderId, itemIdx, item)
                        }
                    )
                }
            }
        }
    }

    // 🔄 MASA & KİŞİ TAŞIMA DİYALOĞU
    tableToTransfer?.let { table ->
        val tableOrders = activeOrders.filter { it.tableId == table.id }
        TransferTableOrPersonDialog(
            currentTable = table,
            tableOrders = tableOrders,
            allTables = tables,
            onDismiss = { tableToTransfer = null },
            onTransferEntireTable = { toTable ->
                viewModel.transferEntireTable(table.id, toTable.id, toTable.label) {
                    tableToTransfer = null
                }
            },
            onTransferSingleOrder = { orderId, toTable ->
                viewModel.transferSingleOrder(orderId, toTable.id, toTable.label) {
                    tableToTransfer = null
                }
            }
        )
    }

    // 💳 ÖDEME YÖNTEMİ SEÇME DİYALOĞU (Nakit, Kart, Havale, İkram)
    paymentTarget?.let { target ->
        PaymentMethodDialog(
            target = target,
            onDismiss = { paymentTarget = null },
            onConfirmPayment = { method ->
                when (target) {
                    is PaymentTarget.Item -> {
                        viewModel.payOrderItem(target.orderId, target.itemIndex, method)
                    }
                    is PaymentTarget.FullTable -> {
                        viewModel.payEntireTable(target.tableId, method)
                    }
                }
                paymentTarget = null
            }
        )
    }

    // 🎁 İKRAM & İNDİRİM UYGULAMA DİYALOĞU
    discountTarget?.let { target ->
        DiscountOrComplimentaryDialog(
            target = target,
            onDismiss = { discountTarget = null },
            onApply = { isComplimentary, discountAmount ->
                viewModel.setItemDiscountOrComplimentary(target.orderId, target.itemIndex, isComplimentary, discountAmount)
                discountTarget = null
            }
        )
    }

    // ✏️ ÜRÜN & FİYAT DÜZENLEME DİYALOĞU
    itemToEdit?.let { target ->
        EditOrderItemDialog(
            item = target.item,
            menuItems = menuItems,
            onDismiss = { itemToEdit = null },
            onSave = { updatedItem ->
                viewModel.updateOrderItem(target.orderId, target.itemIndex, updatedItem)
                itemToEdit = null
            },
            onDelete = {
                viewModel.removeOrderItem(target.orderId, target.itemIndex)
                itemToEdit = null
            }
        )
    }
}

// Sealed class for payment targets
sealed class PaymentTarget {
    data class Item(val orderId: String, val itemIndex: Int, val itemName: String, val amount: Double) : PaymentTarget()
    data class FullTable(val tableId: String, val tableLabel: String, val amount: Double) : PaymentTarget()
}

data class DiscountTarget(
    val orderId: String,
    val itemIndex: Int,
    val itemName: String,
    val originalTotal: Double,
    val isCurrentlyComplimentary: Boolean,
    val currentDiscount: Double
)

data class ItemEditTarget(
    val orderId: String,
    val itemIndex: Int,
    val item: OrderItem
)

@Composable
fun ActiveTableOrderCard(
    table: TableItem,
    orders: List<Order>,
    tableRemaining: Double,
    onOpenTransfer: () -> Unit,
    onPayItemClick: (orderId: String, itemIndex: Int, item: OrderItem) -> Unit,
    onPayFullTableClick: () -> Unit,
    onDiscountClick: (orderId: String, itemIndex: Int, item: OrderItem) -> Unit,
    onEditItemClick: (orderId: String, itemIndex: Int, item: OrderItem) -> Unit
) {
    val sdfTime = SimpleDateFormat("HH:mm", Locale("tr"))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.5.dp, ForestGreen.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Table Header Row with Transfer Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = ForestGreen,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "📍 ${table.label}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    // 🔄 Taşı Butonu
                    OutlinedButton(
                        onClick = onOpenTransfer,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestGreen),
                        border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Taşı 🔄", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Kalan Hesap", fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "₺${"%.2f".format(tableRemaining)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFFB45309)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = ForestGreen.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(10.dp))

            // Orders inside this Table
            orders.forEachIndexed { orderIdx, order ->
                if (orderIdx > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Slate100)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "👤 ${order.customerName.ifBlank { "Misafir" }}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ForestGreen
                        )
                    }
                    val timeStr = order.createdAt?.let { sdfTime.format(it) } ?: ""
                    Text(text = "🕒 $timeStr", fontSize = 11.sp, color = Slate500)
                }

                if (order.note.isNotBlank()) {
                    Surface(
                        color = SoftMintGreen,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
                    ) {
                        Text(
                            text = "Not: ${order.note}",
                            fontSize = 11.sp,
                            color = ForestGreen,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Item-by-item listing with payment and discount options
                order.items.forEachIndexed { itemIdx, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (!item.isPaid) {
                                        Modifier
                                            .clickable { onEditItemClick(order.id, itemIdx, item) }
                                            .padding(vertical = 2.dp, horizontal = 2.dp)
                                    } else {
                                        Modifier.padding(vertical = 2.dp, horizontal = 2.dp)
                                    }
                                )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${item.quantity}x ${item.name}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = if (item.isPaid) Slate500 else ForestGreen
                                )
                                if (!item.isPaid) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Ürünü / Fiyatı Düzenle",
                                        tint = ForestGreen.copy(alpha = 0.6f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                if (item.isComplimentary) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0xFFFEF3C7),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("İKRAM 🎁", color = Color(0xFFB45309), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                } else if (item.discountAmount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0xFFDCFCE7),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("-₺${"%.2f".format(item.discountAmount)} 🏷️", color = Color(0xFF166534), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (item.isComplimentary) {
                                    Text(
                                        text = "₺${"%.2f".format(item.unitPrice * item.quantity)}",
                                        fontSize = 11.sp,
                                        color = Slate500,
                                        textDecoration = TextDecoration.LineThrough
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "₺0,00", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                } else {
                                    Text(
                                        text = "₺${"%.2f".format(item.effectivePrice())}",
                                        fontSize = 12.sp,
                                        color = if (item.isPaid) Slate500 else WarmGold,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (item.isPaid && item.paymentMethod.isNotBlank()) {
                                    val methodLabel = when (item.paymentMethod) {
                                        "cash" -> " (Nakit 💵)"
                                        "card" -> " (Kart 💳)"
                                        "transfer" -> " (Havale 📲)"
                                        "complimentary" -> " (İkram 🎁)"
                                        else -> ""
                                    }
                                    Text(text = methodLabel, fontSize = 10.sp, color = Slate500)
                                }
                            }

                            if (item.note.isNotBlank()) {
                                Text(text = "↳ Not: ${item.note}", fontSize = 11.sp, color = Color(0xFFD97706))
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!item.isPaid) {
                                // İkram / İndirim Butonu
                                IconButton(
                                    onClick = { onDiscountClick(order.id, itemIdx, item) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Text("🎁", fontSize = 14.sp)
                                }

                                // Öde Butonu
                                Button(
                                    onClick = { onPayItemClick(order.id, itemIdx, item) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Öde 💳", color = WarmGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Surface(
                                    color = SoftMintGreen,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Ödendi ✅",
                                        color = ForestGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Full Table Payment Button
            Button(
                onClick = onPayFullTableClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WarmGold, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Masanın Kalanını Kapat (₺${"%.2f".format(tableRemaining)}) ✨",
                    color = WarmGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// 🔄 MASA VEYA KİŞİ BAZLI TRANSFER DİYALOĞU
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferTableOrPersonDialog(
    currentTable: TableItem,
    tableOrders: List<Order>,
    allTables: List<TableItem>,
    onDismiss: () -> Unit,
    onTransferEntireTable: (toTable: TableItem) -> Unit,
    onTransferSingleOrder: (orderId: String, toTable: TableItem) -> Unit
) {
    var transferMode by remember { mutableStateOf("table") } // "table" or "person"
    var selectedTargetTable by remember { mutableStateOf(allTables.firstOrNull { it.id != currentTable.id }) }
    var selectedOrderId by remember { mutableStateOf(tableOrders.firstOrNull()?.id ?: "") }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val otherTables = remember(allTables, currentTable) {
        allTables.filter { it.id != currentTable.id }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Masa / Kişi Taşıma (Transfer) 🔄",
                fontWeight = FontWeight.Bold,
                color = ForestGreen,
                fontSize = 17.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Mevcut: ${currentTable.label}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate500
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Mode Selector: Tüm Masa vs Tek Kişi
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = transferMode == "table",
                        onClick = { transferMode = "table" },
                        label = { Text("Tüm Masayı Taşı", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ForestGreen,
                            selectedLabelColor = WarmGold
                        )
                    )
                    FilterChip(
                        selected = transferMode == "person",
                        onClick = { transferMode = "person" },
                        label = { Text("Tek Kişiyi Taşı", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ForestGreen,
                            selectedLabelColor = WarmGold
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (transferMode == "person") {
                    Text("Taşınacak Kişiyi / Adisyonu Seçin:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                    Spacer(modifier = Modifier.height(4.dp))
                    tableOrders.forEach { ord ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedOrderId = ord.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedOrderId == ord.id,
                                onClick = { selectedOrderId = ord.id },
                                colors = RadioButtonDefaults.colors(selectedColor = ForestGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "👤 ${ord.customerName.ifBlank { "Misafir" }} (₺${"%.2f".format(ord.remainingAmount())})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text("Hedef Masayı Seçin:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                Spacer(modifier = Modifier.height(4.dp))

                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { isDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedTargetTable?.label ?: "Masa Seçin",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        otherTables.forEach { tbl ->
                            DropdownMenuItem(
                                text = { Text(tbl.label) },
                                onClick = {
                                    selectedTargetTable = tbl
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = selectedTargetTable
                    if (target != null) {
                        if (transferMode == "table") {
                            onTransferEntireTable(target)
                        } else {
                            onTransferSingleOrder(selectedOrderId, target)
                        }
                    }
                },
                enabled = selectedTargetTable != null,
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
            ) {
                Text("Aktar & Taşı 🚀", color = WarmGold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = Slate500)
            }
        }
    )
}

// 💳 ÖDEME YÖNTEMİ DİYALOĞU (Nakit, Kredi Kartı, Havale, İkram)
@Composable
fun PaymentMethodDialog(
    target: PaymentTarget,
    onDismiss: () -> Unit,
    onConfirmPayment: (paymentMethod: String) -> Unit
) {
    var selectedMethod by remember { mutableStateOf("card") } // "card", "cash", "transfer", "complimentary"

    val title = when (target) {
        is PaymentTarget.Item -> "Ürün Ödemesi: ${target.itemName}"
        is PaymentTarget.FullTable -> "${target.tableLabel} Toplam Hesap"
    }

    val amount = when (target) {
        is PaymentTarget.Item -> target.amount
        is PaymentTarget.FullTable -> target.amount
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Ödeme Yöntemi Seçin 💳", fontWeight = FontWeight.Bold, color = ForestGreen, fontSize = 17.sp)
                Text(text = title, fontSize = 12.sp, color = Slate500)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = SoftMintGreen,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tahsil Edilecek Tutar:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ForestGreen)
                        Text("₺${"%.2f".format(amount)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                    }
                }

                // 4 Payment Options
                val methods = listOf(
                    Triple("card", "Kredi Kartı / Banka Kartı", "💳"),
                    Triple("cash", "Nakit Tahsilat", "💵"),
                    Triple("transfer", "Havale / EFT / FAST", "📲"),
                    Triple("complimentary", "İkram (Ücretsiz 0₺)", "🎁")
                )

                methods.forEach { (id, label, icon) ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedMethod = id },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedMethod == id) ForestGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, if (selectedMethod == id) ForestGreen else Slate100)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(icon, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(label, fontWeight = if (selectedMethod == id) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp, color = ForestGreen)
                            Spacer(modifier = Modifier.weight(1f))
                            if (selectedMethod == id) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmPayment(selectedMethod) },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
            ) {
                Text("Ödemeyi Onayla & Kapat ✨", color = WarmGold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç", color = Slate500)
            }
        }
    )
}

// 🎁 İKRAM & İNDİRİM DİYALOĞU
@Composable
fun DiscountOrComplimentaryDialog(
    target: DiscountTarget,
    onDismiss: () -> Unit,
    onApply: (isComplimentary: Boolean, discountAmount: Double) -> Unit
) {
    var isComplimentary by remember { mutableStateOf(target.isCurrentlyComplimentary) }
    var discountInput by remember { mutableStateOf(if (target.currentDiscount > 0) "%.2f".format(target.currentDiscount) else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("İkram & İndirim Uygula 🎁", fontWeight = FontWeight.Bold, color = ForestGreen, fontSize = 17.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Ürün: ${target.itemName}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ForestGreen)
                Text("Liste Fiyatı: ₺${"%.2f".format(target.originalTotal)}", fontSize = 12.sp, color = Slate500)
                Spacer(modifier = Modifier.height(14.dp))

                // İkram Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("İkram Olarak Belirle 🎁", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ForestGreen)
                        Text("Ürün tutarı 0₺ yapılır.", fontSize = 11.sp, color = Slate500)
                    }
                    Switch(
                        checked = isComplimentary,
                        onCheckedChange = {
                            isComplimentary = it
                            if (it) discountInput = ""
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = ForestGreen, checkedTrackColor = WarmGold)
                    )
                }

                if (!isComplimentary) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Veya İndirim Tutarı Girin (TL):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = discountInput,
                        onValueChange = { discountInput = it },
                        placeholder = { Text("Örn: 20,00") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    // Quick percentage discount buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(10, 15, 20, 50).forEach { pct ->
                            val calcDisc = target.originalTotal * (pct / 100.0)
                            OutlinedButton(
                                onClick = { discountInput = "%.2f".format(calcDisc) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("%$pct", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val disc = discountInput.replace(",", ".").toDoubleOrNull() ?: 0.0
                    onApply(isComplimentary, disc)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
            ) {
                Text("Uygula ✨", color = WarmGold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = Slate500)
            }
        }
    )
}

// ✏️ ÜRÜN & FİYAT DÜZENLEME DİYALOĞU
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditOrderItemDialog(
    item: OrderItem,
    menuItems: List<MenuItem>,
    onDismiss: () -> Unit,
    onSave: (updatedItem: OrderItem) -> Unit,
    onDelete: () -> Unit
) {
    var selectedMenuItemId by remember { mutableStateOf(item.menuItemId) }
    var itemName by remember { mutableStateOf(item.name) }
    var unitPriceText by remember {
        mutableStateOf(
            if (item.unitPrice % 1.0 == 0.0) item.unitPrice.toInt().toString() else item.unitPrice.toString()
        )
    }
    var quantity by remember { mutableStateOf(item.quantity) }
    var note by remember { mutableStateOf(item.note) }
    var isMenuDropdownExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredMenu = remember(menuItems, searchQuery) {
        if (searchQuery.isBlank()) menuItems
        else menuItems.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(SoftMintGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✏️", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Ürünü Düzenle & Değiştir", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ForestGreen)
                    Text("Ürünü değiştirebilir veya fiyatı güncelleyebilirsiniz.", fontSize = 11.5.sp, color = Slate500)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. ÜRÜN SEÇİMİ / DEĞİŞTİRME (DROPDOWN + ARAMA)
                Text("Ürün (Menüden Değiştirin):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreen)

                ExposedDropdownMenuBox(
                    expanded = isMenuDropdownExpanded,
                    onExpandedChange = { isMenuDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isMenuDropdownExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestGreen,
                            unfocusedBorderColor = ForestGreen.copy(alpha = 0.3f),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = isMenuDropdownExpanded,
                        onDismissRequest = { isMenuDropdownExpanded = false }
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Ürün ara...", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        )
                        HorizontalDivider()
                        filteredMenu.forEach { mItem ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(mItem.name, fontWeight = FontWeight.Medium)
                                        Text("₺${"%.2f".format(mItem.price)}", fontWeight = FontWeight.Bold, color = WarmGold)
                                    }
                                },
                                onClick = {
                                    selectedMenuItemId = mItem.id
                                    itemName = mItem.name
                                    // Ürün değiştiğinde FİYAT OTOMATİK OLARAK GÜNCELLENİR!
                                    unitPriceText = if (mItem.price % 1.0 == 0.0) mItem.price.toInt().toString() else mItem.price.toString()
                                    isMenuDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // 2. FİYAT DÜZENLEME (ÖZEL FİYAT / MANUEL FİYAT GİRİŞİ)
                Text("Birim Fiyat (₺):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                OutlinedTextField(
                    value = unitPriceText,
                    onValueChange = { input ->
                        val clean = input.replace(',', '.')
                        if (clean.isEmpty() || clean.toDoubleOrNull() != null || clean.count { it == '.' } <= 1) {
                            unitPriceText = clean
                        }
                    },
                    label = { Text("Birim Fiyat") },
                    prefix = { Text("₺ ", fontWeight = FontWeight.Bold, color = WarmGold) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = ForestGreen.copy(alpha = 0.3f),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 3. ADET / MİKTAR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Adet / Miktar:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (quantity > 1) quantity-- },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                        ) {
                            Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                        }
                        Text(
                            text = "$quantity",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = ForestGreen,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                        IconButton(
                            onClick = { quantity++ },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                        ) {
                            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                        }
                    }
                }

                // 4. SİPARİŞ NOTU
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Ürün Notu") },
                    placeholder = { Text("Örn: Az şekerli, ekstra sıcak vb.") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = ForestGreen.copy(alpha = 0.3f),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 5. YENİ TOPLAM TUTAR ÖZETİ
                val parsedPrice = unitPriceText.toDoubleOrNull() ?: item.unitPrice
                val subtotal = parsedPrice * quantity
                Surface(
                    color = SoftMintGreen,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Yeni Kalem Tutarı:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ForestGreen)
                        Text("₺${"%.2f".format(subtotal)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WarmGold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalPrice = unitPriceText.toDoubleOrNull() ?: item.unitPrice
                    val updated = item.copy(
                        menuItemId = selectedMenuItemId,
                        name = itemName.trim().ifBlank { item.name },
                        unitPrice = finalPrice,
                        quantity = quantity,
                        note = note.trim()
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Kaydet ✨", color = WarmGold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
                ) {
                    Text("🗑️ Sil", fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = Slate500)
                ) {
                    Text("Vazgeç")
                }
            }
        }
    )
}
