package com.example.sadec.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
fun OrderDetailScreen(
    order: Order,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val timeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val formattedTime = order.createdAt?.let { timeFormat.format(it) } ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sipariş Detayı (#${order.id.takeLast(6).uppercase()})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = order.tableLabel.ifBlank { "Masa" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = OrangeDark
                            )
                            if (formattedTime.isNotBlank()) {
                                Text(
                                    text = formattedTime,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Slate500
                                )
                            }
                        }
                        OrderStatusBadge(status = order.status)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Sipariş Durumunu Değiştir:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate700)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Status Change Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusButton(
                            label = "Bekliyor",
                            isActive = order.status == "pending",
                            color = WarningYellow,
                            modifier = Modifier.weight(1f)
                        ) { viewModel.updateOrderStatus(order.id, "pending") }

                        StatusButton(
                            label = "Hazırlanıyor",
                            isActive = order.status == "preparing",
                            color = InfoBlue,
                            modifier = Modifier.weight(1f)
                        ) { viewModel.updateOrderStatus(order.id, "preparing") }

                        StatusButton(
                            label = "Hazır",
                            isActive = order.status == "ready",
                            color = SuccessGreen,
                            modifier = Modifier.weight(1f)
                        ) { viewModel.updateOrderStatus(order.id, "ready") }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusButton(
                            label = "Teslim Edildi",
                            isActive = order.status == "delivered",
                            color = Slate700,
                            modifier = Modifier.weight(1f)
                        ) { viewModel.updateOrderStatus(order.id, "delivered") }

                        StatusButton(
                            label = "İptal Et",
                            isActive = order.status == "cancelled",
                            color = DangerRed,
                            modifier = Modifier.weight(1f)
                        ) { viewModel.updateOrderStatus(order.id, "cancelled") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Sipariş Edilen Ürünler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(order.items) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${item.quantity}x ${item.name}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "₺${"%.2f".format(item.unitPrice * item.quantity)}",
                                    fontWeight = FontWeight.Bold,
                                    color = OrangePrimary
                                )
                            }
                            Text(
                                text = "Birim Fiyat: ₺${"%.2f".format(item.unitPrice)}",
                                fontSize = 12.sp,
                                color = Slate500
                            )
                            if (item.note.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "↳ Not: ${item.note}",
                                    fontSize = 13.sp,
                                    color = WarningYellow
                                )
                            }
                        }
                    }
                }
            }

            // Order Note & Summary Footer
            if (order.note.isNotBlank()) {
                Surface(
                    color = Slate100,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Genel Sipariş Notu:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(order.note, fontSize = 13.sp, color = Slate800)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Genel Toplam", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "₺${"%.2f".format(order.totalPrice)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = OrangePrimary
                    )
                }
            }
        }
    }
}

@Composable
fun StatusButton(
    label: String,
    isActive: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) color else Slate100,
            contentColor = if (isActive) Color.White else Slate700
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
    }
}
