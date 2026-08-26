package com.example.sadec.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.sadec.data.model.Category
import com.example.sadec.data.model.MenuItem
import com.example.sadec.ui.theme.*
import com.example.sadec.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    category: Category,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onAddProduct: () -> Unit,
    onEditProduct: (MenuItem) -> Unit
) {
    val allCategories by viewModel.categories.collectAsState()
    val currentCategory = allCategories.find { it.id == category.id } ?: category
    val allMenuItems by viewModel.menuItems.collectAsState()
    val categoryItems = remember(allMenuItems, currentCategory.id) {
        allMenuItems.filter { it.categoryId == currentCategory.id }
    }

    var showEditCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<MenuItem?>(null) }
    var selectedCategoryUri by remember { mutableStateOf<Uri?>(null) }

    val categoryImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedCategoryUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = {
                    Column {
                        Text(
                            text = currentCategory.name.ifBlank { "Kategori Detayı" },
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${categoryItems.size} Ürün Mevcut",
                            fontSize = 12.sp,
                            color = WarmGold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        selectedCategoryUri = null
                        showEditCategoryDialog = true
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Kategori Ayarları", tint = Color.White)
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
                onClick = onAddProduct,
                containerColor = ForestGreen,
                contentColor = WarmGold,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = "Ürün Ekle") },
                text = { Text("Bu Kategoriye Ürün Ekle", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 10.dp,
                bottom = 120.dp
            )
        ) {
            // Category Info & Quick Settings Banner (Luxury Sade.C Botanical Card)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Category Image / Thumbnail
                            if (currentCategory.imageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = currentCategory.imageUrl,
                                    contentDescription = currentCategory.name,
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(SoftMintGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("☕", fontSize = 28.sp)
                                }
                            }

                            // Category Text Details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentCategory.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreen,
                                    fontSize = 17.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = SoftMintGreen,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${categoryItems.size} Ürün",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ForestGreen,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "• Sıra #${currentCategory.sortOrder}",
                                        fontSize = 12.sp,
                                        color = Slate500
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Slate100)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Action Buttons (Clean & Spaced)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedCategoryUri = null
                                    showEditCategoryDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestGreen),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Düzenle", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kategoriyi Sil", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Products Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kategori Ürünleri (${categoryItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreen
                    )
                }
            }

            // Products List
            if (categoryItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("☕", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Bu kategoride henüz ürün bulunmuyor",
                                fontWeight = FontWeight.SemiBold,
                                color = ForestGreen
                            )
                            Text(
                                text = "Aşağıdaki butonla hemen yeni bir ürün ekleyebilirsiniz.",
                                fontSize = 13.sp,
                                color = Slate500,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                items(categoryItems, key = { it.id }) { item ->
                    CategoryProductCard(
                        item = item,
                        onEdit = { onEditProduct(item) },
                        onDelete = { itemToDelete = item },
                        onToggleAvailable = { isAvail ->
                            viewModel.toggleMenuItemAvailability(item.id, isAvail)
                        }
                    )
                }
            }
        }
    }

    // Edit Category Dialog (With Direct Gallery Photo Picker & Overwrite)
    if (showEditCategoryDialog) {
        var editName by remember { mutableStateOf(currentCategory.name) }
        var editOrder by remember { mutableStateOf(currentCategory.sortOrder.toString()) }
        var isSavingCat by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSavingCat) showEditCategoryDialog = false },
            title = { Text("Kategori Ayarları", fontWeight = FontWeight.Bold, color = ForestGreen) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Photo Picker Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Slate100)
                            .clickable { categoryImagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedCategoryUri != null) {
                            AsyncImage(
                                model = selectedCategoryUri,
                                contentDescription = "Yeni Kategori Görseli",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else if (currentCategory.imageUrl.isNotBlank()) {
                            AsyncImage(
                                model = currentCategory.imageUrl,
                                contentDescription = "Mevcut Kategori Görseli",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Overlay badge
                        Surface(
                            color = ForestGreen.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = WarmGold, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (selectedCategoryUri != null || currentCategory.imageUrl.isNotBlank()) "Görseli Değiştir (Galeri)" else "Fotoğraf Seç (Galeri)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Kategori Adı") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = editOrder,
                        onValueChange = { editOrder = it },
                        label = { Text("Menü Sıralaması") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            isSavingCat = true
                            val orderInt = editOrder.toIntOrNull() ?: currentCategory.sortOrder
                            viewModel.saveCategory(
                                name = editName.trim(),
                                sortOrder = orderInt,
                                categoryId = currentCategory.id,
                                imageUrl = currentCategory.imageUrl,
                                imageUri = selectedCategoryUri,
                                onComplete = {
                                    isSavingCat = false
                                    showEditCategoryDialog = false
                                }
                            )
                        }
                    },
                    enabled = !isSavingCat && editName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSavingCat) {
                        CircularProgressIndicator(color = WarmGold, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Kaydet", color = WarmGold, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditCategoryDialog = false },
                    enabled = !isSavingCat
                ) {
                    Text("Vazgeç", color = Slate500)
                }
            }
        )
    }

    // Delete Category Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Kategoriyi Sil?", fontWeight = FontWeight.Bold, color = DangerRed) },
            text = {
                Text("Bu kategoriyi silmek istediğinizden emin misiniz? (İçindeki ürünler silinir).")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategory(currentCategory.id)
                        showDeleteConfirmDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Evet, Sil", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Vazgeç", color = Slate500)
                }
            }
        )
    }

    // Delete Product Confirmation Dialog
    itemToDelete?.let { product ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Ürünü Sil?", fontWeight = FontWeight.Bold, color = DangerRed) },
            text = {
                Text("'${product.name}' ürününü menüden tamamen silmek istediğinizden emin misiniz?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMenuItem(product.id)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Evet, Sil", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Vazgeç", color = Slate500)
                }
            }
        )
    }
}

@Composable
fun CategoryProductCard(
    item: MenuItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleAvailable: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Product Thumbnail
                if (item.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.name,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SoftMintGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("☕", fontSize = 28.sp)
                    }
                }

                // Product Details
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreen,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "₺${"%.2f".format(item.price)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = WarmGold
                        )
                    }

                    if (item.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500,
                            maxLines = 2
                        )
                    }

                    // Allergen letter badges in Android UI
                    if (item.allergens.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item.allergens.forEach { allergen ->
                                val letter = when {
                                    allergen.contains("Gluten", true) -> "G"
                                    allergen.contains("Süt", true) || allergen.contains("Laktoz", true) -> "S"
                                    allergen.contains("Yumurta", true) -> "Y"
                                    allergen.contains("Kafein", true) || allergen.contains("Kahve", true) -> "K"
                                    allergen.contains("Fındık", true) || allergen.contains("Fıstık", true) || allergen.contains("Kuruyemiş", true) -> "F"
                                    else -> allergen.take(1).uppercase()
                                }
                                Surface(
                                    color = WarmGold.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "[$letter] $allergen",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SageGreen,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Slate100)
            Spacer(modifier = Modifier.height(8.dp))

            // Action Row: Stock Switch & Edit / Delete Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = item.isAvailable,
                        onCheckedChange = onToggleAvailable,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ForestGreen
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (item.isAvailable) "Satışta ✅" else "Tükendi ❌",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isAvailable) ForestGreen else DangerRed
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Düzenle", tint = ForestGreen)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Sil", tint = DangerRed)
                    }
                }
            }
        }
    }
}
