package com.example.sadec.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.sadec.R
import com.example.sadec.data.model.PopupCampaign
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
    val menuItems by viewModel.menuItems.collectAsState()
    val tables by viewModel.tables.collectAsState()
    val context = LocalContext.current

    val rawBaseUrl = restaurant?.webMenuUrl?.ifBlank { "https://sadec.vercel.app" } ?: "https://sadec.vercel.app"
    val baseUrl = if (rawBaseUrl.startsWith("http")) rawBaseUrl else "https://$rawBaseUrl"

    // --- POP-UP CAMPAIGN STATE ---
    val existingCampaign = restaurant?.popupCampaign
    var isPopupActive by remember(existingCampaign) { mutableStateOf(existingCampaign?.isActive ?: false) }
    var popupBadge by remember(existingCampaign) { mutableStateOf(existingCampaign?.badge ?: "DENEDİNİZ Mİ? 🌟") }
    var popupTitle by remember(existingCampaign) { mutableStateOf(existingCampaign?.title ?: "") }
    var popupDesc by remember(existingCampaign) { mutableStateOf(existingCampaign?.description ?: "") }
    var popupPrice by remember(existingCampaign) { mutableStateOf(existingCampaign?.priceText ?: "") }
    var popupButtonText by remember(existingCampaign) { mutableStateOf(existingCampaign?.buttonText ?: "Hemen Keşfet ✨") }
    var selectedTargetItemId by remember(existingCampaign) { mutableStateOf(existingCampaign?.targetMenuItemId ?: "") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isTargetMenuDropdownExpanded by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = { Text("Ayarlar & Restoran Bilgisi", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ForestGreen,
                    titleContentColor = Color.White
                )
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
            // 📄 TOPLU QR KODLARI PDF ÇIKARTMA KARTI
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        com.example.sadec.util.QrPdfGenerator.generateAndSharePdf(
                            context = context,
                            tables = tables,
                            restaurantId = restaurantId,
                            baseUrl = baseUrl,
                            restaurantName = restaurant?.name ?: "Sade.C Kahve Gerze"
                        )
                    },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SoftMintGreen),
                border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("📄", fontSize = 22.sp)
                            Text(
                                text = "Masa QR Standlarını PDF Çıkart",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = ForestGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Üstte QR kod, altta masa numarası olacak şekilde tüm masaları A4 baskıya hazır PDF olarak indirir/paylaşır.",
                            fontSize = 12.sp,
                            color = SageGreen,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Surface(
                        color = ForestGreen,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "PDF Çıkart",
                                tint = WarmGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 🌟 QR MENÜ KAMPANYA POP-UP (DENEDİNİZ Mİ?) AYARLARI KARTI
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                border = BorderStroke(1.5.dp, if (isPopupActive) WarmGold else ForestGreen.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header & Active/Passive Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(if (isPopupActive) WarmGold else Slate100, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🌟", fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "QR Menü Kampanya Pop-up",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = ForestGreen
                                )
                                Text(
                                    text = if (isPopupActive) "🟢 Menüde Aktif & Gösteriliyor" else "⚪ Kapalı / Pasif",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isPopupActive) SuccessGreen else Slate500
                                )
                            }
                        }

                        Switch(
                            checked = isPopupActive,
                            onCheckedChange = { isPopupActive = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ForestGreen,
                                checkedTrackColor = WarmGold,
                                uncheckedThumbColor = Slate500,
                                uncheckedTrackColor = Slate100
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Müşteriler masadaki QR menüyü okutup açtığında karşılarına çıkacak özel tanıtım kartını özelleştirin.",
                        fontSize = 12.sp,
                        color = Slate500,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = ForestGreen.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Image Preview & Upload
                    Text("Pop-up Ürün Görseli:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF16241D))
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        val currentImgUrl = existingCampaign?.imageUrl
                        if (selectedImageUri != null) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Seçilen Görsel",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else if (!currentImgUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = currentImgUrl,
                                contentDescription = "Mevcut Görsel",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = WarmGold, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Görsel Yüklemek İçin Dokunun 📷", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        // Badge on image preview
                        Surface(
                            color = ForestGreen.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Değiştir 📷",
                                color = WarmGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Badge & Title
                    OutlinedTextField(
                        value = popupBadge,
                        onValueChange = { popupBadge = it },
                        label = { Text("Pop-up Rozeti (Üst Başlık)") },
                        placeholder = { Text("Örn: DENEDİNİZ Mİ? 🌟, GÜNÜN SPESİYALİ") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = popupTitle,
                        onValueChange = { popupTitle = it },
                        label = { Text("Ürün / Kampanya Başlığı") },
                        placeholder = { Text("Örn: San Sebastian Cheesecake & Belçika Çikolatası") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Description
                    OutlinedTextField(
                        value = popupDesc,
                        onValueChange = { popupDesc = it },
                        label = { Text("Açıklama / Davet Metni") },
                        placeholder = { Text("Örn: Özel Belçika çikolatası eritilerek taptaze hazırlanan enfes lezzeti denediniz mi?") },
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Price & Button Text Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = popupPrice,
                            onValueChange = { popupPrice = it },
                            label = { Text("Fiyat / İndirim") },
                            placeholder = { Text("Örn: ₺160,00") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = popupButtonText,
                            onValueChange = { popupButtonText = it },
                            label = { Text("Buton Metni") },
                            placeholder = { Text("Örn: Hemen İncele ✨") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 5. Target Menu Item Selector (Dropdown)
                    Text("Butona Basılınca Açılacak Menü Ürünü:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                    Spacer(modifier = Modifier.height(4.dp))

                    val selectedItem = menuItems.find { it.id == selectedTargetItemId }
                    ExposedDropdownMenuBox(
                        expanded = isTargetMenuDropdownExpanded,
                        onExpandedChange = { isTargetMenuDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedItem?.name ?: if (selectedTargetItemId.isBlank()) "Genel Menüye Yönlendir" else "Seçili Ürün",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isTargetMenuDropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = isTargetMenuDropdownExpanded,
                            onDismissRequest = { isTargetMenuDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Genel Menü / Özel Ürün Yok") },
                                onClick = {
                                    selectedTargetItemId = ""
                                    isTargetMenuDropdownExpanded = false
                                }
                            )
                            menuItems.forEach { mItem ->
                                DropdownMenuItem(
                                    text = { Text("${mItem.name} (₺${"%.2f".format(mItem.price)})") },
                                    onClick = {
                                        selectedTargetItemId = mItem.id
                                        if (popupTitle.isBlank()) popupTitle = mItem.name
                                        if (popupPrice.isBlank()) popupPrice = "₺${"%.2f".format(mItem.price)}"
                                        isTargetMenuDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Save Button
                    Button(
                        onClick = {
                            val cleanPrice = popupPrice.trim()
                            val formattedPrice = if (cleanPrice.isNotBlank()) {
                                if (!cleanPrice.startsWith("₺") && !cleanPrice.contains("TL", ignoreCase = true)) "₺$cleanPrice" else cleanPrice
                            } else ""

                            val newCampaign = PopupCampaign(
                                isActive = isPopupActive,
                                badge = popupBadge.ifBlank { "DENEDİNİZ Mİ? 🌟" },
                                title = popupTitle.trim(),
                                description = popupDesc.trim(),
                                imageUrl = existingCampaign?.imageUrl ?: "",
                                priceText = formattedPrice,
                                buttonText = popupButtonText.ifBlank { "Hemen Keşfet ✨" },
                                targetMenuItemId = selectedTargetItemId,
                                updatedAt = System.currentTimeMillis()
                            )

                            viewModel.savePopupCampaign(
                                campaign = newCampaign,
                                imageUri = selectedImageUri,
                                onComplete = {
                                    selectedImageUri = null
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = WarmGold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPopupActive) "Pop-up Kampanyasını Kaydet & Yayına Al 🚀" else "Ayarları Kaydet (Pop-up Pasif)",
                            fontWeight = FontWeight.Bold,
                            color = WarmGold
                        )
                    }
                }
            }

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

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
