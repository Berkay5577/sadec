package com.example.sadec.data.repository

import com.example.sadec.data.model.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreRepository {

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    // -------------------------------------------------------------
    // RESTAURANT
    // -------------------------------------------------------------
    suspend fun getOrCreateRestaurant(restaurantId: String): Restaurant {
        val docRef = db.collection("restaurants").document(restaurantId)
        val snapshot = docRef.get().await()

        val rest = if (snapshot.exists()) {
            snapshot.toObject(Restaurant::class.java)?.copy(id = snapshot.id)
                ?: Restaurant(id = restaurantId, name = "Sade.C Kahve Gerze", slug = restaurantId)
        } else {
            val newRest = Restaurant(
                id = restaurantId,
                name = "Sade.C Kahve Gerze",
                slug = restaurantId,
                address = "Gerze / Sinop",
                phone = "0555 123 45 67",
                instagram = "@sadeckahve",
                logoUrl = "logo.png"
            )
            docRef.set(newRest).await()
            newRest
        }

        return rest
    }

    fun listenRestaurant(restaurantId: String): Flow<Restaurant?> = callbackFlow {
        val listener = db.collection("restaurants").document(restaurantId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val rest = snapshot.toObject(Restaurant::class.java)?.copy(id = snapshot.id)
                    trySend(rest)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateRestaurantWebMenuUrl(restaurantId: String, webUrl: String): Result<Unit> {
        return try {
            val cleanUrl = webUrl.trim().removeSuffix("/")
            db.collection("restaurants").document(restaurantId).update("webMenuUrl", cleanUrl).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateManagerPin(restaurantId: String, newPin: String): Result<Unit> {
        return try {
            db.collection("restaurants").document(restaurantId).update("managerPin", newPin.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // ORDERS (Real-time Flow)
    // -------------------------------------------------------------
    fun listenOrders(restaurantId: String): Flow<List<Order>> = callbackFlow {
        val collectionRef = db.collection("restaurants")
            .document(restaurantId)
            .collection("orders")
            .orderBy("createdAt", Query.Direction.DESCENDING)

        val listenerRegistration = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val orders = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Order::class.java)?.let { ord ->
                        ord.copy(id = doc.id, items = ord.items.unbundled())
                    }
                }
                trySend(orders)
            }
        }

        awaitClose { listenerRegistration.remove() }
    }

    suspend fun createOrder(restaurantId: String, order: Order): Result<String> {
        return try {
            val normalizedOrder = order.copy(items = order.items.unbundled())
            val docRef = db.collection("restaurants")
                .document(restaurantId)
                .collection("orders")
                .add(normalizedOrder)
                .await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateOrderStatus(restaurantId: String, orderId: String, newStatus: String): Result<Unit> {
        return try {
            db.collection("restaurants")
                .document(restaurantId)
                .collection("orders")
                .document(orderId)
                .update(
                    mapOf(
                        "status" to newStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelOrder(restaurantId: String, orderId: String, reason: String): Result<Unit> {
        return try {
            db.collection("restaurants")
                .document(restaurantId)
                .collection("orders")
                .document(orderId)
                .update(
                    mapOf(
                        "status" to "cancelled",
                        "cancelReason" to reason,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun payOrderItem(
        restaurantId: String,
        orderId: String,
        itemIndex: Int,
        isPaid: Boolean,
        paymentMethod: String = "card",
        breakdown: SplitPaymentBreakdown? = null
    ): Result<Unit> {
        return try {
            val docRef = db.collection("restaurants").document(restaurantId).collection("orders").document(orderId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val order = snapshot.toObject(Order::class.java) ?: return@runTransaction
                val updatedItems = order.items.unbundled().toMutableList()
                if (itemIndex in updatedItems.indices) {
                    val targetItem = updatedItems[itemIndex]
                    val price = targetItem.effectivePrice()
                    val isSplit = breakdown != null && breakdown.isSplit()

                    updatedItems[itemIndex] = targetItem.copy(
                        isPaid = isPaid,
                        paymentMethod = if (isSplit) "split" else (if (isPaid) paymentMethod else ""),
                        cashPaid = if (isSplit) breakdown!!.cashAmount else (if (isPaid && paymentMethod == "cash") price else 0.0),
                        cardPaid = if (isSplit) breakdown!!.cardAmount else (if (isPaid && (paymentMethod == "card" || paymentMethod.isEmpty())) price else 0.0),
                        transferPaid = if (isSplit) breakdown!!.transferAmount else (if (isPaid && paymentMethod == "transfer") price else 0.0),
                        paidAt = if (isPaid) System.currentTimeMillis() else null
                    )
                    val isAllPaid = updatedItems.isEmpty() || updatedItems.all { it.isPaid || it.isComplimentary }
                    val newStatus = if (isAllPaid) "delivered" else order.status
                    transaction.update(
                        docRef,
                        mapOf(
                            "items" to updatedItems,
                            "status" to newStatus,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun payMultipleItems(
        restaurantId: String,
        itemsToPay: List<SelectedItemRef>,
        paymentMethod: String = "card",
        breakdown: SplitPaymentBreakdown? = null
    ): Result<Unit> {
        return try {
            val ordersGrouped = itemsToPay.groupBy { it.orderId }
            val ordersCol = db.collection("restaurants").document(restaurantId).collection("orders")
            val isSplit = breakdown != null && breakdown.isSplit()

            var remCash = if (isSplit) breakdown!!.cashAmount else 0.0
            var remCard = if (isSplit) breakdown!!.cardAmount else 0.0
            var remTransfer = if (isSplit) breakdown!!.transferAmount else 0.0

            db.runTransaction { transaction ->
                // 1. AŞAMA: TÜM OKUMALAR (READS)
                val orderDocs = ordersGrouped.keys.map { orderId ->
                    val docRef = ordersCol.document(orderId)
                    val snapshot = transaction.get(docRef)
                    val order = snapshot.toObject(Order::class.java)
                    Triple(docRef, order, ordersGrouped[orderId] ?: emptyList())
                }

                // 2. AŞAMA: HESAPLAMALAR
                val updates = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any?>>>()

                orderDocs.forEach { (docRef, order, targetList) ->
                    if (order != null) {
                        val rawItems = order.items.unbundled().toMutableList()
                        val targetIndices = targetList.map { it.itemIndex }.toSet()

                        targetIndices.forEach { idx ->
                            if (idx in rawItems.indices) {
                                val item = rawItems[idx]
                                val price = item.effectivePrice()
                                if (isSplit) {
                                    val cashToTake = minOf(remCash, price)
                                    remCash = maxOf(0.0, remCash - cashToTake)

                                    val neededAfterCash = price - cashToTake
                                    val cardToTake = minOf(remCard, neededAfterCash)
                                    remCard = maxOf(0.0, remCard - cardToTake)

                                    val neededAfterCard = neededAfterCash - cardToTake
                                    val transferToTake = minOf(remTransfer, neededAfterCard)
                                    remTransfer = maxOf(0.0, remTransfer - transferToTake)

                                    val method = when {
                                        cashToTake > 0 && cardToTake == 0.0 && transferToTake == 0.0 -> "cash"
                                        cardToTake > 0 && cashToTake == 0.0 && transferToTake == 0.0 -> "card"
                                        transferToTake > 0 && cashToTake == 0.0 && cardToTake == 0.0 -> "transfer"
                                        else -> "split"
                                    }

                                    rawItems[idx] = item.copy(
                                        isPaid = true,
                                        paymentMethod = method,
                                        cashPaid = cashToTake,
                                        cardPaid = cardToTake,
                                        transferPaid = transferToTake,
                                        paidAt = System.currentTimeMillis()
                                    )
                                } else {
                                    val isCash = paymentMethod == "cash"
                                    val isCard = paymentMethod == "card" || paymentMethod.isEmpty()
                                    val isTransfer = paymentMethod == "transfer"

                                    rawItems[idx] = item.copy(
                                        isPaid = true,
                                        paymentMethod = paymentMethod,
                                        cashPaid = if (isCash) price else 0.0,
                                        cardPaid = if (isCard) price else 0.0,
                                        transferPaid = if (isTransfer) price else 0.0,
                                        paidAt = System.currentTimeMillis()
                                    )
                                }
                            }
                        }

                        val isAllPaid = rawItems.isEmpty() || rawItems.all { it.isPaid || it.isComplimentary }
                        val newStatus = if (isAllPaid) "delivered" else order.status

                        updates.add(
                            docRef to mapOf(
                                "items" to rawItems,
                                "status" to newStatus,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }

                // 3. AŞAMA: TÜM YAZMALAR (WRITES)
                updates.forEach { (docRef, data) ->
                    transaction.update(docRef, data)
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun payFullOrder(
        restaurantId: String,
        orderId: String,
        paymentMethod: String = "card",
        breakdown: SplitPaymentBreakdown? = null
    ): Result<Unit> {
        return try {
            val docRef = db.collection("restaurants").document(restaurantId).collection("orders").document(orderId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val order = snapshot.toObject(Order::class.java) ?: return@runTransaction
                val isSplit = breakdown != null && breakdown.isSplit()
                var remCash = if (isSplit) breakdown!!.cashAmount else 0.0
                var remCard = if (isSplit) breakdown!!.cardAmount else 0.0
                var remTransfer = if (isSplit) breakdown!!.transferAmount else 0.0

                val updatedItems = order.items.unbundled().map { it ->
                    if (it.isPaid) return@map it
                    val price = it.effectivePrice()
                    if (isSplit) {
                        val cashToTake = minOf(remCash, price)
                        remCash = maxOf(0.0, remCash - cashToTake)

                        val neededAfterCash = price - cashToTake
                        val cardToTake = minOf(remCard, neededAfterCash)
                        remCard = maxOf(0.0, remCard - cardToTake)

                        val neededAfterCard = neededAfterCash - cardToTake
                        val transferToTake = minOf(remTransfer, neededAfterCard)
                        remTransfer = maxOf(0.0, remTransfer - transferToTake)

                        val method = when {
                            cashToTake > 0 && cardToTake == 0.0 && transferToTake == 0.0 -> "cash"
                            cardToTake > 0 && cashToTake == 0.0 && transferToTake == 0.0 -> "card"
                            transferToTake > 0 && cashToTake == 0.0 && cardToTake == 0.0 -> "transfer"
                            else -> "split"
                        }

                        it.copy(
                            isPaid = true,
                            paymentMethod = method,
                            cashPaid = cashToTake,
                            cardPaid = cardToTake,
                            transferPaid = transferToTake,
                            paidAt = it.paidAt ?: System.currentTimeMillis()
                        )
                    } else {
                        val isCash = paymentMethod == "cash"
                        val isCard = paymentMethod == "card" || paymentMethod.isEmpty()
                        val isTransfer = paymentMethod == "transfer"

                        it.copy(
                            isPaid = true,
                            paymentMethod = if (it.paymentMethod.isBlank()) paymentMethod else it.paymentMethod,
                            cashPaid = if (isCash) price else 0.0,
                            cardPaid = if (isCard) price else 0.0,
                            transferPaid = if (isTransfer) price else 0.0,
                            paidAt = it.paidAt ?: System.currentTimeMillis()
                        )
                    }
                }
                transaction.update(
                    docRef,
                    mapOf(
                        "items" to updatedItems,
                        "paymentMethod" to if (isSplit) "split" else paymentMethod,
                        "status" to "delivered",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun payEntireTableWithBreakdown(
        restaurantId: String,
        tableOrders: List<Order>,
        paymentMethod: String = "card",
        breakdown: SplitPaymentBreakdown? = null
    ): Result<Unit> {
        return try {
            val isSplit = breakdown != null && breakdown.isSplit()
            var remCash = if (isSplit) breakdown!!.cashAmount else 0.0
            var remCard = if (isSplit) breakdown!!.cardAmount else 0.0
            var remTransfer = if (isSplit) breakdown!!.transferAmount else 0.0
            val ordersCol = db.collection("restaurants").document(restaurantId).collection("orders")

            db.runTransaction { transaction ->
                // 1. AŞAMA: TÜM OKUMALAR (READS)
                val orderDocs = tableOrders.map { order ->
                    val docRef = ordersCol.document(order.id)
                    val snapshot = transaction.get(docRef)
                    val currentOrder = snapshot.toObject(Order::class.java)
                    Pair(docRef, currentOrder)
                }

                // 2. AŞAMA: HESAPLAMALAR
                val updates = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any?>>>()

                orderDocs.forEach { (docRef, currentOrder) ->
                    if (currentOrder != null) {
                        val updatedItems = currentOrder.items.unbundled().map { it ->
                            if (it.isPaid) return@map it
                            val price = it.effectivePrice()
                            if (isSplit) {
                                val cashToTake = minOf(remCash, price)
                                remCash = maxOf(0.0, remCash - cashToTake)

                                val neededAfterCash = price - cashToTake
                                val cardToTake = minOf(remCard, neededAfterCash)
                                remCard = maxOf(0.0, remCard - cardToTake)

                                val neededAfterCard = neededAfterCash - cardToTake
                                val transferToTake = minOf(remTransfer, neededAfterCard)
                                remTransfer = maxOf(0.0, remTransfer - transferToTake)

                                val method = when {
                                    cashToTake > 0 && cardToTake == 0.0 && transferToTake == 0.0 -> "cash"
                                    cardToTake > 0 && cashToTake == 0.0 && transferToTake == 0.0 -> "card"
                                    transferToTake > 0 && cashToTake == 0.0 && cardToTake == 0.0 -> "transfer"
                                    else -> "split"
                                }

                                it.copy(
                                    isPaid = true,
                                    paymentMethod = method,
                                    cashPaid = cashToTake,
                                    cardPaid = cardToTake,
                                    transferPaid = transferToTake,
                                    paidAt = it.paidAt ?: System.currentTimeMillis()
                                )
                            } else {
                                val isCash = paymentMethod == "cash"
                                val isCard = paymentMethod == "card" || paymentMethod.isEmpty()
                                val isTransfer = paymentMethod == "transfer"

                                it.copy(
                                    isPaid = true,
                                    paymentMethod = if (it.paymentMethod.isBlank()) paymentMethod else it.paymentMethod,
                                    cashPaid = if (isCash) price else 0.0,
                                    cardPaid = if (isCard) price else 0.0,
                                    transferPaid = if (isTransfer) price else 0.0,
                                    paidAt = it.paidAt ?: System.currentTimeMillis()
                                )
                            }
                        }

                        updates.add(
                            docRef to mapOf(
                                "items" to updatedItems,
                                "paymentMethod" to if (isSplit) "split" else paymentMethod,
                                "status" to "delivered",
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }

                // 3. AŞAMA: TÜM YAZMALAR (WRITES)
                updates.forEach { (docRef, data) ->
                    transaction.update(docRef, data)
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun transferEntireTable(
        restaurantId: String,
        fromTableId: String,
        toTableId: String,
        toTableLabel: String
    ): Result<Unit> {
        return try {
            val ordersQuery = db.collection("restaurants")
                .document(restaurantId)
                .collection("orders")
                .whereEqualTo("tableId", fromTableId)
                .whereEqualTo("isArchived", false)
                .get()
                .await()

            val batch = db.batch()
            for (doc in ordersQuery.documents) {
                val order = doc.toObject(Order::class.java)
                if (order != null && !order.isFullyPaid()) {
                    batch.update(
                        doc.reference,
                        mapOf(
                            "tableId" to toTableId,
                            "tableLabel" to toTableLabel,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun transferSingleOrder(
        restaurantId: String,
        orderId: String,
        toTableId: String,
        toTableLabel: String
    ): Result<Unit> {
        return try {
            db.collection("restaurants")
                .document(restaurantId)
                .collection("orders")
                .document(orderId)
                .update(
                    mapOf(
                        "tableId" to toTableId,
                        "tableLabel" to toTableLabel,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateOrderItem(
        restaurantId: String,
        orderId: String,
        itemIndex: Int,
        updatedItem: OrderItem
    ): Result<Unit> {
        return try {
            val docRef = db.collection("restaurants").document(restaurantId).collection("orders").document(orderId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val order = snapshot.toObject(Order::class.java) ?: return@runTransaction
                val updatedItems = order.items.toMutableList()
                if (itemIndex in updatedItems.indices) {
                    updatedItems[itemIndex] = updatedItem
                    val newTotal = updatedItems.sumOf { it.unitPrice * it.quantity }
                    transaction.update(
                        docRef,
                        mapOf(
                            "items" to updatedItems,
                            "totalPrice" to newTotal,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeOrderItem(
        restaurantId: String,
        orderId: String,
        itemIndex: Int
    ): Result<Unit> {
        return try {
            val docRef = db.collection("restaurants").document(restaurantId).collection("orders").document(orderId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val order = snapshot.toObject(Order::class.java) ?: return@runTransaction
                val updatedItems = order.items.toMutableList()
                if (itemIndex in updatedItems.indices) {
                    updatedItems.removeAt(itemIndex)
                    if (updatedItems.isEmpty()) {
                        transaction.update(
                            docRef,
                            mapOf(
                                "items" to emptyList<OrderItem>(),
                                "totalPrice" to 0.0,
                                "status" to "cancelled",
                                "cancelReason" to "Tüm ürünler çıkarıldı",
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    } else {
                        val newTotal = updatedItems.sumOf { it.unitPrice * it.quantity }
                        transaction.update(
                            docRef,
                            mapOf(
                                "items" to updatedItems,
                                "totalPrice" to newTotal,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setItemDiscountOrComplimentary(
        restaurantId: String,
        orderId: String,
        itemIndex: Int,
        isComplimentary: Boolean,
        discountAmount: Double
    ): Result<Unit> {
        return try {
            val docRef = db.collection("restaurants").document(restaurantId).collection("orders").document(orderId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val order = snapshot.toObject(Order::class.java) ?: return@runTransaction
                val updatedItems = order.items.toMutableList()
                if (itemIndex in updatedItems.indices) {
                    val targetItem = updatedItems[itemIndex]
                    updatedItems[itemIndex] = targetItem.copy(
                        isComplimentary = isComplimentary,
                        discountAmount = if (isComplimentary) 0.0 else discountAmount
                    )
                    transaction.update(
                        docRef,
                        mapOf(
                            "items" to updatedItems,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun closeDailyOrders(restaurantId: String, orderIds: List<String>, dayDate: String): Result<Unit> {
        return try {
            val batch = db.batch()
            val ordersCol = db.collection("restaurants").document(restaurantId).collection("orders")
            orderIds.forEach { orderId ->
                val ref = ordersCol.document(orderId)
                batch.update(
                    ref,
                    mapOf(
                        "isDayClosed" to true,
                        "closedDayDate" to dayDate,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun archiveOrders(restaurantId: String, orderIds: List<String>, weekPeriod: String): Result<Unit> {
        return try {
            val batch = db.batch()
            val ordersCol = db.collection("restaurants").document(restaurantId).collection("orders")
            orderIds.forEach { orderId ->
                val ref = ordersCol.document(orderId)
                batch.update(
                    ref,
                    mapOf(
                        "isArchived" to true,
                        "weekPeriod" to weekPeriod,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // CATEGORIES (Real-time Flow)
    // -------------------------------------------------------------
    fun listenCategories(restaurantId: String): Flow<List<Category>> = callbackFlow {
        val collectionRef = db.collection("restaurants")
            .document(restaurantId)
            .collection("categories")
            .orderBy("sortOrder", Query.Direction.ASCENDING)

        val listener = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val categories = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Category::class.java)?.copy(id = doc.id)
                }
                trySend(categories)
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun saveCategory(restaurantId: String, category: Category): Result<String> {
        return try {
            val col = db.collection("restaurants").document(restaurantId).collection("categories")
            val id = if (category.id.isNotBlank()) {
                col.document(category.id).set(category).await()
                category.id
            } else {
                val doc = col.add(category).await()
                doc.id
            }
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(restaurantId: String, categoryId: String): Result<Unit> {
        return try {
            db.collection("restaurants")
                .document(restaurantId)
                .collection("categories")
                .document(categoryId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // MENU ITEMS (Real-time Flow)
    // -------------------------------------------------------------
    fun listenMenuItems(restaurantId: String): Flow<List<MenuItem>> = callbackFlow {
        val collectionRef = db.collection("restaurants")
            .document(restaurantId)
            .collection("menuItems")
            .orderBy("sortOrder", Query.Direction.ASCENDING)

        val listener = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val items = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(MenuItem::class.java)?.copy(id = doc.id)
                }
                trySend(items)
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun saveMenuItem(restaurantId: String, item: MenuItem): Result<String> {
        return try {
            val col = db.collection("restaurants").document(restaurantId).collection("menuItems")
            val id = if (item.id.isNotBlank()) {
                col.document(item.id).set(item).await()
                item.id
            } else {
                val doc = col.add(item).await()
                doc.id
            }
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleMenuItemAvailability(restaurantId: String, itemId: String, isAvailable: Boolean): Result<Unit> {
        return try {
            db.collection("restaurants")
                .document(restaurantId)
                .collection("menuItems")
                .document(itemId)
                .update(
                    mapOf(
                        "isAvailable" to isAvailable,
                        "available" to isAvailable
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMenuItem(restaurantId: String, itemId: String): Result<Unit> {
        return try {
            db.collection("restaurants")
                .document(restaurantId)
                .collection("menuItems")
                .document(itemId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // TABLES (Real-time Flow)
    // -------------------------------------------------------------
    fun listenTables(restaurantId: String): Flow<List<TableItem>> = callbackFlow {
        val collectionRef = db.collection("restaurants")
            .document(restaurantId)
            .collection("tables")

        val listener = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val tables = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(TableItem::class.java)?.copy(id = doc.id)
                }
                trySend(tables)
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun saveTable(restaurantId: String, table: TableItem): Result<String> {
        return try {
            val col = db.collection("restaurants").document(restaurantId).collection("tables")
            val id = if (table.id.isNotBlank()) {
                col.document(table.id).set(table).await()
                table.id
            } else {
                val doc = col.add(table).await()
                doc.id
            }
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTable(restaurantId: String, tableId: String): Result<Unit> {
        return try {
            db.collection("restaurants")
                .document(restaurantId)
                .collection("tables")
                .document(tableId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // STAFF & FCM TOKENS
    // -------------------------------------------------------------
    suspend fun updateStaffFcmToken(staffId: String, restaurantId: String, name: String, token: String): Result<Unit> {
        return try {
            val staffDoc = db.collection("restaurants")
                .document(restaurantId)
                .collection("staff")
                .document(staffId)

            staffDoc.set(
                mapOf(
                    "name" to name,
                    "fcmTokens" to FieldValue.arrayUnion(token),
                    "role" to "owner",
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun savePopupCampaign(restaurantId: String, campaign: PopupCampaign): Result<Unit> {
        return try {
            val map = campaign.toMap()
            db.collection("restaurants")
                .document(restaurantId)
                .set(mapOf("popupCampaign" to map), com.google.firebase.firestore.SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setPopupCampaignActive(restaurantId: String, isActive: Boolean): Result<Unit> {
        return try {
            db.collection("restaurants")
                .document(restaurantId)
                .update(
                    mapOf(
                        "popupCampaign.isActive" to isActive,
                        "popupCampaign.active" to isActive,
                        "popupCampaign.updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            try {
                db.collection("restaurants")
                    .document(restaurantId)
                    .set(
                        mapOf(
                            "popupCampaign" to mapOf(
                                "isActive" to isActive,
                                "active" to isActive,
                                "updatedAt" to System.currentTimeMillis()
                            )
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .await()
                Result.success(Unit)
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }

    suspend fun saveAppUpdateInfo(restaurantId: String, updateInfo: AppUpdateInfo): Result<Unit> {
        return try {
            db.collection("restaurants")
                .document(restaurantId)
                .set(mapOf("appUpdateInfo" to updateInfo), com.google.firebase.firestore.SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
