package com.example.sadec.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.sadec.data.model.MenuItem
import com.example.sadec.ui.theme.*
import com.example.sadec.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductScreen(
    editingItem: MenuItem?,
    defaultCategoryId: String = "",
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()

    var name by remember { mutableStateOf(editingItem?.name ?: "") }
    var description by remember { mutableStateOf(editingItem?.description ?: "") }
    var priceStr by remember { mutableStateOf(editingItem?.let { "%.2f".format(it.price) } ?: "") }
    var selectedCategoryId by remember {
        mutableStateOf(
            editingItem?.categoryId
                ?: defaultCategoryId.ifBlank { categories.firstOrNull()?.id ?: "" }
        )
    }
    var imageUrl by remember { mutableStateOf(editingItem?.imageUrl ?: "") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var allergensStr by remember { mutableStateOf(editingItem?.allergens?.joinToString(", ") ?: "") }
    var isAvailable by remember { mutableStateOf(editingItem?.isAvailable ?: true) }
    var isSaving by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    val selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name ?: "Kategori Seçin"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editingItem != null) "Ürünü Düzenle" else "Yeni Ürün Ekle", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ForestGreen,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Image Picker Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Slate100)
                    .clickable { photoPickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Seçilen Fotoğraf",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Ürün Fotoğrafı",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = Slate500
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Fotoğraf Seçmek İçin Dokunun", fontSize = 13.sp, color = Slate500)
                    }
                }
            }

            // Name Field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Ürün Adı (Örn: Truffle Burger)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Price Field
            OutlinedTextField(
                value = priceStr,
                onValueChange = { priceStr = it },
                label = { Text("Fiyat (₺)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Category Dropdown
            ExposedDropdownMenuBox(
                expanded = categoryDropdownExpanded,
                onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCategoryName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Kategori") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = categoryDropdownExpanded,
                    onDismissRequest = { categoryDropdownExpanded = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = {
                                selectedCategoryId = cat.id
                                categoryDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Description Field
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Açıklama / İçindekiler") },
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Allergens Field
            OutlinedTextField(
                value = allergensStr,
                onValueChange = { allergensStr = it },
                label = { Text("Alerjenler (Virgülle ayırın: Gluten, Süt, Fıstık)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Direct Image URL Field (Optional fallback)
            OutlinedTextField(
                value = imageUrl,
                onValueChange = { imageUrl = it },
                label = { Text("Görsel URL Bağlantısı (İsteğe bağlı)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Availability Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Menüde Satışa Açık (Stokta Var)", fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = isAvailable,
                    onCheckedChange = { isAvailable = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ForestGreen
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Save Button
            Button(
                onClick = {
                    val price = priceStr.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val allergenList = allergensStr.split(",").map { it.trim() }.filter { it.isNotBlank() }

                    val itemToSave = (editingItem ?: MenuItem()).copy(
                        name = name.trim(),
                        description = description.trim(),
                        price = price,
                        categoryId = selectedCategoryId,
                        imageUrl = imageUrl.trim(),
                        allergens = allergenList,
                        isAvailable = isAvailable
                    )

                    isSaving = true
                    viewModel.saveMenuItem(itemToSave, selectedImageUri) {
                        isSaving = false
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                enabled = !isSaving && name.isNotBlank()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kaydet ve Menüye Ekle", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
