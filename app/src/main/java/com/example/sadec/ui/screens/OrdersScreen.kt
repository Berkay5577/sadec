package com.example.sadec.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

    val filteredOrders = remember(orders, filter) {
        if (filter == "all") orders else orders.filter { it.status == filter }
    }

    val pendingCount = orders.count { it.status == "pending" }
    val preparingCount = orders.count { it.status == "preparing" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = restaurant?.name ?: "Sipariş Yönetimi",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Canlı Sipariş Akışı (${orders.size} Toplam)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                actions = {
                    if (pendingCount > 0) {
                        Surface(
                            color = OrangePrimary,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text(
                                text = "🔔 $pendingCount Yeni",
                                color = Color.White,
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
                pendingCount = pendingCount,
                preparingCount = preparingCount,
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
                            text = "Henüz sipariş bulunmuyor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Müşteriler QR menüyü okutup sipariş verdiğinde anlık olarak burada listelenecektir.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate500,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.seedSampleMenu() },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                        ) {
                            Text("Örnek Menü & Masaları Yükle 🍔")
                        }
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
                            onStatusChange = { newStatus ->
                                viewModel.updateOrderStatus(order.id, newStatus)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusFilterRow(
    selectedFilter: String,
    pendingCount: Int,
    preparingCount: Int,
    onFilterSelected: (String) -> Unit
) {
    val filters = listOf(
        "all" to "Tümü",
        "pending" to if (pendingCount > 0) "Bekleyen ($pendingCount)" else "Bekleyen",
        "preparing" to if (preparingCount > 0) "Hazırlanan ($preparingCount)" else "Hazırlanıyor",
        "ready" to "Hazır",
        "delivered" to "Teslim Edildi",
        "cancelled" to "İptal"
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
                    selectedContainerColor = OrangePrimary,
                    selectedLabelColor = Color.White
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
    onStatusChange: (String) -> Unit
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
            // Header Row: Table Label + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = OrangeLight,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = order.tableLabel.ifBlank { "Masa" },
                            color = OrangeDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
                            fontWeight = FontWeight.Medium
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
                    color = Slate100,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Müşteri Notu: ${order.note}",
                        fontSize = 12.sp,
                        color = Slate800,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = Slate100)
            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Row: Total Price + Quick Action Buttons
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
                        color = OrangePrimary
                    )
                }

                // Quick Status Action Button
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (order.status) {
                        "pending" -> {
                            Button(
                                onClick = { onStatusChange("preparing") },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = InfoBlue),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("👨‍🍳 Hazırla", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        "preparing" -> {
                            Button(
                                onClick = { onStatusChange("ready") },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("🍽️ Hazır", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        "ready" -> {
                            Button(
                                onClick = { onStatusChange("delivered") },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("✅ Teslim Et", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OrderStatusBadge(status: String) {
    val (bgColor, textColor, label) = when (status) {
        "pending" -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), "Bekliyor 🕒")
        "preparing" -> Triple(Color(0xFFE0E7FF), Color(0xFF4338CA), "Hazırlanıyor 👨‍🍳")
        "ready" -> Triple(Color(0xFFD1FAE5), Color(0xFF059669), "Hazır 🍽️")
        "delivered" -> Triple(Color(0xFFECFDF5), Color(0xFF047857), "Teslim Edildi ✅")
        "cancelled" -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "İptal ❌")
        else -> Triple(Slate100, Slate700, status)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
