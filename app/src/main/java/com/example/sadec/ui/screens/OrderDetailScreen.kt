package com.example.sadec.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun OrderDetailScreen(
    order: Order,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val timeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val formattedTime = order.createdAt?.let { timeFormat.format(it) } ?: ""

    var showCancelDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sipariş Detayı (#${order.id.takeLast(6).uppercase()})", fontWeight = FontWeight.Bold, color = Color.White) },
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
                                color = ForestGreen
                            )
                            if (order.customerName.isNotBlank()) {
                                Text(
                                    text = "Müşteri: ${order.customerName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = ForestGreen
                                )
                            }
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

                    if (order.cancelReason.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "❌ İptal Sebebi: ${order.cancelReason}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DangerRed,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Sipariş İşlemleri:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Sadece Teslim Edildi ve İptal Et Butonları
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.updateOrderStatus(order.id, "delivered")
                                onBack() // Teslim edildiğinde sayfadan çıkıp ana ekrana döner
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (order.status == "delivered") ForestGreen else SuccessGreen
                            )
                        ) {
                            Text(
                                text = if (order.status == "delivered") "✓ Teslim Edildi" else "Teslim Edildi ✅",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }

                        Button(
                            onClick = { showCancelDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (order.status == "cancelled") DangerRed else Color(0xFFFEE2E2)
                            )
                        ) {
                            Text(
                                text = "İptal Et ❌",
                                fontWeight = FontWeight.Bold,
                                color = if (order.status == "cancelled") Color.White else DangerRed,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Sipariş Edilen Ürünler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ForestGreen)
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
                                    fontSize = 15.sp,
                                    color = ForestGreen
                                )
                                Text(
                                    text = "₺${"%.2f".format(item.unitPrice * item.quantity)}",
                                    fontWeight = FontWeight.Bold,
                                    color = WarmGold
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
                    color = SoftMintGreen,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Genel Sipariş Notu:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ForestGreen)
                        Text(order.note, fontSize = 13.sp, color = Slate700)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ForestGreen)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Genel Toplam", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        text = "₺${"%.2f".format(order.totalPrice)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = WarmGold
                    )
                }
            }
        }
    }

    // Cancel Reason Dialog
    if (showCancelDialog) {
        CancelReasonDialog(
            onConfirm = { reason ->
                viewModel.cancelOrder(order.id, reason)
                showCancelDialog = false
                onBack() // İptal edildiğinde ana ekrana döner
            },
            onDismiss = { showCancelDialog = false }
        )
    }
}

@Composable
fun CancelReasonDialog(
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    val reasons = listOf(
        "Müşteri vazgeçti / ayrıldı",
        "Ürün tükendi / stok yetersiz",
        "Hatalı / Yanlış sipariş",
        "Masa boşaldı / Yanlış masa",
        "Diğer..."
    )
    var selectedReason by remember { mutableStateOf(reasons[0]) }
    var customReason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Siparişi İptal Et", fontWeight = FontWeight.Bold, color = DangerRed) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lütfen iptal gerekçesini seçin veya yazın:", fontSize = 13.sp, color = Slate500)

                reasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(reason, fontSize = 13.sp, fontWeight = if (selectedReason == reason) FontWeight.Bold else FontWeight.Normal)
                    }
                }

                if (selectedReason == "Diğer...") {
                    OutlinedTextField(
                        value = customReason,
                        onValueChange = { customReason = it },
                        label = { Text("İptal Sebebi") },
                        placeholder = { Text("Sebebi buraya yazınız...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalReason = if (selectedReason == "Diğer...") customReason.ifBlank { "Diğer" } else selectedReason
                    onConfirm(finalReason)
                },
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
            ) {
                Text("İptali Onayla ❌", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}
