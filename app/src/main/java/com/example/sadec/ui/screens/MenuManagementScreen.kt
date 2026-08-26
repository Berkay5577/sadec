package com.example.sadec.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                title = {
                    Column {
                        Text("Menü & Kategoriler", fontWeight = FontWeight.Bold, color = ForestGreen)
                        Text(
                            text = "${categories.size} Kategori • ${menuItems.size} Ürün",
                            fontSize = 12.sp,
                            color = SageGreen
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    categoryNameInput = ""
                    categoryOrderInput = (categories.size + 1).toString()
                    showAddCategoryDialog = true
                },
                containerColor = ForestGreen,
                contentColor = WarmGold
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Kategori Ekle")
                    Text("Yeni Kategori Ekle", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (categories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📂", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Henüz kategori eklenmemiş",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreen
                        )
                        Text(
                            text = "Menünüzü oluşturmak için aşağıdaki 'Yeni Kategori Ekle' butonuna basarak ilk kategorinizi oluşturun.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate500,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = "Kategoriler (Detay ve ürünler için dokunun)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate500,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(categories, key = { it.id }) { cat ->
                        val count = menuItems.count { it.categoryId == cat.id }
                        CategoryListCard(
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
            title = { Text("Yeni Kategori Ekle", fontWeight = FontWeight.Bold, color = ForestGreen) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Menüde görünecek kategori adını girin (Örn: Sıcak Kahveler):", fontSize = 13.sp, color = Slate500)
                    OutlinedTextField(
                        value = categoryNameInput,
                        onValueChange = { categoryNameInput = it },
                        label = { Text("Kategori Adı") },
                        placeholder = { Text("Örn: Soğuk Kahveler") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = categoryOrderInput,
                        onValueChange = { categoryOrderInput = it },
                        label = { Text("Sıra Numarası") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
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
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Text("Kategoriyi Oluştur", color = WarmGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }
}

@Composable
fun CategoryListCard(
    category: Category,
    productCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = SoftMintGreen,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("📁", fontSize = 22.sp)
                    }
                }

                Column {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreen
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$productCount Ürün • Sıra #${category.sortOrder}",
                        fontSize = 13.sp,
                        color = SageGreen
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Detay",
                tint = ForestGreen
            )
        }
    }
}
