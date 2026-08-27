package com.example.sadec.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sadec.data.model.*
import com.example.sadec.data.repository.AuthRepository
import com.example.sadec.data.repository.FirestoreRepository
import com.example.sadec.data.repository.StorageRepository
import com.example.sadec.util.SoundPlayer
import com.google.firebase.messaging.FirebaseMessaging
import java.util.Date
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val authRepository: AuthRepository = AuthRepository(),
    private val firestoreRepository: FirestoreRepository = FirestoreRepository(),
    private val storageRepository: StorageRepository = StorageRepository()
) : AndroidViewModel(application) {

    private val _restaurantId = MutableStateFlow("sadec-gerze")
    val restaurantId: StateFlow<String> = _restaurantId.asStateFlow()

    private val _restaurant = MutableStateFlow<Restaurant?>(null)
    val restaurant: StateFlow<Restaurant?> = _restaurant.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders.asStateFlow()

    private val _selectedStatusFilter = MutableStateFlow("all")
    val selectedStatusFilter: StateFlow<String> = _selectedStatusFilter.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val menuItems: StateFlow<List<MenuItem>> = _menuItems.asStateFlow()

    private val _tables = MutableStateFlow<List<TableItem>>(emptyList())
    val tables: StateFlow<List<TableItem>> = _tables.asStateFlow()

    private val prefs = application.getSharedPreferences("sadec_weekly_prefs", android.content.Context.MODE_PRIVATE)

    private val _isWeeklyLockActive = MutableStateFlow(false)
    val isWeeklyLockActive: StateFlow<Boolean> = _isWeeklyLockActive.asStateFlow()

    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    private var previousOrderIds = setOf<String>()
    private var isFirstOrdersLoad = true

    init {
        try {
            checkUserSession()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkUserSession() {
        try {
            val user = authRepository.currentUser
            _isLoggedIn.value = user != null
            if (user != null) {
                initDataListeners()
                fetchAndRegisterFcmToken()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _isLoggedIn.value = false
        }
    }

    fun setRestaurantId(id: String) {
        if (id.isNotBlank() && id != _restaurantId.value) {
            _restaurantId.value = id
            initDataListeners()
        }
    }

    fun setStatusFilter(filter: String) {
        _selectedStatusFilter.value = filter
    }

    private fun initDataListeners() {
        val restId = _restaurantId.value

        // Load / Ensure Restaurant Doc
        viewModelScope.launch {
            try {
                _restaurant.value = firestoreRepository.getOrCreateRestaurant(restId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Listen Orders (Realtime)
        viewModelScope.launch {
            firestoreRepository.listenOrders(restId)
                .catch { e -> _uiMessage.emit("Siparişler yüklenemedi: ${e.message}") }
                .collect { orderList ->
                    val currentPendingIds = orderList.filter { it.status == "pending" }.map { it.id }.toSet()
                    if (!isFirstOrdersLoad) {
                        val newArrivals = currentPendingIds - previousOrderIds
                        if (newArrivals.isNotEmpty()) {
                            SoundPlayer.playOrderAlert(getApplication())
                            _uiMessage.emit("🔔 Yeni sipariş geldi!")
                        }
                    }
                    previousOrderIds = currentPendingIds
                    isFirstOrdersLoad = false

                    _orders.value = orderList
                    checkWeeklyPeriodStatus()
                }
        }

        // Listen Categories (Realtime)
        viewModelScope.launch {
            firestoreRepository.listenCategories(restId)
                .catch { e -> _uiMessage.emit("Kategoriler yüklenemedi: ${e.message}") }
                .collect { _categories.value = it }
        }

        // Listen Menu Items (Realtime)
        viewModelScope.launch {
            firestoreRepository.listenMenuItems(restId)
                .catch { e -> _uiMessage.emit("Menü yüklenemedi: ${e.message}") }
                .collect { _menuItems.value = it }
        }

        // Listen Tables (Realtime)
        viewModelScope.launch {
            firestoreRepository.listenTables(restId)
                .catch { e -> _uiMessage.emit("Masalar yüklenemedi: ${e.message}") }
                .collect { _tables.value = it }
        }
    }

    // --- AUTH ACTIONS ---
    fun signIn(email: String, pass: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val res = authRepository.signIn(email, pass)
            res.onSuccess {
                _isLoggedIn.value = true
                initDataListeners()
                fetchAndRegisterFcmToken()
                _uiMessage.emit("Giriş başarılı!")
                onSuccess()
            }.onFailure {
                _uiMessage.emit("Giriş hatası: ${it.localizedMessage}")
            }
        }
    }

    fun signUp(email: String, pass: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val res = authRepository.signUp(email, pass)
            res.onSuccess {
                _isLoggedIn.value = true
                initDataListeners()
                fetchAndRegisterFcmToken()
                _uiMessage.emit("Kayıt başarılı!")
                onSuccess()
            }.onFailure {
                _uiMessage.emit("Kayıt hatası: ${it.localizedMessage}")
            }
        }
    }

    fun enterDemoMode(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val res = authRepository.signInAnonymously()
            res.onSuccess {
                _isLoggedIn.value = true
                initDataListeners()
                fetchAndRegisterFcmToken()
                _uiMessage.emit("Demo moduna girildi.")
                onSuccess()
            }.onFailure {
                _isLoggedIn.value = true
                initDataListeners()
                _uiMessage.emit("Demo moduna girildi.")
                onSuccess()
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
        _isLoggedIn.value = false
    }

    // --- ORDER ACTIONS ---
    fun updateOrderStatus(orderId: String, newStatus: String) {
        viewModelScope.launch {
            val res = firestoreRepository.updateOrderStatus(_restaurantId.value, orderId, newStatus)
            res.onSuccess {
                if (newStatus == "delivered") {
                    _uiMessage.emit("Sipariş teslim edildi ve arşive aktarıldı! ✅")
                }
            }.onFailure {
                _uiMessage.emit("Durum güncellenemedi: ${it.localizedMessage}")
            }
        }
    }

    fun cancelOrder(orderId: String, reason: String) {
        viewModelScope.launch {
            val res = firestoreRepository.cancelOrder(_restaurantId.value, orderId, reason)
            res.onSuccess {
                _uiMessage.emit("Sipariş iptal edildi ❌")
            }.onFailure {
                _uiMessage.emit("İptal işlemi başarısız: ${it.localizedMessage}")
            }
        }
    }

    fun payOrderItem(orderId: String, itemIndex: Int, paymentMethod: String = "card") {
        val methodLabel = when (paymentMethod) {
            "cash" -> "Nakit 💵"
            "card" -> "Kredi Kartı 💳"
            "transfer" -> "Havale/EFT 📲"
            "complimentary" -> "İkram 🎁"
            else -> "Ödeme 💳"
        }
        viewModelScope.launch {
            val res = firestoreRepository.payOrderItem(_restaurantId.value, orderId, itemIndex, true, paymentMethod)
            res.onSuccess {
                _uiMessage.emit("Ürün ödemesi ($methodLabel) alındı! ✅")
            }.onFailure {
                _uiMessage.emit("Ödeme işlenemedi: ${it.localizedMessage}")
            }
        }
    }

    fun payFullOrder(orderId: String, paymentMethod: String = "card") {
        val methodLabel = when (paymentMethod) {
            "cash" -> "Nakit 💵"
            "card" -> "Kredi Kartı 💳"
            "transfer" -> "Havale/EFT 📲"
            "complimentary" -> "İkram 🎁"
            else -> "Ödeme 💳"
        }
        viewModelScope.launch {
            val res = firestoreRepository.payFullOrder(_restaurantId.value, orderId, paymentMethod)
            res.onSuccess {
                _uiMessage.emit("Hesap ($methodLabel) tahsil edildi ve kapatıldı! ✨")
            }.onFailure {
                _uiMessage.emit("Hesap kapatılamadı: ${it.localizedMessage}")
            }
        }
    }

    fun payEntireTable(tableId: String, paymentMethod: String = "card") {
        viewModelScope.launch {
            val tableOrders = _orders.value.filter { it.tableId == tableId && !it.isArchived && !it.isFullyPaid() }
            for (order in tableOrders) {
                firestoreRepository.payFullOrder(_restaurantId.value, order.id, paymentMethod)
            }
            _uiMessage.emit("Tüm masa ödemesi başarıyla alındı! 💳✅")
        }
    }

    fun transferEntireTable(fromTableId: String, toTableId: String, toTableLabel: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val res = firestoreRepository.transferEntireTable(_restaurantId.value, fromTableId, toTableId, toTableLabel)
            res.onSuccess {
                _uiMessage.emit("Masa siparişleri $toTableLabel masasına aktarıldı! 🔄✅")
                onComplete()
            }.onFailure {
                _uiMessage.emit("Masa taşınamadı: ${it.localizedMessage}")
            }
        }
    }

    fun transferSingleOrder(orderId: String, toTableId: String, toTableLabel: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val res = firestoreRepository.transferSingleOrder(_restaurantId.value, orderId, toTableId, toTableLabel)
            res.onSuccess {
                _uiMessage.emit("Kişi siparişi $toTableLabel masasına aktarıldı! 🔄✅")
                onComplete()
            }.onFailure {
                _uiMessage.emit("Aktarım yapılamadı: ${it.localizedMessage}")
            }
        }
    }

    fun setItemDiscountOrComplimentary(orderId: String, itemIndex: Int, isComplimentary: Boolean, discountAmount: Double = 0.0) {
        viewModelScope.launch {
            val res = firestoreRepository.setItemDiscountOrComplimentary(_restaurantId.value, orderId, itemIndex, isComplimentary, discountAmount)
            res.onSuccess {
                if (isComplimentary) {
                    _uiMessage.emit("Ürün İkram 🎁 olarak işaretlendi!")
                } else if (discountAmount > 0) {
                    _uiMessage.emit("₺${"%.2f".format(discountAmount)} indirim uygulandı! 🏷️")
                } else {
                    _uiMessage.emit("İndirim/İkram kaldırıldı.")
                }
            }.onFailure {
                _uiMessage.emit("İşlem uygulanamadı: ${it.localizedMessage}")
            }
        }
    }

    fun checkWeeklyPeriodStatus() {
        val cal = java.util.Calendar.getInstance()
        val currentYear = cal.get(java.util.Calendar.YEAR)
        val currentWeek = cal.get(java.util.Calendar.WEEK_OF_YEAR)
        val currentPeriod = "$currentYear-W$currentWeek"

        // Check if there are any unarchived orders created in a previous week (older than current week)
        val hasOrdersFromPreviousWeek = _orders.value.any { order ->
            if (order.isArchived || order.status == "cancelled") return@any false
            val orderDate = order.createdAt ?: return@any false
            val orderCal = java.util.Calendar.getInstance().apply { time = orderDate }
            val orderPeriod = "${orderCal.get(java.util.Calendar.YEAR)}-W${orderCal.get(java.util.Calendar.WEEK_OF_YEAR)}"
            orderPeriod != currentPeriod
        }

        if (hasOrdersFromPreviousWeek) {
            _isWeeklyLockActive.value = true
        }
    }

    fun dismissWeeklyLock() {
        _isWeeklyLockActive.value = false
    }

    fun archiveWeeklyOrders(weekPeriod: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val unarchivedOrders = _orders.value.filter { !it.isArchived }.map { it.id }
            if (unarchivedOrders.isEmpty()) {
                _uiMessage.emit("Arşivlenecek sipariş bulunmuyor.")
                onComplete()
                return@launch
            }
            val res = firestoreRepository.archiveOrders(_restaurantId.value, unarchivedOrders, weekPeriod)
            res.onSuccess {
                val cal = java.util.Calendar.getInstance()
                val currentYear = cal.get(java.util.Calendar.YEAR)
                val currentWeek = cal.get(java.util.Calendar.WEEK_OF_YEAR)
                val currentPeriod = "$currentYear-W$currentWeek"
                prefs.edit().putString("last_closed_week_period", currentPeriod).apply()
                _isWeeklyLockActive.value = false
                _uiMessage.emit("Haftalık kasa başarıyla sıfırlandı ve arşivlendi! 📊✅")
                onComplete()
            }.onFailure {
                _uiMessage.emit("Hafta sıfırlama hatası: ${it.localizedMessage}")
            }
        }
    }

    fun createManualOrder(order: Order) {
        viewModelScope.launch {
            val res = firestoreRepository.createOrder(_restaurantId.value, order)
            res.onSuccess {
                _uiMessage.emit("Manuel satış kaydedildi ve kasaya işlendi! 💳✅")
            }.onFailure {
                _uiMessage.emit("Satış kaydedilemedi: ${it.localizedMessage}")
            }
        }
    }

    // --- CATEGORY ACTIONS ---
    fun saveCategory(
        name: String,
        sortOrder: Int,
        categoryId: String = "",
        imageUrl: String = "",
        imageUri: Uri? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            var finalImageUrl = imageUrl
            if (imageUri != null) {
                val uploadRes = storageRepository.uploadCategoryImage(
                    restaurantId = _restaurantId.value,
                    categoryId = categoryId,
                    imageUri = imageUri,
                    oldImageUrl = imageUrl
                )
                uploadRes.onSuccess { url -> finalImageUrl = url }
                    .onFailure { _uiMessage.emit("Görsel yüklenemedi: ${it.localizedMessage}") }
            }
            val cat = Category(id = categoryId, name = name, sortOrder = sortOrder, imageUrl = finalImageUrl)
            val res = firestoreRepository.saveCategory(_restaurantId.value, cat)
            res.onSuccess {
                _uiMessage.emit("Kategori kaydedildi.")
                onComplete()
            }.onFailure { _uiMessage.emit("Hata: ${it.localizedMessage}") }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            val oldCat = _categories.value.find { it.id == categoryId }
            val res = firestoreRepository.deleteCategory(_restaurantId.value, categoryId)
            res.onSuccess { _uiMessage.emit("Kategori silindi.") }
                .onFailure { _uiMessage.emit("Hata: ${it.localizedMessage}") }
        }
    }

    // --- MENU ITEM ACTIONS ---
    fun saveMenuItem(item: MenuItem, imageUri: Uri? = null, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            var finalItem = item
            if (imageUri != null) {
                val uploadRes = storageRepository.uploadProductImage(
                    restaurantId = _restaurantId.value,
                    imageUri = imageUri,
                    oldImageUrl = item.imageUrl
                )
                uploadRes.onSuccess { url ->
                    finalItem = finalItem.copy(imageUrl = url)
                }.onFailure {
                    _uiMessage.emit("Ürün görseli yüklenemedi: ${it.localizedMessage}")
                }
            }

            val res = firestoreRepository.saveMenuItem(_restaurantId.value, finalItem)
            res.onSuccess {
                _uiMessage.emit("Ürün kaydedildi.")
                onComplete()
            }.onFailure {
                _uiMessage.emit("Hata: ${it.localizedMessage}")
            }
        }
    }

    fun toggleMenuItemAvailability(itemId: String, isAvailable: Boolean) {
        viewModelScope.launch {
            firestoreRepository.toggleMenuItemAvailability(_restaurantId.value, itemId, isAvailable)
        }
    }

    fun deleteMenuItem(itemId: String) {
        viewModelScope.launch {
            firestoreRepository.deleteMenuItem(_restaurantId.value, itemId)
        }
    }

    // --- RESTAURANT URL & POPUP CAMPAIGN ACTIONS ---
    fun updateWebMenuUrl(url: String) {
        viewModelScope.launch {
            val res = firestoreRepository.updateRestaurantWebMenuUrl(_restaurantId.value, url)
            res.onSuccess {
                _restaurant.value = _restaurant.value?.copy(webMenuUrl = url.trim().removeSuffix("/"))
                _uiMessage.emit("Web Menü linki başarıyla kaydedildi!")
            }.onFailure {
                _uiMessage.emit("Güncellenemedi: ${it.localizedMessage}")
            }
        }
    }

    fun savePopupCampaign(
        campaign: PopupCampaign,
        imageUri: Uri? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            var finalCampaign = campaign
            if (imageUri != null) {
                _uiMessage.emit("Kampanya görseli yükleniyor... ⏳")
                val uploadRes = storageRepository.uploadCampaignImage(_restaurantId.value, imageUri, campaign.imageUrl)
                uploadRes.onSuccess { url ->
                    finalCampaign = finalCampaign.copy(imageUrl = url)
                }.onFailure {
                    _uiMessage.emit("Görsel yüklenemedi: ${it.localizedMessage}")
                    return@launch
                }
            }

            val res = firestoreRepository.savePopupCampaign(_restaurantId.value, finalCampaign)
            res.onSuccess {
                _uiMessage.emit("Pop-up kampanyası güncellendi ve yayına alındı! 🌟✨")
                onComplete()
            }.onFailure {
                _uiMessage.emit("Kampanya kaydedilemedi: ${it.localizedMessage}")
            }
        }
    }

    // --- MANUAL SALE / DASHBOARD ACTIONS ---
    fun addManualSale(
        items: List<OrderItem>,
        tableLabel: String = "KASA / ELDEN",
        customerName: String = "",
        note: String = ""
    ) {
        viewModelScope.launch {
            if (items.isEmpty()) {
                _uiMessage.emit("Lütfen en az bir ürün seçin.")
                return@launch
            }
            val totalPrice = items.sumOf { it.unitPrice * it.quantity }
            val order = Order(
                tableId = "table-kasa",
                tableLabel = tableLabel.ifBlank { "KASA / ELDEN" },
                customerName = customerName.ifBlank { "Kasa Satışı" },
                status = "delivered", // Doğrudan teslim edilmiş satış
                items = items,
                totalPrice = totalPrice,
                note = note,
                createdAt = Date()
            )
            firestoreRepository.createOrder(_restaurantId.value, order)
                .onSuccess {
                    _uiMessage.emit("Satış başarıyla tamamlandı ve kaydedildi! 💸")
                }.onFailure {
                    _uiMessage.emit("Satış kaydedilemedi: ${it.localizedMessage}")
                }
        }
    }

    // --- TABLE ACTIONS ---
    fun saveTable(label: String, tableId: String = "", qrKey: String = "") {
        viewModelScope.launch {
            val table = TableItem(id = tableId, label = label, isActive = true, qrKey = qrKey)
            val res = firestoreRepository.saveTable(_restaurantId.value, table)
            res.onSuccess { _uiMessage.emit("Masa kaydedildi.") }
                .onFailure { _uiMessage.emit("Hata: ${it.localizedMessage}") }
        }
    }

    fun deleteTable(tableId: String) {
        viewModelScope.launch {
            firestoreRepository.deleteTable(_restaurantId.value, tableId)
        }
    }

    // --- FCM TOKEN ---
    private fun fetchAndRegisterFcmToken() {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    val user = authRepository.currentUser
                    if (user != null && token != null) {
                        viewModelScope.launch {
                            firestoreRepository.updateStaffFcmToken(
                                user.uid,
                                _restaurantId.value,
                                user.email ?: "Dükkan Sahibi",
                                token
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
