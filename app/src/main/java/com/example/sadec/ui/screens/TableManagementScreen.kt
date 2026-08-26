package com.example.sadec.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sadec.data.model.Order
import com.example.sadec.data.model.TableItem
import com.example.sadec.ui.theme.*
import com.example.sadec.ui.viewmodel.MainViewModel
import com.example.sadec.util.QrCodeGenerator
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableManagementScreen(
    viewModel: MainViewModel
) {
    val tables by viewModel.tables.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val restaurantId by viewModel.restaurantId.collectAsState()
    val restaurant by viewModel.restaurant.collectAsState()
    val context = LocalContext.current

    val rawBaseUrl = restaurant?.webMenuUrl?.ifBlank { "https://sadec.vercel.app" } ?: "https://sadec.vercel.app"
    val baseUrl = if (rawBaseUrl.startsWith("http")) rawBaseUrl else "https://$rawBaseUrl"

    var showAddTableDialog by remember { mutableStateOf(false) }
    var newTableLabel by remember { mutableStateOf("") }
    var selectedTableForQr by remember { mutableStateOf<TableItem?>(null) }
    var selectedTableForDetail by remember { mutableStateOf<TableItem?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Active tables: tables that have unarchived, non-cancelled orders with unpaid items or pending status
    val activeOrders = orders.filter { !it.isArchived && it.status != "cancelled" && (!it.isFullyPaid() || it.status == "pending" || it.status == "preparing" || it.status == "ready") }
    val activeTableCount = tables.count { table -> activeOrders.any { it.tableId == table.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = {
                    Column {
                        Text("Masa QR Kodları", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text = "${tables.size} Masa Tanımlı • QR Standı & PDF",
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
                    IconButton(onClick = {
                        com.example.sadec.util.QrPdfGenerator.generateAndSharePdf(
                            context = context,
                            tables = tables,
                            restaurantId = restaurantId,
                            baseUrl = baseUrl,
                            restaurantName = restaurant?.name ?: "Sade.C Kahve Gerze"
                        )
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Tüm QR'ları PDF Çıkart", tint = WarmGold)
                    }
                    IconButton(onClick = {
                        newTableLabel = "Masa ${tables.size + 1}"
                        showAddTableDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Masa Ekle", tint = Color.White)
                    }
                }
            )
        }
    ) { paddingValues ->
        if (tables.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(64.dp), tint = Slate500)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Henüz masa eklenmemiş", fontWeight = FontWeight.SemiBold, color = Slate500)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            newTableLabel = "BAR"
                            showAddTableDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                    ) {
                        Text("İlk Masayı Ekle", color = WarmGold)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = 120.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Table Summary & Security Banner
                item(span = { GridItemSpan(2) }) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (activeTableCount > 0) SoftMintGreen else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (activeTableCount > 0) Icons.Default.ReceiptLong else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (activeTableCount > 0) ForestGreen else SuccessGreen,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (activeTableCount > 0) "🟢 $activeTableCount Masada Açık Hesap Var" else "✨ Tüm Masalar Müsait",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ForestGreen
                                )
                                Text(
                                    text = "Masaya tıklayarak içindeki ürünleri görebilir, tek tek veya toplu ödeme alabilirsiniz.",
                                    fontSize = 11.sp,
                                    color = SageGreen
                                )
                            }
                        }
                    }
                }

                items(tables, key = { it.id }) { table ->
                    val tableOrders = activeOrders.filter { it.tableId == table.id }
                    val isOccupied = tableOrders.isNotEmpty()
                    val totalUnpaid = tableOrders.sumOf { it.remainingAmount() }

                    TableCard(
                        table = table,
                        isOccupied = isOccupied,
                        orderCount = tableOrders.size,
                        totalUnpaid = totalUnpaid,
                        onClick = {
                            selectedTableForDetail = table
                        },
                        onShowQr = {
                            val key = if (table.qrKey.isNotBlank()) table.qrKey else UUID.randomUUID().toString().take(8)
                            if (table.qrKey.isBlank()) {
                                viewModel.saveTable(table.label, table.id, key)
                            }
                            val webUrl = "$baseUrl/?restId=$restaurantId&table=${table.id}&key=$key"
                            qrBitmap = QrCodeGenerator.generateQrBitmap(webUrl, 600)
                            selectedTableForQr = table.copy(qrKey = key)
                        },
                        onDelete = {
                            viewModel.deleteTable(table.id)
                        }
                    )
                }
            }
        }
    }

    // Add Table Dialog
    if (showAddTableDialog) {
        AlertDialog(
            onDismissRequest = { showAddTableDialog = false },
            title = { Text("Yeni Masa Oluştur") },
            text = {
                OutlinedTextField(
                    value = newTableLabel,
                    onValueChange = { newTableLabel = it },
                    label = { Text("Masa Adı / No (Örn: BAR, İÇ 1, DIŞ 2, Y1)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTableLabel.isNotBlank()) {
                            val newKey = UUID.randomUUID().toString().take(8)
                            viewModel.saveTable(newTableLabel.trim(), qrKey = newKey)
                            showAddTableDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Text("Oluştur", color = WarmGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTableDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // Active Table Detail & Item-by-Item Payment Dialog
    selectedTableForDetail?.let { table ->
        val tableOrders = orders.filter { it.tableId == table.id && !it.isArchived && it.status != "cancelled" }
        val activeTableOrders = tableOrders.filter { !it.isFullyPaid() || it.status == "pending" || it.status == "preparing" || it.status == "ready" }
        val remainingTotal = activeTableOrders.sumOf { it.remainingAmount() }
        val paidTotal = activeTableOrders.sumOf { it.paidAmount() }

        AlertDialog(
            onDismissRequest = { selectedTableForDetail = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "📍 ${table.label}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = ForestGreen
                        )
                        Text(
                            text = if (activeTableOrders.isNotEmpty()) "Açık Masa Hesabı (${activeTableOrders.size} Adisyon)" else "Masa Boş / Hesap Yok",
                            fontSize = 12.sp,
                            color = if (activeTableOrders.isNotEmpty()) WarmGold else Slate500
                        )
                    }
                    if (activeTableOrders.isNotEmpty()) {
                        Surface(
                            color = SoftMintGreen,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "🟢 DOLU",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                ) {
                    if (activeTableOrders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Bu masada bekleyen sipariş veya ödeme yok.", fontWeight = FontWeight.SemiBold, color = ForestGreen)
                                Text("Müşteri QR kodu okuttuğunda siparişler burada belirecektir.", fontSize = 12.sp, color = Slate500)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            lazyListItems(activeTableOrders) { order ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.2f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
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
                                            val timeStr = order.createdAt?.let { SimpleDateFormat("HH:mm", Locale("tr")).format(it) } ?: ""
                                            Text(text = "🕒 $timeStr", fontSize = 11.sp, color = Slate500)
                                        }

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = ForestGreen.copy(alpha = 0.1f))

                                        // Item by Item listing with payment buttons
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
                                                        Text(text = "Not: ${item.note}", fontSize = 11.sp, color = Color(0xFFD97706))
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
                                                        onClick = {
                                                            viewModel.payOrderItem(order.id, itemIdx)
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(34.dp)
                                                    ) {
                                                        Text("Öde 💳", color = WarmGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }

                                        // Full Order Quick Payment
                                        if (!order.isFullyPaid()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.payFullOrder(order.id)
                                                },
                                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestGreen),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("Bu Adisyonu Tamamen Kapat (₺${"%.2f".format(order.remainingAmount())})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Financial Summary of Table
                        Surface(
                            color = ForestGreen,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("KALAN ÖDENECEK", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                                    Text("₺${"%.2f".format(remainingTotal)}", fontSize = 18.sp, color = WarmGold, fontWeight = FontWeight.Bold)
                                }
                                if (remainingTotal > 0) {
                                    Button(
                                        onClick = {
                                            activeTableOrders.forEach { ord ->
                                                if (!ord.isFullyPaid()) viewModel.payFullOrder(ord.id)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = WarmGold),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Tüm Masayı Kapat ✨", color = ForestGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTableForDetail = null }) {
                    Text("Kapat", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // QR Code Preview Dialog
    selectedTableForQr?.let { table ->
        val webUrl = "$baseUrl/?restId=$restaurantId&table=${table.id}&key=${table.qrKey}"

        AlertDialog(
            onDismissRequest = { selectedTableForQr = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${table.label} — Güvenli QR Kod",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = ForestGreen
                    )
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Müşteriler bu QR kodu okuttuğunda doğrudan bu masanın menüsü açılır.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    qrBitmap?.let { bmp ->
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "QR Kod",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = webUrl,
                        fontSize = 10.sp,
                        color = Slate500
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "${table.label} Menü Linki: $webUrl")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "QR Linkini Paylaş"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = WarmGold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Link Paylaş", color = WarmGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedTableForQr = null }) {
                    Text("Kapat")
                }
            }
        )
    }
}

@Composable
fun TableCard(
    table: TableItem,
    isOccupied: Boolean,
    orderCount: Int,
    totalUnpaid: Double,
    onClick: () -> Unit,
    onShowQr: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOccupied) SoftMintGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isOccupied) BorderStroke(1.5.dp, ForestGreen) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isOccupied) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (isOccupied) ForestGreen else Slate500.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isOccupied) "🟢 DOLU" else "⚪ BOŞ",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOccupied) Color.White else Slate500,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(if (isOccupied) ForestGreen else ForestGreen.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isOccupied) Icons.Default.ReceiptLong else Icons.Default.QrCode2,
                    contentDescription = null,
                    tint = if (isOccupied) WarmGold else ForestGreen,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = table.label,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = ForestGreen
            )

            if (isOccupied) {
                Text(
                    text = "₺${"%.2f".format(totalUnpaid)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFFB45309)
                )
                Text(
                    text = "$orderCount Sipariş",
                    fontSize = 11.sp,
                    color = SageGreen
                )
            } else {
                Text(
                    text = "Müsait",
                    fontSize = 12.sp,
                    color = Slate500
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.weight(1f).height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOccupied) ForestGreen else SageGreen
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(if (isOccupied) "Hesap 💳" else "İncele", fontSize = 12.sp, color = WarmGold, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onShowQr,
                    modifier = Modifier
                        .size(34.dp)
                        .background(ForestGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = "QR Kod", tint = ForestGreen, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
