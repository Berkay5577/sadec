package com.example.sadec.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sadec.data.model.Order
import com.example.sadec.ui.theme.*
import com.example.sadec.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    viewModel: MainViewModel,
    onOrderClick: (Order) -> Unit
) {
    val orders by viewModel.orders.collectAsState()
    val filter by viewModel.selectedStatusFilter.collectAsState()
    val restaurant by viewModel.restaurant.collectAsState()

    // Varsayılan görünüm: Sadece aktif (teslim edilmemiş ve iptal edilmemiş) siparişleri gösterir
    val filteredOrders = remember(orders, filter) {
        when (filter) {
            "all" -> orders.filter { it.status != "delivered" && it.status != "cancelled" }
            "delivered" -> orders.filter { it.status == "delivered" }
            "cancelled" -> orders.filter { it.status == "cancelled" }
            else -> orders.filter { it.status == filter }
        }
    }

    val activeCount = orders.count { it.status != "delivered" && it.status != "cancelled" }
    var orderToCancel by remember { mutableStateOf<Order?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = {
                    Column {
                        Text(
                            text = restaurant?.name ?: "Sade.C Kahve Gerze",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Canlı Sipariş Akışı ($activeCount Aktif)",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarmGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ForestGreen,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    if (activeCount > 0) {
                        Surface(
                            color = ForestGreen,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text(
                                text = "🔔 $activeCount Aktif",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter Chips Bar
            StatusFilterRow(
                selectedFilter = filter,
                activeCount = activeCount,
                onFilterSelected = { viewModel.setStatusFilter(it) }
            )

            if (filteredOrders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Slate500
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (filter == "all") "Bekleyen aktif sipariş yok 🎉" else "Bu kategoride sipariş bulunmuyor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ForestGreen
                        )
                        Text(
                            text = "Müşteriler QR menüyü okutup sipariş verdiğinde anlık olarak burada listelenecektir.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate500,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredOrders, key = { it.id }) { order ->
                        OrderCard(
                            order = order,
                            onClick = { onOrderClick(order) },
                            onDeliver = {
                                viewModel.updateOrderStatus(order.id, "delivered")
                            },
                            onCancel = {
                                orderToCancel = order
                            }
                        )
                    }
                }
            }
        }
    }

    // Cancel Reason Dialog for Card Action
    orderToCancel?.let { order ->
        CancelReasonDialog(
            onConfirm = { reason ->
                viewModel.cancelOrder(order.id, reason)
                orderToCancel = null
            },
            onDismiss = { orderToCancel = null }
        )
    }
}

@Composable
fun StatusFilterRow(
    selectedFilter: String,
    activeCount: Int,
    onFilterSelected: (String) -> Unit
) {
    val filters = listOf(
        "all" to if (activeCount > 0) "Aktif Siparişler ($activeCount)" else "Aktif Siparişler",
        "delivered" to "Teslim Edilenler",
        "cancelled" to "İptal Edilenler"
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters) { (key, label) ->
            val isSelected = selectedFilter == key
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(key) },
                label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ForestGreen,
                    selectedLabelColor = WarmGold
                ),
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
fun OrderCard(
    order: Order,
    onClick: () -> Unit,
    onDeliver: () -> Unit,
    onCancel: () -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val formattedTime = order.createdAt?.let { timeFormat.format(it) } ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Table Label + Customer Name + Status Pill
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
                            text = order.tableLabel.ifBlank { "Masa" },
                            color = ForestGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (order.customerName.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "👤 ${order.customerName}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = ForestGreen
                        )
                    }
                    if (formattedTime.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate500
                        )
                    }
                }

                OrderStatusBadge(status = order.status)
            }

            if (order.cancelReason.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "❌ İptal Sebebi: ${order.cancelReason}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DangerRed,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Order Items Summary
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                order.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${item.quantity}x ${item.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = ForestGreen
                        )
                        Text(
                            text = "₺${"%.2f".format(item.unitPrice * item.quantity)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate700
                        )
                    }
                    if (item.note.isNotBlank()) {
                        Text(
                            text = "↳ Not: ${item.note}",
                            fontSize = 12.sp,
                            color = WarningYellow,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }

            if (order.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = SoftMintGreen,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Müşteri Notu: ${order.note}",
                        fontSize = 12.sp,
                        color = ForestGreen,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Slate100)
            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Row: Total Price + Action Buttons (Teslim Et & İptal)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Toplam", fontSize = 11.sp, color = Slate500)
                    Text(
                        text = "₺${"%.2f".format(order.totalPrice)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = WarmGold
                    )
                }

                // Quick Status Action Button
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (order.status != "delivered" && order.status != "cancelled") {
                        Button(
                            onClick = onDeliver,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("✅ Teslim Et", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WarmGold)
                        }

                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("❌ İptal", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OrderStatusBadge(status: String) {
    val (label, bg, fg) = when (status) {
        "delivered" -> Triple("Teslim Edildi", SoftMintGreen, ForestGreen)
        "cancelled" -> Triple("İptal Edildi", Color(0xFFFEE2E2), DangerRed)
        else -> Triple("Bekliyor 🕒", Color(0xFFFEF3C7), WarningYellow)
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
