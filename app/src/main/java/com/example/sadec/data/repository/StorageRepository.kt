package com.example.sadec.data.repository

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

class StorageRepository {

    private val storage: FirebaseStorage
        get() = FirebaseStorage.getInstance()

    /**
     * Uploads category image with automatic cleanup of old image to prevent storage bloat.
     */
    suspend fun uploadCategoryImage(
        restaurantId: String,
        categoryId: String,
        imageUri: Uri,
        oldImageUrl: String? = null
    ): Result<String> {
        return try {
            // 1. Delete old storage image if present
            deleteOldImageIfStorage(oldImageUrl)

            // 2. Upload new image using category ID to guarantee overwrite or clean path
            val docKey = if (categoryId.isNotBlank()) categoryId else UUID.randomUUID().toString()
            val fileName = "cat_${docKey}_${System.currentTimeMillis()}.jpg"
            val imageRef = storage.reference.child("restaurants/$restaurantId/categories/$fileName")
            
            imageRef.putFile(imageUri).await()
            val downloadUrl = imageRef.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uploads product image with automatic cleanup of old image.
     */
    suspend fun uploadProductImage(
        restaurantId: String,
        imageUri: Uri,
        oldImageUrl: String? = null
    ): Result<String> {
        return try {
            // 1. Delete old storage image if present
            deleteOldImageIfStorage(oldImageUrl)

            val fileName = "prod_${UUID.randomUUID()}_${System.currentTimeMillis()}.jpg"
            val imageRef = storage.reference.child("restaurants/$restaurantId/menu/$fileName")
            imageRef.putFile(imageUri).await()
            val downloadUrl = imageRef.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadCampaignImage(
        restaurantId: String,
        imageUri: Uri,
        oldImageUrl: String? = null
    ): Result<String> {
        return try {
            deleteOldImageIfStorage(oldImageUrl)

            val fileName = "campaign_${System.currentTimeMillis()}.jpg"
            val imageRef = storage.reference.child("restaurants/$restaurantId/campaigns/$fileName")
            imageRef.putFile(imageUri).await()
            val downloadUrl = imageRef.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun deleteOldImageIfStorage(oldUrl: String?) {
        if (oldUrl.isNullOrBlank()) return
        try {
            if (oldUrl.contains("firebasestorage.googleapis.com")) {
                val oldRef = storage.getReferenceFromUrl(oldUrl)
                oldRef.delete().await()
            }
        } catch (e: Exception) {
            // Ignore deletion error (e.g. if file doesn't exist or already removed)
        }
    }
}
