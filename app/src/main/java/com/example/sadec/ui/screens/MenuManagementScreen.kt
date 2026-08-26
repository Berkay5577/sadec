package com.example.sadec.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.sadec.data.model.Category
import com.example.sadec.ui.theme.*
import com.example.sadec.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuManagementScreen(
    viewModel: MainViewModel,
    onCategoryClick: (Category) -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryNameInput by remember { mutableStateOf("") }
    var categoryOrderInput by remember { mutableStateOf((categories.size + 1).toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = {
                    Column {
                        Text(
                            text = "Menü & Kategoriler",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (categories.isEmpty()) "Menü henüz boş" else "${categories.size} Kategori • ${menuItems.size} Ürün",
                            fontSize = 12.sp,
                            color = WarmGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ForestGreen,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    categoryNameInput = ""
                    categoryOrderInput = (categories.size + 1).toString()
                    showAddCategoryDialog = true
                },
                containerColor = ForestGreen,
                contentColor = WarmGold,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = "Ekle") },
                text = { Text("Yeni Kategori Ekle", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (categories.isEmpty()) {
                // Elegant Minimal Empty State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(SoftMintGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestaurantMenu,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = ForestGreen
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Menünüz Henüz Boş",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreen
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Menünüzü oluşturmak için ilk kategorinizi (örn: Sıcak Kahveler, Tatlılar) ekleyerek başlayın.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate500,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        Button(
                            onClick = {
                                categoryNameInput = ""
                                categoryOrderInput = "1"
                                showAddCategoryDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = WarmGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("İlk Kategorinizi Ekleyin", fontWeight = FontWeight.Bold, color = WarmGold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 110.dp)
                ) {
                    item {
                        Text(
                            text = "KATEGORİLER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Slate500,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }

                    items(categories, key = { it.id }) { cat ->
                        val count = menuItems.count { it.categoryId == cat.id }
                        PremiumCategoryCard(
                            category = cat,
                            productCount = count,
                            onClick = { onCategoryClick(cat) }
                        )
                    }
                }
            }
        }
    }

    // Add New Category Dialog
    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = {
                Text(
                    text = "Yeni Kategori Oluştur",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = ForestGreen
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Menüde müşterilerin göreceği kategori ismini ve sıra numarasını belirleyin:",
                        fontSize = 13.sp,
                        color = Slate500
                    )
                    OutlinedTextField(
                        value = categoryNameInput,
                        onValueChange = { categoryNameInput = it },
                        label = { Text("Kategori Adı") },
                        placeholder = { Text("Örn: Sıcak Kahveler") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = categoryOrderInput,
                        onValueChange = { categoryOrderInput = it },
                        label = { Text("Sıra Numarası") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (categoryNameInput.isNotBlank()) {
                            val order = categoryOrderInput.toIntOrNull() ?: (categories.size + 1)
                            viewModel.saveCategory(categoryNameInput.trim(), order)
                            showAddCategoryDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Oluştur", color = WarmGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Vazgeç", color = Slate500)
                }
            }
        )
    }
}

@Composable
fun PremiumCategoryCard(
    category: Category,
    productCount: Int,
    onClick: () -> Unit
) {
    // Dynamic icon based on category name
    val lowerName = category.name.lowercase()
    val (iconSymbol, iconBg) = when {
        lowerName.contains("sıcak") || lowerName.contains("kahve") || lowerName.contains("coffee") -> "☕" to SoftMintGreen
        lowerName.contains("soğuk") || lowerName.contains("içecek") || lowerName.contains("ice") -> "🧊" to Color(0xFFE0F2FE)
        lowerName.contains("tatlı") || lowerName.contains("pasta") || lowerName.contains("kek") -> "🍰" to Color(0xFFFEF3C7)
        lowerName.contains("sandviç") || lowerName.contains("tost") || lowerName.contains("spesiyal") -> "🥪" to Color(0xFFFEE2E2)
        lowerName.contains("atıştırmalık") || lowerName.contains("börek") || lowerName.contains("poğaça") -> "🥐" to Color(0xFFEDE9FE)
        else -> "🍽️" to SoftMintGreen
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Category Icon or Image Badge
                if (category.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = category.imageUrl,
                        contentDescription = category.name,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = iconSymbol, fontSize = 22.sp)
                    }
                }

                // Category Info
                Column {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreen,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = SoftMintGreen,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "$productCount Ürün",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "• Sıra #${category.sortOrder}",
                            fontSize = 12.sp,
                            color = Slate500
                        )
                    }
                }
            }

            // Arrow Action
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SoftMintGreen.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Detay",
                    modifier = Modifier.size(13.dp),
                    tint = ForestGreen
                )
            }
        }
    }
}
