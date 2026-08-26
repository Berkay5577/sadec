package com.example.sadec.data.repository

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

class StorageRepository {

    private val storage: FirebaseStorage
        get() = FirebaseStorage.getInstance()

    suspend fun uploadProductImage(restaurantId: String, imageUri: Uri): Result<String> {
        return try {
            val fileName = "${UUID.randomUUID()}.jpg"
            val imageRef = storage.reference.child("restaurants/$restaurantId/menu/$fileName")
            imageRef.putFile(imageUri).await()
            val downloadUrl = imageRef.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
