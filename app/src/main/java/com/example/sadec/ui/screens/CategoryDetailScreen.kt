package com.example.sadec.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentCategory.name.ifBlank { "Kategori Detayı" },
                            fontWeight = FontWeight.Bold,
                            color = ForestGreen
                        )
                        Text(
                            text = "${categoryItems.size} Ürün Bulunuyor",
                            fontSize = 12.sp,
                            color = SageGreen
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = ForestGreen)
                    }
                },
                actions = {
                    IconButton(onClick = { showEditCategoryDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Kategori Ayarları", tint = ForestGreen)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddProduct,
                containerColor = ForestGreen,
                contentColor = WarmGold
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ürün Ekle")
                    Text("Bu Kategoriye Ürün Ekle", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Category Info & Quick Settings Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftMintGreen)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "📁 ${currentCategory.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreen
                                )
                                Text(
                                    text = "Menü Sıralaması: #${currentCategory.sortOrder}",
                                    fontSize = 13.sp,
                                    color = SageGreen
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(
                                    onClick = { showEditCategoryDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestGreen)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Düzenle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { showDeleteConfirmDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sil", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
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

            // Spacer for FAB bottom padding
            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }

    // Edit Category Dialog
    if (showEditCategoryDialog) {
        var editName by remember { mutableStateOf(currentCategory.name) }
        var editOrder by remember { mutableStateOf(currentCategory.sortOrder.toString()) }
        var editImageUrl by remember { mutableStateOf(currentCategory.imageUrl) }

        AlertDialog(
            onDismissRequest = { showEditCategoryDialog = false },
            title = { Text("Kategori Ayarları", fontWeight = FontWeight.Bold, color = ForestGreen) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Kategori Adı") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = editOrder,
                        onValueChange = { editOrder = it },
                        label = { Text("Sıra Numarası") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = editImageUrl,
                        onValueChange = { editImageUrl = it },
                        label = { Text("Kategori Görseli (URL veya dosya adı)") },
                        placeholder = { Text("images/cat_hot.jpg") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            val orderInt = editOrder.toIntOrNull() ?: currentCategory.sortOrder
                            viewModel.saveCategory(
                                name = editName.trim(),
                                sortOrder = orderInt,
                                categoryId = currentCategory.id,
                                imageUrl = editImageUrl.trim()
                            )
                            showEditCategoryDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Text("Kaydet", color = WarmGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditCategoryDialog = false }) {
                    Text("Vazgeç")
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
                Text("Bu kategoriyi silmek istediğinizden emin misiniz? (İçindeki ürünler kategorisiz kalabilir veya silinebilir).")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategory(currentCategory.id)
                        showDeleteConfirmDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Evet, Sil", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Vazgeç")
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
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Evet, Sil", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Vazgeç")
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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

                    if (item.allergens.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Alerjen: ${item.allergens.joinToString(", ")}",
                            fontSize = 11.sp,
                            color = WarningYellow
                        )
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
