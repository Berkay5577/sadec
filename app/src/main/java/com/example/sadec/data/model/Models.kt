package com.example.sadec.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Restaurant(
    @DocumentId val id: String = "",
    val name: String = "",
    val slug: String = "",
    val logoUrl: String = "",
    val phone: String = "",
    val address: String = "",
    val instagram: String = "",
    val themeColor: String = "#2C1A14",
    val createdAt: Long = System.currentTimeMillis()
)

data class Category(
    @DocumentId val id: String = "",
    val name: String = "",
    val sortOrder: Int = 0
)

data class MenuItem(
    @DocumentId val id: String = "",
    val categoryId: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val imageUrl: String = "",
    val allergens: List<String> = emptyList(),
    val isAvailable: Boolean = true,
    val sortOrder: Int = 0
)

data class TableItem(
    @DocumentId val id: String = "",
    val label: String = "",
    val isActive: Boolean = true
)

data class Order(
    @DocumentId val id: String = "",
    val tableId: String = "",
    val tableLabel: String = "",
    val status: String = "pending", // pending, preparing, ready, delivered, cancelled
    val items: List<OrderItem> = emptyList(),
    val totalPrice: Double = 0.0,
    val note: String = "",
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)

data class OrderItem(
    val menuItemId: String = "",
    val name: String = "",
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val note: String = ""
)

data class Staff(
    @DocumentId val id: String = "",
    val name: String = "",
    val role: String = "owner", // owner, waiter, kitchen
    val fcmTokens: List<String> = emptyList()
)
