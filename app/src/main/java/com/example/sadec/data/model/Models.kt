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
    val appUpdateInfo: AppUpdateInfo? = null,
    val managerPin: String = "1923", // Yönetici Dashboard & Finans PIN Kodu
    val createdAt: Long = System.currentTimeMillis()
)

data class AppUpdateInfo(
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "1.0.0",
    val apkUrl: String = "",
    val releaseNotes: String = "",
    @get:PropertyName("isMandatory") @set:PropertyName("isMandatory")
    var isMandatory: Boolean = false,
    val publishDate: String = ""
)

data class PopupCampaign(
    @get:PropertyName("isActive") @set:PropertyName("isActive") @field:PropertyName("isActive")
    var isActive: Boolean = false,
    val badge: String = "DENEDİNİZ Mİ? 🌟",
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val priceText: String = "",
    val buttonText: String = "Hemen Keşfet ✨",
    val targetMenuItemId: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "isActive" to isActive,
        "active" to isActive,
        "badge" to badge,
        "title" to title,
        "description" to description,
        "imageUrl" to imageUrl,
        "priceText" to priceText,
        "buttonText" to buttonText,
        "targetMenuItemId" to targetMenuItemId,
        "updatedAt" to updatedAt
    )
}

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
    val paymentMethod: String = "", // cash, card, transfer, complimentary, mix
    val discountAmount: Double = 0.0,
    @get:PropertyName("isComplimentary") @set:PropertyName("isComplimentary")
    var isComplimentary: Boolean = false,
    @get:PropertyName("isDayClosed") @set:PropertyName("isDayClosed")
    var isDayClosed: Boolean = false, // Gün Sonu Z-Raporu kapanışı için
    val closedDayDate: String = "",   // Örn: "2026-08-27"
    @get:PropertyName("isArchived") @set:PropertyName("isArchived")
    var isArchived: Boolean = false, // Haftalık sıfırlama/arşivleme için
    val weekPeriod: String = "",     // Örn: "2026-W35"
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
) {
    fun isFullyPaid(): Boolean = items.isEmpty() || items.all { it.isPaid || it.isComplimentary } || remainingAmount() <= 0.001
    fun remainingAmount(): Double = items.filter { !it.isPaid && !it.isComplimentary }.sumOf { it.effectivePrice() }
    fun paidAmount(): Double = items.filter { it.isPaid }.sumOf { it.effectivePrice() }
    fun cashPaidAmount(): Double = items.filter { it.isPaid }.sumOf { item ->
        if (item.cashPaid > 0.0 || item.cardPaid > 0.0 || item.transferPaid > 0.0) item.cashPaid
        else if (item.paymentMethod == "cash" || (item.paymentMethod.isEmpty() && paymentMethod == "cash")) item.effectivePrice()
        else 0.0
    }
    fun cardPaidAmount(): Double = items.filter { it.isPaid }.sumOf { item ->
        if (item.cashPaid > 0.0 || item.cardPaid > 0.0 || item.transferPaid > 0.0) item.cardPaid
        else if (item.paymentMethod == "card" || (item.paymentMethod.isEmpty() && (paymentMethod == "card" || paymentMethod.isEmpty()))) item.effectivePrice()
        else 0.0
    }
    fun transferPaidAmount(): Double = items.filter { it.isPaid }.sumOf { item ->
        if (item.cashPaid > 0.0 || item.cardPaid > 0.0 || item.transferPaid > 0.0) item.transferPaid
        else if (item.paymentMethod == "transfer" || (item.paymentMethod.isEmpty() && paymentMethod == "transfer")) item.effectivePrice()
        else 0.0
    }
    fun complimentaryAmount(): Double = items.filter { it.isComplimentary }.sumOf { it.unitPrice * it.quantity } + if (isComplimentary) totalPrice else 0.0
}

data class SplitPaymentBreakdown(
    val cashAmount: Double = 0.0,
    val cardAmount: Double = 0.0,
    val transferAmount: Double = 0.0
) {
    val total: Double get() = cashAmount + cardAmount + transferAmount
    fun isSplit(): Boolean = listOf(cashAmount > 0, cardAmount > 0, transferAmount > 0).count { it } > 1
}

data class OrderItem(
    val menuItemId: String = "",
    val name: String = "",
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val note: String = "",
    val paymentMethod: String = "", // cash, card, transfer, complimentary, split
    val discountAmount: Double = 0.0,
    @get:PropertyName("isComplimentary") @set:PropertyName("isComplimentary")
    var isComplimentary: Boolean = false,
    @get:PropertyName("isPaid") @set:PropertyName("isPaid")
    var isPaid: Boolean = false,
    val paidAt: Long? = null,
    val cashPaid: Double = 0.0,
    val cardPaid: Double = 0.0,
    val transferPaid: Double = 0.0
) {
    fun effectivePrice(): Double = if (isComplimentary) 0.0 else maxOf(0.0, (unitPrice * quantity) - discountAmount)
}

data class SelectedItemRef(
    val orderId: String = "",
    val itemIndex: Int = 0,
    val itemName: String = "",
    val amount: Double = 0.0
)

fun List<OrderItem>.unbundled(): List<OrderItem> {
    return this.flatMap { item ->
        if (item.quantity > 1) {
            List(item.quantity) {
                item.copy(
                    quantity = 1,
                    discountAmount = if (item.discountAmount > 0) item.discountAmount / item.quantity else 0.0
                )
            }
        } else {
            listOf(item)
        }
    }
}

data class Staff(
    @DocumentId val id: String = "",
    val name: String = "",
    val role: String = "owner",
    val fcmTokens: List<String> = emptyList()
)
