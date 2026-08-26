package com.example.sadec.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sadec.data.model.Order
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

    // Active unarchived orders with unpaid items or pending status
    val activeOrders = remember(orders) {
        orders.filter { !it.isArchived && it.status != "cancelled" && (!it.isFullyPaid() || it.status == "pending" || it.status == "preparing" || it.status == "ready") }
    }

    // ONLY tables that have active products/orders. Empty tables do NOT show up here!
    val activeTables = remember(tables, activeOrders) {
        tables.filter { table -> activeOrders.any { ord -> ord.tableId == table.id } }
    }

    val totalRemainingAmount = remember(activeOrders) {
        activeOrders.sumOf { it.remainingAmount() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = {
                    Column {
                        Text("Açık Masalar", fontWeight = FontWeight.Bold, color = Color.White)
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
                        onPayItem = { orderId, itemIdx ->
                            viewModel.payOrderItem(orderId, itemIdx)
                        },
                        onPayFullTable = {
                            tableOrders.forEach { ord ->
                                if (!ord.isFullyPaid()) viewModel.payFullOrder(ord.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveTableOrderCard(
    table: TableItem,
    orders: List<Order>,
    tableRemaining: Double,
    onPayItem: (orderId: String, itemIndex: Int) -> Unit,
    onPayFullTable: () -> Unit
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
            // Table Header Row
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
                    Surface(
                        color = SoftMintGreen,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "🟢 DOLU",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
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
                    Text(
                        text = "👤 ${order.customerName.ifBlank { "Misafir" }}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ForestGreen
                    )
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

                // Item-by-item listing with individual payment buttons
                order.items.forEachIndexed { itemIdx, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.quantity}x ${item.name}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (item.isPaid) Slate500 else ForestGreen
                            )
                            Text(
                                text = "₺${"%.2f".format(item.unitPrice * item.quantity)}",
                                fontSize = 12.sp,
                                color = if (item.isPaid) Slate500 else WarmGold,
                                fontWeight = FontWeight.Bold
                            )
                            if (item.note.isNotBlank()) {
                                Text(text = "↳ Not: ${item.note}", fontSize = 11.sp, color = Color(0xFFD97706))
                            }
                        }

                        if (item.isPaid) {
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
                        } else {
                            Button(
                                onClick = { onPayItem(order.id, itemIdx) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Öde 💳", color = WarmGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Full Table Payment Button
            Button(
                onClick = onPayFullTable,
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
