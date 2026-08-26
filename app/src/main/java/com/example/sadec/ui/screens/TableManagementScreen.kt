package com.example.sadec.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.example.sadec.data.model.TableItem
import com.example.sadec.ui.theme.ForestGreen
import com.example.sadec.ui.theme.SageGreen
import com.example.sadec.ui.theme.Slate500
import com.example.sadec.ui.theme.SoftMintGreen
import com.example.sadec.ui.theme.SuccessGreen
import com.example.sadec.ui.theme.WarmGold
import com.example.sadec.ui.viewmodel.MainViewModel
import com.example.sadec.util.QrCodeGenerator
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableManagementScreen(
    viewModel: MainViewModel
) {
    val tables by viewModel.tables.collectAsState()
    val restaurantId by viewModel.restaurantId.collectAsState()
    val restaurant by viewModel.restaurant.collectAsState()
    val context = LocalContext.current

    val rawBaseUrl = restaurant?.webMenuUrl?.ifBlank { "https://sadec.vercel.app" } ?: "https://sadec.vercel.app"
    val baseUrl = if (rawBaseUrl.startsWith("http")) rawBaseUrl else "https://$rawBaseUrl"

    var showAddTableDialog by remember { mutableStateOf(false) }
    var newTableLabel by remember { mutableStateOf("") }
    var selectedTableForQr by remember { mutableStateOf<TableItem?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = { Text("Masa & QR Kod Yönetimi", fontWeight = FontWeight.Bold, color = Color.White) },
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
                // Security Info Banner (Spans full width)
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SoftMintGreen)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("🔒 Korumalı QR Doğrulaması Aktif", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ForestGreen)
                                Text("QR kodlar şifrelidir. Müşteriler bağlantıyı kopyalayıp dükkan dışından sipariş veremez.", fontSize = 11.sp, color = SageGreen)
                            }
                        }
                    }
                }

                items(tables, key = { it.id }) { table ->
                    TableCard(
                        table = table,
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
                            putExtra(Intent.EXTRA_TEXT, "Sade.C QR Menü (${table.label}): $webUrl")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "QR Menü Bağlantısını Paylaş"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp), tint = WarmGold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paylaş / Yazdır", color = WarmGold)
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
    onShowQr: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(ForestGreen, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = null,
                    tint = WarmGold,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = table.label,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = ForestGreen
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onShowQr,
                modifier = Modifier.fillMaxWidth().height(38.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp), tint = WarmGold)
                Spacer(modifier = Modifier.width(6.dp))
                Text("QR Kod", fontSize = 13.sp, color = WarmGold)
            }

            Spacer(modifier = Modifier.height(4.dp))

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}
