package com.example.sadec.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
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
    val themeColor: String = "#1E3A2F",
    val webMenuUrl: String = "https://sadec.vercel.app", // Canlı Vercel QR Menü Alan Adı
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val enforceGeoFence: Boolean = false,
    val popupCampaign: PopupCampaign? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class PopupCampaign(
    @get:PropertyName("isActive") @set:PropertyName("isActive")
    var isActive: Boolean = false,
    val badge: String = "DENEDİNİZ Mİ? 🌟",
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val priceText: String = "",
    val buttonText: String = "Hemen İncele ✨",
    val targetMenuItemId: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class Category(
    @DocumentId val id: String = "",
    val name: String = "",
    val sortOrder: Int = 0,
    val imageUrl: String = ""
)

data class MenuItem(
    @DocumentId val id: String = "",
    val categoryId: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val imageUrl: String = "",
    val allergens: List<String> = emptyList(),
    @get:PropertyName("isAvailable") @set:PropertyName("isAvailable")
    var isAvailable: Boolean = true,
    val sortOrder: Int = 0
)

data class TableItem(
    @DocumentId val id: String = "",
    val label: String = "",
    @get:PropertyName("isActive") @set:PropertyName("isActive")
    var isActive: Boolean = true,
    val qrKey: String = "" // Güvenlik token'ı (QR kodun paylaşılmasını engeller)
)

data class Order(
    @DocumentId val id: String = "",
    val tableId: String = "",
    val tableLabel: String = "",
    val customerName: String = "", // Müşterinin girdiği isim
    val status: String = "pending", // pending, preparing, ready, delivered, cancelled
    val items: List<OrderItem> = emptyList(),
    val totalPrice: Double = 0.0,
    val note: String = "",
    val cancelReason: String = "", // İptal gerekçesi
    @get:PropertyName("isArchived") @set:PropertyName("isArchived")
    var isArchived: Boolean = false, // Haftalık sıfırlama/arşivleme için
    val weekPeriod: String = "",     // Örn: "2026-W35"
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
) {
    fun isFullyPaid(): Boolean = items.isNotEmpty() && items.all { it.isPaid }
    fun remainingAmount(): Double = items.filter { !it.isPaid }.sumOf { it.unitPrice * it.quantity }
    fun paidAmount(): Double = items.filter { it.isPaid }.sumOf { it.unitPrice * it.quantity }
}

data class OrderItem(
    val menuItemId: String = "",
    val name: String = "",
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val note: String = "",
    @get:PropertyName("isPaid") @set:PropertyName("isPaid")
    var isPaid: Boolean = false,
    val paidAt: Long? = null
)

data class Staff(
    @DocumentId val id: String = "",
    val name: String = "",
    val role: String = "owner",
    val fcmTokens: List<String> = emptyList()
)
