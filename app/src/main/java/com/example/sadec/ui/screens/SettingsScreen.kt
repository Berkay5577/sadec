package com.example.sadec.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sadec.R
import com.example.sadec.ui.theme.*
import com.example.sadec.ui.viewmodel.MainViewModel
import com.example.sadec.util.SoundPlayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateToDashboard: () -> Unit,
    onSignOut: () -> Unit
) {
    val restaurantId by viewModel.restaurantId.collectAsState()
    val restaurant by viewModel.restaurant.collectAsState()
    val context = LocalContext.current

    var webUrlInput by remember(restaurant?.webMenuUrl) { 
        mutableStateOf(restaurant?.webMenuUrl ?: "https://sadec.vercel.app") 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar & Restoran Bilgisi", fontWeight = FontWeight.Bold, color = ForestGreen) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Restaurant Info Card
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
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(ForestGreen)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.sadec_logo),
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = restaurant?.name ?: "Sade.C Kahve Gerze",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreen
                            )
                            Text(
                                text = "Şube Kodu: $restaurantId",
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        }
                    }
                }
            }

            // 📊 SATIŞ DASHBOARD & RAPORLARI BANNER
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToDashboard() },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ForestGreen),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📊 Satış Dashboard & Raporları",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = WarmGold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Adet bazlı ürün satışları (Espresso x14 vb.), ciro analizi, elle kasa satışı ve sipariş geçmişi.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.88f),
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Surface(
                        color = WarmGold,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Aç",
                                tint = ForestGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Web Menu Domain / URL Settings
            Text("Web QR Menü Alan Adı (Vercel / Domain)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ForestGreen)
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Vercel üzerinde açılan canlı web sitenizin adresini buraya yapıştırın. QR kodlar bu adresi kullanacaktır.",
                        fontSize = 12.sp,
                        color = Slate500
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = webUrlInput,
                        onValueChange = { webUrlInput = it },
                        label = { Text("Web Menü Linki") },
                        placeholder = { Text("https://sadec.vercel.app") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (webUrlInput.isNotBlank()) {
                                    viewModel.updateWebMenuUrl(webUrlInput.trim())
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                        ) {
                            Text("Linki Kaydet", color = WarmGold, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val targetUrl = if (webUrlInput.startsWith("http")) webUrlInput else "https://$webUrlInput"
                                val fullTestUrl = "$targetUrl/?restId=$restaurantId&table=table-bar"
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fullTestUrl))
                                context.startActivity(browserIntent)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SageGreen)
                        ) {
                            Text("Aç & Test Et 🌐", color = Color.White)
                        }
                    }
                }
            }

            // Quick Tools & Testing
            Text("Test & Ses Araçları", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ForestGreen)

            // Test Sound
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        colors = ButtonDefaults.buttonColors(containerColor = WarmGold)
                    ) {
                        Text("Çal 🔔", color = ForestGreen, fontWeight = FontWeight.Bold)
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
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Oturumu Kapat", fontWeight = FontWeight.Bold)
            }
        }
    }
}
