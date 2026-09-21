package com.example.sadec.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class StorageRepository(private val context: Context) {

    // TODO: Cloudinary'den alacağınız "Cloud Name" bilgisini buraya yazın
    private val cloudName = "kvdllzqf"
    private val uploadPreset = "sadec_menu"

    suspend fun uploadCategoryImage(
        restaurantId: String,
        categoryId: String,
        imageUri: Uri,
        oldImageUrl: String? = null
    ): Result<String> {
        return uploadToCloudinary(imageUri)
    }

    suspend fun uploadProductImage(
        restaurantId: String,
        imageUri: Uri,
        oldImageUrl: String? = null
    ): Result<String> {
        return uploadToCloudinary(imageUri)
    }

    suspend fun uploadCampaignImage(
        restaurantId: String,
        imageUri: Uri,
        oldImageUrl: String? = null
    ): Result<String> {
        return uploadToCloudinary(imageUri)
    }

    /**
     * Verilen Uri'yi Base64 Data URI formatına çevirip Cloudinary REST API'sine yükler.
     */
    private suspend fun uploadToCloudinary(imageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (cloudName == "BURAYA_CLOUD_NAME_YAZIN" || uploadPreset == "BURAYA_UPLOAD_PRESET_YAZIN") {
                return@withContext Result.failure(Exception("Lütfen StorageRepository.kt dosyasına Cloudinary bilgilerinizi ekleyin!"))
            }

            // 1. Resmi oku ve Base64'e çevir
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val bytes = inputStream?.readBytes() ?: return@withContext Result.failure(Exception("Görsel okunamadı"))
            inputStream.close()
            
            // Cloudinary "file" parametresi Base64 için "data:image/jpeg;base64,..." formatını bekler
            val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val dataUri = "data:image/jpeg;base64,$base64Image"
            
            // 2. HTTP isteğini hazırla (Unsigned Upload REST API)
            val url = URL("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            // 3. Veriyi gönder
            val postData = "upload_preset=$uploadPreset&file=${URLEncoder.encode(dataUri, "UTF-8")}"
            connection.outputStream.write(postData.toByteArray(Charsets.UTF_8))
            connection.outputStream.flush()
            connection.outputStream.close()

            // 4. Yanıtı al
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(responseString)
                val secureUrl = jsonObject.getString("secure_url") // Cloudinary'nin güvenli HTTPS linki
                Result.success(secureUrl)
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Bilinmeyen hata"
                Result.failure(Exception("Cloudinary Yükleme Hatası ($responseCode): $errorResponse"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
