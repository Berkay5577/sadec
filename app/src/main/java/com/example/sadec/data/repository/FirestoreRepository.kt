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

    suspend fun updateRestaurantWebMenuUrl(restaurantId: String, webUrl: String): Result<Unit> {
        return try {
            val cleanUrl = webUrl.trim().removeSuffix("/")
            db.collection("restaurants").document(restaurantId).update("webMenuUrl", cleanUrl).await()
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
                    doc.toObject(Order::class.java)?.copy(id = doc.id)
                }
                trySend(orders)
            }
        }

        awaitClose { listenerRegistration.remove() }
    }

    suspend fun createOrder(restaurantId: String, order: Order): Result<String> {
        return try {
            val docRef = db.collection("restaurants")
                .document(restaurantId)
                .collection("orders")
                .add(order)
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

}
