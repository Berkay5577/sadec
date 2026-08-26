package com.example.sadec.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sadec.ui.theme.*
import com.example.sadec.ui.viewmodel.MainViewModel
import com.example.sadec.util.SoundPlayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onSignOut: () -> Unit
) {
    val restaurant by viewModel.restaurant.collectAsState()
    val restaurantId by viewModel.restaurantId.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar & Dükkan Profili", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Restaurant Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(OrangePrimary, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (restaurant?.name?.firstOrNull() ?: 'S').uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = restaurant?.name ?: "Sadec Restoran",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Restoran Kodu: $restaurantId",
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        }
                    }
                }
            }

            // Quick Tools & Testing
            Text("Test & Hızlı Araçlar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Test Sound
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sipariş Zil Sesi Testi", fontWeight = FontWeight.Bold)
                        Text("Yeni sipariş geldiğinde çalacak ses ve titreşimi test edin.", fontSize = 12.sp, color = Slate500)
                    }
                    Button(
                        onClick = { SoundPlayer.playOrderAlert(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningYellow)
                    ) {
                        Text("Çal 🔔", color = Slate900, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Web Menu Browser Link
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Web QR Menüyü Aç", fontWeight = FontWeight.Bold)
                        Text("Müşteri gözünden QR menüyü tarayıcıda hemen test edin.", fontSize = 12.sp, color = Slate500)
                    }
                    Button(
                        onClick = {
                            val webUrl = "https://sadec-9b458.web.app/?restId=$restaurantId&table=table-1"
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                            context.startActivity(browserIntent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = InfoBlue)
                    ) {
                        Text("Aç 🌐")
                    }
                }
            }

            // Reset / Seed Sample Data
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Örnek Menü & Masaları Yükle", fontWeight = FontWeight.Bold)
                        Text("Veritabanına zengin burger, pizza ve tatlı menüsü ekler.", fontSize = 12.sp, color = Slate500)
                    }
                    Button(
                        onClick = { viewModel.seedSampleMenu() },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                    ) {
                        Text("Yükle 🍔")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sign Out
            OutlinedButton(
                onClick = {
                    viewModel.signOut()
                    onSignOut()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Oturumu Kapat", fontWeight = FontWeight.Bold)
            }
        }
    }
}
