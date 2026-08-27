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
import com.example.sadec.BuildConfig
import com.example.sadec.R
import com.example.sadec.data.model.AppUpdateInfo
import com.example.sadec.data.model.PopupCampaign
import com.example.sadec.ui.theme.*
import com.example.sadec.ui.viewmodel.MainViewModel
import com.example.sadec.util.AppUpdateManager

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
                title = { Text("Yönetim & Ayarlar", fontWeight = FontWeight.Bold, color = Color.White) },
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
            // 1. RESTAURANT PROFILE & STATUS HEADER
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(ForestGreen)
                            .padding(3.dp),
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = restaurant?.name ?: "Sade.C Kahve Gerze",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreen
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Kahvenin en saf hali • Gerze / Sinop",
                            fontSize = 12.sp,
                            color = SageGreen
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            color = SoftMintGreen,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Şube: $restaurantId • Çevrimiçi 🟢",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ForestGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // 2. 📊 SATIŞ DASHBOARD & RAPORLARI BANNER
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToDashboard() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ForestGreen),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = BorderStroke(1.dp, WarmGold.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📊", fontSize = 20.sp)
                            Text(
                                text = "Satış Dashboard & Raporlar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = WarmGold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Haftalık ciro, adet bazlı ürün satışları, manuel kasa satışı ve sipariş geçmişi analizi.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Surface(
                        color = WarmGold,
                        shape = CircleShape,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Aç",
                                tint = ForestGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 3. 🔔 BU CİHAZDA BİLDİRİM VE SES AYARLARI KARTI
            val isDeviceNotifEnabled by viewModel.isDeviceNotificationsEnabled.collectAsState()
            val isNotifSoundEnabled by viewModel.isNotificationSoundEnabled.collectAsState()
            val isWakeScreenEnabled by viewModel.isWakeScreenEnabled.collectAsState()

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                border = BorderStroke(1.5.dp, if (isDeviceNotifEnabled) WarmGold else ForestGreen.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Master Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(if (isDeviceNotifEnabled) ForestGreen else Slate100, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isDeviceNotifEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = if (isDeviceNotifEnabled) WarmGold else Slate500,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Bu Cihazda Bildirimler",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = ForestGreen
                                )
                                Text(
                                    text = if (isDeviceNotifEnabled) "🟢 Açık (Siparişler Bu Telefona Düşer)" else "⚪ Kapalı (Bildirim Gönderilmez)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDeviceNotifEnabled) SuccessGreen else Slate500
                                )
                            }
                        }

                        Switch(
                            checked = isDeviceNotifEnabled,
                            onCheckedChange = { viewModel.setDeviceNotificationsEnabled(it) },
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
                        text = "Masalardan gelen yeni siparişlerin sadece bu kullanılan telefona bildirim ve ses olarak düşüp düşmeyeceğini kontrol edin.",
                        fontSize = 12.sp,
                        color = Slate500,
                        lineHeight = 16.sp
                    )

                    if (isDeviceNotifEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = ForestGreen.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Sub-switch 1: Sound
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text("🔊", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Sipariş Alarm Sesi", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ForestGreen)
                                    Text("Yeni sipariş geldiğinde yüksek sesli uyarı çalar", fontSize = 11.sp, color = Slate500)
                                }
                            }
                            Switch(
                                checked = isNotifSoundEnabled,
                                onCheckedChange = { viewModel.setNotificationSoundEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ForestGreen,
                                    checkedTrackColor = WarmGold,
                                    uncheckedThumbColor = Slate500,
                                    uncheckedTrackColor = Slate100
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Sub-switch 2: Screen Wake
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text("💡", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Ekranı Otomatik Uyandır", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ForestGreen)
                                    Text("Telefon kapalıyken sipariş geldiğinde ekranı açar", fontSize = 11.sp, color = Slate500)
                                }
                            }
                            Switch(
                                checked = isWakeScreenEnabled,
                                onCheckedChange = { viewModel.setWakeScreenEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ForestGreen,
                                    checkedTrackColor = WarmGold,
                                    uncheckedThumbColor = Slate500,
                                    uncheckedTrackColor = Slate100
                                )
                            )
                        }
                    }
                }
            }

            // 4. 🌟 QR MENÜ KAMPANYA POP-UP (DENEDİNİZ Mİ?) AYARLARI KARTI
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
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
                                    .size(38.dp)
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
                        text = "Müşteriler masadaki QR menüyü açtığında karşılarına çıkacak özel kampanya kartını özelleştirin.",
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

            // 4. 📄 TOPLU MASA STANDLARI PDF ÇIKARTMA KARTI
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
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SoftMintGreen),
                border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.25f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                            Text("📄", fontSize = 20.sp)
                            Text(
                                text = "Masa QR Standlarını PDF Çıkart",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = ForestGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tüm masaları A4 baskıya hazır QR masa standı olarak tek tıkla PDF indirir veya paylaşır.",
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

            // 5. 🚀 UYGULAMA SÜRÜMÜ & GÜNCELLEMELER KARTI
            val appUpdateInfo = restaurant?.appUpdateInfo
            val currentVersionName = BuildConfig.VERSION_NAME
            val currentVersionCode = BuildConfig.VERSION_CODE
            val hasUpdate = appUpdateInfo != null && AppUpdateManager.isUpdateAvailable(appUpdateInfo.latestVersionCode)

            var isPublishUpdateSectionExpanded by remember { mutableStateOf(false) }
            var newVersionNameInput by remember { mutableStateOf(appUpdateInfo?.latestVersionName ?: "1.1.0") }
            var newVersionCodeInput by remember { mutableStateOf((appUpdateInfo?.latestVersionCode ?: 1).plus(1).toString()) }
            var newApkUrlInput by remember { mutableStateOf(appUpdateInfo?.apkUrl ?: "") }
            var newReleaseNotesInput by remember { mutableStateOf(appUpdateInfo?.releaseNotes ?: "") }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, if (hasUpdate) WarmGold else ForestGreen.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(if (hasUpdate) WarmGold else ForestGreen, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (hasUpdate) Icons.Default.NewReleases else Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = if (hasUpdate) ForestGreen else WarmGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Uygulama Sürümü",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = ForestGreen
                                )
                                Text(
                                    text = "Kurulu: v$currentVersionName (Yapı $currentVersionCode)",
                                    fontSize = 12.sp,
                                    color = Slate500
                                )
                            }
                        }

                        if (hasUpdate) {
                            Button(
                                onClick = { viewModel.openUpdateDialog() },
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Güncelle ⚡", color = WarmGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            Surface(
                                color = SoftMintGreen,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "🟢 Güncel",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ForestGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (hasUpdate)
                            "Yeni bir sürüm mevcut (v${appUpdateInfo?.latestVersionName}). Tek tıkla kablosuz güncelleyebilirsiniz."
                        else
                            "Uygulamanız en son Sade.C özellikleri ve geliştirmeleriyle güncel durumdadır.",
                        fontSize = 12.sp,
                        color = Slate500,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = ForestGreen.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Admin Sürüm Yayınlama Paneli
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPublishUpdateSectionExpanded = !isPublishUpdateSectionExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPublishUpdateSectionExpanded) "🔼 Güncelleme Panelini Gizle" else "⚙️ Yeni Sürüm Duyur / Güncelleme Yayınla",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SageGreen
                        )
                        Icon(
                            imageVector = if (isPublishUpdateSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = SageGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isPublishUpdateSectionExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newVersionNameInput,
                            onValueChange = { newVersionNameInput = it },
                            label = { Text("Yeni Sürüm Adı (Örn: 1.1.0)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newVersionCodeInput,
                            onValueChange = { newVersionCodeInput = it },
                            label = { Text("Yeni Sürüm Kodu (Örn: 2)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newApkUrlInput,
                            onValueChange = { newApkUrlInput = it },
                            label = { Text("APK İndirme Linki (Direct URL)") },
                            placeholder = { Text("https://.../sadec.apk") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newReleaseNotesInput,
                            onValueChange = { newReleaseNotesInput = it },
                            label = { Text("Sürüm Notları (Neler Yeni?)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val code = newVersionCodeInput.toIntOrNull() ?: (currentVersionCode + 1)
                                val info = AppUpdateInfo(
                                    latestVersionCode = code,
                                    latestVersionName = newVersionNameInput.trim().ifBlank { "1.1.0" },
                                    apkUrl = newApkUrlInput.trim(),
                                    releaseNotes = newReleaseNotesInput.trim()
                                )
                                viewModel.publishAppUpdate(info) {
                                    isPublishUpdateSectionExpanded = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                        ) {
                            Text("Tüm Cihazlara Güncelleme Gönder 🚀", color = WarmGold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 6. GÜVENLİ ÇIKIŞ (SIGN OUT)
            OutlinedButton(
                onClick = {
                    viewModel.signOut()
                    onSignOut()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.4f))
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Oturumu Kapat", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
