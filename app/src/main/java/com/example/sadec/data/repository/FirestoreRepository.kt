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

        // Otomatik Menü Kontrolü: Kategoriler boşsa veya eski demo (Pizza/Burger) varsa temizle ve Sade C Gerze menüsünü yükle
        try {
            val catSnap = docRef.collection("categories").get().await()
            val hasOldDemo = catSnap.documents.any { doc ->
                val name = doc.getString("name") ?: ""
                name.contains("Pizza", ignoreCase = true) || name.contains("Burger", ignoreCase = true)
            }
            if (catSnap.isEmpty || hasOldDemo) {
                seedSampleMenu(restaurantId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
                .update("isAvailable", isAvailable)
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

    // -------------------------------------------------------------
    // SEED COMPLETE SADE C MENU DATA
    // -------------------------------------------------------------
    suspend fun seedSampleMenu(restaurantId: String): Result<Unit> {
        return try {
            val restRef = db.collection("restaurants").document(restaurantId)

            // 0. Eski tüm demo ürün ve kategorileri tamamen sil (Pizza, Burger vb. temizle)
            try {
                val oldItems = restRef.collection("menuItems").get().await()
                for (doc in oldItems.documents) {
                    doc.reference.delete().await()
                }
                val oldCats = restRef.collection("categories").get().await()
                for (doc in oldCats.documents) {
                    doc.reference.delete().await()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            restRef.set(
                Restaurant(
                    id = restaurantId,
                    name = "Sade.C Kahve Gerze",
                    slug = restaurantId,
                    address = "Gerze / Sinop",
                    phone = "0555 123 45 67",
                    instagram = "@sadeckahve",
                    logoUrl = "logo.png"
                ),
                SetOptions.merge()
            ).await()

            // 1. Kategoriler
            val catHotRef = restRef.collection("categories").add(Category(name = "Sıcak Kahve & İçecekler", sortOrder = 1)).await()
            val catColdRef = restRef.collection("categories").add(Category(name = "Soğuk İçecekler & Kahveler", sortOrder = 2)).await()
            val catSpecialRef = restRef.collection("categories").add(Category(name = "Spesiyeller & Sandviçler", sortOrder = 3)).await()
            val catSnackRef = restRef.collection("categories").add(Category(name = "Atıştırmalıklar & Tostlar", sortOrder = 4)).await()
            val catDessertRef = restRef.collection("categories").add(Category(name = "Tatlılar & Pastalar", sortOrder = 5)).await()

            // 2. Ürünler
            val items = listOf(
                // Sıcak
                MenuItem(categoryId = catHotRef.id, name = "Espresso", description = "30ml taze çekilmiş espresso.", price = 100.0, imageUrl = "images/hot_coffee.jpg", sortOrder = 1),
                MenuItem(categoryId = catHotRef.id, name = "Double Espresso", description = "60ml yoğun espresso.", price = 120.0, imageUrl = "images/hot_coffee.jpg", sortOrder = 2),
                MenuItem(categoryId = catHotRef.id, name = "Double Shot Americano", description = "150ml sıcak su ve 60ml espresso.", price = 140.0, imageUrl = "images/hot_coffee.jpg", sortOrder = 3),
                MenuItem(categoryId = catHotRef.id, name = "Caffe Latte", description = "30ml espresso, 150ml taze sıcak süt.", price = 170.0, imageUrl = "images/hot_coffee.jpg", allergens = listOf("Süt"), sortOrder = 4),
                MenuItem(categoryId = catHotRef.id, name = "Cappuccino", description = "30ml espresso, 150ml sıcak süt, süt köpüğü ve çikolata tozu.", price = 170.0, imageUrl = "images/hot_coffee.jpg", allergens = listOf("Süt"), sortOrder = 5),
                MenuItem(categoryId = catHotRef.id, name = "Espresso Macchiato", description = "30ml espresso ve bir kaşık süt köpüğü.", price = 150.0, imageUrl = "images/hot_coffee.jpg", allergens = listOf("Süt"), sortOrder = 6),
                MenuItem(categoryId = catHotRef.id, name = "Caramel Macchiato", description = "30ml espresso, 180ml süt ve 30ml karamel sos.", price = 160.0, imageUrl = "images/hot_coffee.jpg", allergens = listOf("Süt"), sortOrder = 7),
                MenuItem(categoryId = catHotRef.id, name = "Mocha", description = "30ml espresso, 20gr çikolata, 130ml süt ve süt kreması.", price = 190.0, imageUrl = "images/hot_coffee.jpg", allergens = listOf("Süt"), sortOrder = 8),
                MenuItem(categoryId = catHotRef.id, name = "White Chocolate Mocha", description = "30ml espresso, 20gr beyaz çikolata, 130ml süt ve süt kreması.", price = 190.0, imageUrl = "images/hot_coffee.jpg", allergens = listOf("Süt"), sortOrder = 9),
                MenuItem(categoryId = catHotRef.id, name = "Cortado", description = "60ml espresso, 60ml süt ve kreması.", price = 160.0, imageUrl = "images/hot_coffee.jpg", allergens = listOf("Süt"), sortOrder = 10),
                MenuItem(categoryId = catHotRef.id, name = "Flat White", description = "60ml double espresso, 120ml sıcak süt.", price = 170.0, imageUrl = "images/hot_coffee.jpg", allergens = listOf("Süt"), sortOrder = 11),
                MenuItem(categoryId = catHotRef.id, name = "Black Eye", description = "180ml filtre kahve ve 60ml espresso.", price = 160.0, imageUrl = "images/hot_coffee.jpg", sortOrder = 12),
                MenuItem(categoryId = catHotRef.id, name = "Filtre Kahve V60", description = "Kolombiya, Sumatra, Guatemala bölgelerinden çekirdekler ile elle demlenir.", price = 180.0, imageUrl = "images/scene_pour.jpg", sortOrder = 13),
                MenuItem(categoryId = catHotRef.id, name = "Filtre Kahve Makina", description = "Kolombiya kahvesi ile taze demlenir.", price = 120.0, imageUrl = "images/hot_coffee.jpg", sortOrder = 14),
                MenuItem(categoryId = catHotRef.id, name = "Siyah Çay", description = "Taze demlenmiş Rize çayı.", price = 30.0, imageUrl = "images/hot_coffee.jpg", sortOrder = 15),
                MenuItem(categoryId = catHotRef.id, name = "Bitki Çayları", description = "Adaçayı, Hibiscus, Papatya, Matcha vb.", price = 150.0, imageUrl = "images/hot_coffee.jpg", sortOrder = 16),
                MenuItem(categoryId = catHotRef.id, name = "Türk Kahvesi", description = "Çifte kavrulmuş lokum ve su ile geleneksel sunum.", price = 75.0, imageUrl = "images/hot_coffee.jpg", sortOrder = 17),
                MenuItem(categoryId = catHotRef.id, name = "Affogato", description = "Vanilyalı dondurma ve 60ml espresso.", price = 250.0, imageUrl = "images/hot_coffee.jpg", allergens = listOf("Süt"), sortOrder = 18),
                MenuItem(categoryId = catHotRef.id, name = "Sıcak Çikolata", description = "180ml sıcak süt ve eritilmiş çikolata.", price = 180.0, imageUrl = "images/hot_coffee.jpg", allergens = listOf("Süt"), sortOrder = 19),
                MenuItem(categoryId = catHotRef.id, name = "Beyaz Sıcak Çikolata", description = "180ml sıcak süt ve beyaz çikolata.", price = 180.0, imageUrl = "images/hot_coffee.jpg", allergens = listOf("Süt"), sortOrder = 20),
                MenuItem(categoryId = catHotRef.id, name = "Pembe Ruby Sıcak Çikolata", description = "180ml sıcak süt ve ruby çikolata.", price = 180.0, imageUrl = "images/hot_coffee.jpg", allergens = listOf("Süt"), sortOrder = 21),

                // Soğuk
                MenuItem(categoryId = catColdRef.id, name = "Ice Americano", description = "150ml soğuk su, 60ml espresso ve buz.", price = 150.0, imageUrl = "images/cold_drinks.jpg", sortOrder = 1),
                MenuItem(categoryId = catColdRef.id, name = "Ice Latte", description = "130ml soğuk süt, 30ml espresso ve buz.", price = 180.0, imageUrl = "images/cold_drinks.jpg", allergens = listOf("Süt"), sortOrder = 2),
                MenuItem(categoryId = catColdRef.id, name = "Ice White Chocolate Mocha", description = "30ml espresso, 20gr beyaz çikolata, 130ml soğuk süt, krema ve buz.", price = 200.0, imageUrl = "images/cold_drinks.jpg", allergens = listOf("Süt"), sortOrder = 3),
                MenuItem(categoryId = catColdRef.id, name = "Ice Mocha", description = "30ml espresso, 20gr çikolata, 130ml soğuk süt, krema ve buz.", price = 200.0, imageUrl = "images/cold_drinks.jpg", allergens = listOf("Süt"), sortOrder = 4),
                MenuItem(categoryId = catColdRef.id, name = "Ice Caramel Macchiato", description = "30ml espresso, 180ml soğuk süt, 30ml karamel ve buz.", price = 190.0, imageUrl = "images/cold_drinks.jpg", allergens = listOf("Süt"), sortOrder = 5),
                MenuItem(categoryId = catColdRef.id, name = "Frapeler", description = "Çilekli, Muzlu, Kakaolu, Karpuzlu, Yeşil Elmalı, Mangolu vb.", price = 180.0, imageUrl = "images/cold_drinks.jpg", allergens = listOf("Süt"), sortOrder = 6),
                MenuItem(categoryId = catColdRef.id, name = "Cold Brew", description = "Soğuk dem Kolombiya kahvesi.", price = 180.0, imageUrl = "images/cold_drinks.jpg", sortOrder = 7),
                MenuItem(categoryId = catColdRef.id, name = "Ev Yapımı Limonata", description = "200ml ev yapımı ferahlatıcı limonata ve buz.", price = 160.0, imageUrl = "images/cold_drinks.jpg", sortOrder = 8),
                MenuItem(categoryId = catColdRef.id, name = "Ev Yapımı Erik Suyu", description = "200ml ev yapımı erik suyu ve buz.", price = 180.0, imageUrl = "images/cold_drinks.jpg", sortOrder = 9),
                MenuItem(categoryId = catColdRef.id, name = "Ice Hibiscus Çayı", description = "Buz ve soğuk demlenmiş hibiscus çayı.", price = 200.0, imageUrl = "images/cold_drinks.jpg", sortOrder = 10),
                MenuItem(categoryId = catColdRef.id, name = "Muzlu Milkshake", description = "200ml dondurmalı muzlu milkshake.", price = 200.0, imageUrl = "images/cold_drinks.jpg", allergens = listOf("Süt"), sortOrder = 11),
                MenuItem(categoryId = catColdRef.id, name = "Mangolu Milkshake", description = "200ml dondurmalı mango milkshake.", price = 200.0, imageUrl = "images/cold_drinks.jpg", allergens = listOf("Süt"), sortOrder = 12),
                MenuItem(categoryId = catColdRef.id, name = "Çilekli Milkshake", description = "200ml dondurmalı çilek milkshake.", price = 200.0, imageUrl = "images/cold_drinks.jpg", allergens = listOf("Süt"), sortOrder = 13),
                MenuItem(categoryId = catColdRef.id, name = "Çikolatalı Milkshake", description = "200ml dondurmalı çikolata milkshake.", price = 200.0, imageUrl = "images/cold_drinks.jpg", allergens = listOf("Süt"), sortOrder = 14),
                MenuItem(categoryId = catColdRef.id, name = "Soda Limon", description = "Maden suyu ve taze limon dilimi.", price = 70.0, imageUrl = "images/cold_drinks.jpg", sortOrder = 15),
                MenuItem(categoryId = catColdRef.id, name = "Coca Cola", description = "330ml Kutu.", price = 80.0, imageUrl = "images/cold_drinks.jpg", sortOrder = 16),
                MenuItem(categoryId = catColdRef.id, name = "Fanta", description = "330ml Kutu.", price = 80.0, imageUrl = "images/cold_drinks.jpg", sortOrder = 17),
                MenuItem(categoryId = catColdRef.id, name = "Sprite", description = "330ml Kutu.", price = 80.0, imageUrl = "images/cold_drinks.jpg", sortOrder = 18),
                MenuItem(categoryId = catColdRef.id, name = "Bardak Su", description = "Doğal kaynak suyu.", price = 30.0, imageUrl = "images/cold_drinks.jpg", sortOrder = 19),

                // Spesiyeller
                MenuItem(categoryId = catSpecialRef.id, name = "Köfte Sandviç", description = "Cibata Ekmeği, Çıtır Dışı Yumuşak İçi, Köfte, Mozzarella Peyniri, Karamelize Soğan, Közlenmiş Kapya Biber, Özel Soslar", price = 260.0, imageUrl = "images/promo_sandwich.jpg", allergens = listOf("Gluten", "Süt"), sortOrder = 1),
                MenuItem(categoryId = catSpecialRef.id, name = "Tavuk Sandviç", description = "Cibata Ekmeği, Çıtır Dışı Yumuşak İçi, Özel Pişmiş Izgara Tavuk, Yeşillik, Özel Soslar", price = 260.0, imageUrl = "images/promo_sandwich.jpg", allergens = listOf("Gluten", "Süt"), sortOrder = 2),

                // Atıştırmalıklar
                MenuItem(categoryId = catSnackRef.id, name = "Üç Peynirli Bazlama Tost", description = "Mozzarella, kolot, kaşar peynirli", price = 175.0, imageUrl = "images/snacks.jpg", allergens = listOf("Gluten", "Süt"), sortOrder = 1),
                MenuItem(categoryId = catSnackRef.id, name = "Sucuklu Kaşarlı Bazlama Tost", description = "Dana sucuk, kaşar peyniri", price = 175.0, imageUrl = "images/snacks.jpg", allergens = listOf("Gluten", "Süt"), sortOrder = 2),
                MenuItem(categoryId = catSnackRef.id, name = "Mücver (Dip Soslu)", description = "Kabak, havuç, soğan, dereotu, maydanoz, baharat", price = 150.0, imageUrl = "images/snacks.jpg", allergens = listOf("Gluten", "Yumurta"), sortOrder = 3),
                MenuItem(categoryId = catSnackRef.id, name = "Ispanaklı Börek", description = "Ev yapımı yufka, yerli ürünler", price = 60.0, imageUrl = "images/snacks.jpg", allergens = listOf("Gluten"), sortOrder = 4),
                MenuItem(categoryId = catSnackRef.id, name = "Peynirli Börek", description = "Lor peyniri, beyaz peynir, Antep peyniri", price = 60.0, imageUrl = "images/snacks.jpg", allergens = listOf("Gluten", "Süt"), sortOrder = 5),
                MenuItem(categoryId = catSnackRef.id, name = "Patatesli Börek", description = "Ev yapımı yufka, soğan, patates, baharat", price = 60.0, imageUrl = "images/snacks.jpg", allergens = listOf("Gluten"), sortOrder = 6),
                MenuItem(categoryId = catSnackRef.id, name = "Dereotlu Poğaça", description = "Dereotu, maydanoz, havuç, peynir — 110gr", price = 50.0, imageUrl = "images/snacks.jpg", allergens = listOf("Gluten", "Süt", "Yumurta"), sortOrder = 7),
                MenuItem(categoryId = catSnackRef.id, name = "Yumurtalı Peynirli Ekmek", description = "Ezine peyniri, yumurta, maydanoz, kekik (25dk hazırlanır)", price = 80.0, imageUrl = "images/snacks.jpg", allergens = listOf("Gluten", "Süt", "Yumurta"), sortOrder = 8),

                // Tatlılar
                MenuItem(categoryId = catDessertRef.id, name = "San Sebastian Cheesecake", description = "Özel yapım sıcak Belçika çikolata sosu ile.", price = 220.0, imageUrl = "images/promo_cheesecake.jpg", allergens = listOf("Süt", "Yumurta"), sortOrder = 1),
                MenuItem(categoryId = catDessertRef.id, name = "Amerikan Creamy Nemli Kek", description = "Bol kakaolu yumuşak kek ve özel kakaolu kreması ile.", price = 180.0, imageUrl = "images/desserts.jpg", allergens = listOf("Gluten", "Süt", "Yumurta"), sortOrder = 2),
                MenuItem(categoryId = catDessertRef.id, name = "Tres Leches (Trileçe)", description = "Mascarpone ve krema ile örtülmüş süt reçelli kek.", price = 200.0, imageUrl = "images/desserts.jpg", allergens = listOf("Gluten", "Süt", "Yumurta"), sortOrder = 3),
                MenuItem(categoryId = catDessertRef.id, name = "Cup Cakes", description = "Limonlu ve çikolatalı vs.", price = 60.0, imageUrl = "images/desserts.jpg", allergens = listOf("Gluten", "Süt", "Yumurta"), sortOrder = 4),
                MenuItem(categoryId = catDessertRef.id, name = "Çilekli Kap Pasta", description = "Taze çilekler ve vanilyalı pasta kreması.", price = 200.0, imageUrl = "images/desserts.jpg", allergens = listOf("Gluten", "Süt", "Yumurta"), sortOrder = 5),
                MenuItem(categoryId = catDessertRef.id, name = "Supangle", description = "Çikolata pralin ve süt.", price = 190.0, imageUrl = "images/desserts.jpg", allergens = listOf("Süt"), sortOrder = 6),
                MenuItem(categoryId = catDessertRef.id, name = "Tiramisu", description = "Mascarpone ve krema ile hazırlanmış orijinal harç ve kedi dilleri.", price = 200.0, imageUrl = "images/desserts.jpg", allergens = listOf("Gluten", "Süt", "Yumurta"), sortOrder = 7),
                MenuItem(categoryId = catDessertRef.id, name = "Elmalı Kramble (Crumble)", description = "Elma ve üst çıtır örtüsü ile sunulan kek.", price = 190.0, imageUrl = "images/desserts.jpg", allergens = listOf("Gluten", "Süt"), sortOrder = 8),
                MenuItem(categoryId = catDessertRef.id, name = "Brownie", description = "Çikolata, tereyağı, un, ceviz ve fındık içi.", price = 190.0, imageUrl = "images/desserts.jpg", allergens = listOf("Gluten", "Süt", "Yumurta", "Fındık"), sortOrder = 9),
                MenuItem(categoryId = catDessertRef.id, name = "Çok Çikolatalı Kek", description = "Çikolata, espresso kahve, tereyağı ve özel çikolata sosu ile.", price = 180.0, imageUrl = "images/desserts.jpg", allergens = listOf("Gluten", "Süt", "Yumurta"), sortOrder = 10),
                MenuItem(categoryId = catDessertRef.id, name = "Dilim Cheesecake", description = "Frambuazlı, çikolatalı, limonlu ve vişneli seçenekleriyle.", price = 120.0, imageUrl = "images/desserts.jpg", allergens = listOf("Gluten", "Süt", "Yumurta"), sortOrder = 11)
            )

            for (item in items) {
                restRef.collection("menuItems").add(item).await()
            }

            // Masaları temizle ve tam istenen masaları oluştur
            try {
                val oldTables = restRef.collection("tables").get().await()
                for (doc in oldTables.documents) {
                    doc.reference.delete().await()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val tableList = listOf(
                "table-bar" to "BAR",
                "table-ic-1" to "İÇ 1",
                "table-ic-2" to "İÇ 2",
                "table-dis-1" to "DIŞ 1",
                "table-dis-2" to "DIŞ 2",
                "table-dis-3" to "DIŞ 3",
                "table-dis-4" to "DIŞ 4",
                "table-y-1" to "Y1",
                "table-y-2" to "Y2"
            )

            for ((tId, tLabel) in tableList) {
                val key = "sk_" + tId.replace("table-", "") + "_" + (1000..9999).random()
                restRef.collection("tables").document(tId).set(
                    TableItem(id = tId, label = tLabel, isActive = true, qrKey = key)
                ).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
