package com.example.sadec.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
fun MenuManagementScreen(
    viewModel: MainViewModel,
    onAddProduct: () -> Unit,
    onEditProduct: (MenuItem) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val categories by viewModel.categories.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var categoryNameInput by remember { mutableStateOf("") }
    var categoryOrderInput by remember { mutableStateOf("1") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Menü Yönetimi", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.seedSampleMenu() }) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "Örnek Menü Yükle", tint = OrangePrimary)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) {
                        onAddProduct()
                    } else {
                        editingCategory = null
                        categoryNameInput = ""
                        categoryOrderInput = (categories.size + 1).toString()
                        showAddCategoryDialog = true
                    }
                },
                containerColor = OrangePrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ekle")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Ürünler (${menuItems.size})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Kategoriler (${categories.size})", fontWeight = FontWeight.Bold) }
                )
            }

            if (selectedTab == 0) {
                // PRODUCTS TAB
                if (menuItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Henüz ürün eklenmemiş.", color = Slate500)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(menuItems, key = { it.id }) { item ->
                            val categoryName = categories.find { it.id == item.categoryId }?.name ?: "Genel"
                            ProductManagementCard(
                                item = item,
                                categoryName = categoryName,
                                onEdit = { onEditProduct(item) },
                                onDelete = { viewModel.deleteMenuItem(item.id) },
                                onToggleAvailable = { isAvail ->
                                    viewModel.toggleMenuItemAvailability(item.id, isAvail)
                                }
                            )
                        }
                    }
                }
            } else {
                // CATEGORIES TAB
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(categories, key = { it.id }) { cat ->
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
                                Column {
                                    Text(cat.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Sıra No: ${cat.sortOrder}", fontSize = 12.sp, color = Slate500)
                                }
                                Row {
                                    IconButton(onClick = {
                                        editingCategory = cat
                                        categoryNameInput = cat.name
                                        categoryOrderInput = cat.sortOrder.toString()
                                        showAddCategoryDialog = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Düzenle", tint = InfoBlue)
                                    }
                                    IconButton(onClick = { viewModel.deleteCategory(cat.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Sil", tint = DangerRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Category Dialog
    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text(if (editingCategory != null) "Kategoriyi Düzenle" else "Yeni Kategori Ekle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = categoryNameInput,
                        onValueChange = { categoryNameInput = it },
                        label = { Text("Kategori Adı (Örn: Pizzalar)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = categoryOrderInput,
                        onValueChange = { categoryOrderInput = it },
                        label = { Text("Sıralama (1, 2, 3...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val order = categoryOrderInput.toIntOrNull() ?: 1
                        viewModel.saveCategory(categoryNameInput.trim(), order, editingCategory?.id ?: "")
                        showAddCategoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
fun ProductManagementCard(
    item: MenuItem,
    categoryName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleAvailable: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Product Image (Async with Coil)
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Slate100)
            ) {
                if (item.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Fastfood,
                        contentDescription = null,
                        tint = Slate500,
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                    color = Slate100,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        text = categoryName,
                        fontSize = 11.sp,
                        color = Slate700,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "₺${"%.2f".format(item.price)}",
                    fontWeight = FontWeight.Bold,
                    color = OrangePrimary,
                    fontSize = 14.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (item.isAvailable) "Stokta" else "Tükendi",
                        fontSize = 11.sp,
                        color = if (item.isAvailable) SuccessGreen else DangerRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = item.isAvailable,
                        onCheckedChange = onToggleAvailable,
                        colors = SwitchDefaults.colors(checkedThumbColor = OrangePrimary)
                    )
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Düzenle", tint = InfoBlue, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Sil", tint = DangerRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
