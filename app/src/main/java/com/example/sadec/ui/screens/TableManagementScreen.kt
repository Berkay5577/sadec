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
import androidx.core.content.FileProvider
import com.example.sadec.data.model.TableItem
import com.example.sadec.ui.theme.OrangeDark
import com.example.sadec.ui.theme.OrangeLight
import com.example.sadec.ui.theme.OrangePrimary
import com.example.sadec.ui.theme.Slate500
import com.example.sadec.ui.viewmodel.MainViewModel
import com.example.sadec.util.QrCodeGenerator
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableManagementScreen(
    viewModel: MainViewModel
) {
    val tables by viewModel.tables.collectAsState()
    val restaurantId by viewModel.restaurantId.collectAsState()
    val context = LocalContext.current

    var showAddTableDialog by remember { mutableStateOf(false) }
    var newTableLabel by remember { mutableStateOf("") }
    var selectedTableForQr by remember { mutableStateOf<TableItem?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Masa & QR Kod Yönetimi", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        newTableLabel = "Masa ${tables.size + 1}"
                        showAddTableDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Masa Ekle", tint = OrangePrimary)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    newTableLabel = "Masa ${tables.size + 1}"
                    showAddTableDialog = true
                },
                containerColor = OrangePrimary,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Yeni Masa Ekle") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (tables.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Slate500
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Henüz masa tanımlanmamış", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.seedSampleMenu() },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                        ) {
                            Text("Otomatik Örnek Masaları Ekle 🏷️")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tables, key = { it.id }) { table ->
                        TableCard(
                            table = table,
                            onShowQr = {
                                selectedTableForQr = table
                                // URL with restaurantId and tableId
                                val qrUrl = "https://sadec-9b458.web.app/?restId=$restaurantId&table=${table.id}"
                                qrBitmap = QrCodeGenerator.generateQrBitmap(qrUrl, 600)
                            },
                            onDelete = { viewModel.deleteTable(table.id) }
                        )
                    }
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
                    label = { Text("Masa Adı / No (Örn: Masa 5, Bahçe 2)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTableLabel.isNotBlank()) {
                            viewModel.saveTable(newTableLabel.trim())
                            showAddTableDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    Text("Oluştur")
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
        AlertDialog(
            onDismissRequest = { selectedTableForQr = null },
            title = {
                Text(
                    text = "${table.label} - QR Menü Kodu",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Müşteriler bu QR kodu okutarak doğrudan masadan sipariş verebilir.",
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
                    val webUrl = "https://sadec-9b458.web.app/?restId=$restaurantId&table=${table.id}"
                    Text(
                        text = webUrl,
                        fontSize = 11.sp,
                        color = Slate500
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Share URL / QR
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Sadec QR Menü (${table.label}): https://sadec-9b458.web.app/?restId=$restaurantId&table=${table.id}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "QR Menü Bağlantısını Paylaş"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paylaş / Yazdır")
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(OrangeLight, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = null,
                    tint = OrangeDark,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = table.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onShowQr,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Text("QR Kod 📱", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Slate500, modifier = Modifier.size(18.dp))
            }
        }
    }
}
