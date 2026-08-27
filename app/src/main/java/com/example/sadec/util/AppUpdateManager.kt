package com.example.sadec.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.sadec.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sade.C Uygulama İçi Otomatik Güncelleme (In-App OTA Updater) Yöneticisi.
 * - Firestore üzerinden yayınlanan son sürüm kodunu kontrol eder.
 * - Yeni sürüm varsa APK'yı arka planda yüzde ilerleme göstergesiyle indirir.
 * - İndirme bittiğinde Android Paket Yükleyicisi'ni (Package Installer) otomatik başlatır.
 */
object AppUpdateManager {

    /**
     * Mevcut cihazın sürüm kodu ile sunucudaki en son sürüm kodunu karşılaştırır.
     */
    fun isUpdateAvailable(latestVersionCode: Int): Boolean {
        return latestVersionCode > BuildConfig.VERSION_CODE
    }

    /**
     * Cihazda şu anda kurulu olan sürüm bilgilerini döndürür.
     */
    fun getCurrentVersionCode(): Int = BuildConfig.VERSION_CODE
    fun getCurrentVersionName(): String = BuildConfig.VERSION_NAME

    /**
     * Verilen URL'den APK dosyasını önbelleğe (cache) indirir ve canlı ilerleme (0.0 .. 1.0) bildirir.
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (progress: Float, downloadedMB: Float, totalMB: Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            if (apkUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Geçerli bir APK indirme bağlantısı bulunamadı."))
            }

            val url = URL(apkUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            connection.connectTimeout = 20000
            connection.readTimeout = 45000
            connection.instanceFollowRedirects = true
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("Sunucu bağlantı hatası (HTTP $responseCode)"))
            }

            val fileLength = connection.contentLength
            val updateDir = File(context.cacheDir, "updates")
            if (!updateDir.exists()) updateDir.mkdirs()

            val apkFile = File(updateDir, "sadec_latest.apk")
            if (apkFile.exists()) apkFile.delete()

            val input = connection.inputStream
            val output = FileOutputStream(apkFile)

            val buffer = ByteArray(8192)
            var total: Long = 0
            var count: Int
            var lastReportTime = 0L

            while (input.read(buffer).also { count = it } != -1) {
                total += count
                output.write(buffer, 0, count)

                val currentTime = System.currentTimeMillis()
                if (fileLength > 0 && (currentTime - lastReportTime > 100 || total == fileLength.toLong())) {
                    lastReportTime = currentTime
                    val progress = total.toFloat() / fileLength.toFloat()
                    val downloadedMB = total / (1024f * 1024f)
                    val totalMB = fileLength / (1024f * 1024f)
                    withContext(Dispatchers.Main) {
                        onProgress(progress, downloadedMB, totalMB)
                    }
                }
            }

            output.flush()
            output.close()
            input.close()

            Result.success(apkFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * İndirilen APK dosyasını kurmak için Android Paket Yükleyicisini başlatır.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                Toast.makeText(context, "Güncelleme dosyası bulunamadı veya hasarlı.", Toast.LENGTH_SHORT).show()
                return
            }

            // Android 8.0+ (Oreo) Bilinmeyen kaynaklardan yükleme kontrolü
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(
                        context,
                        "Lütfen Sade.C için 'Bilinmeyen uygulamaları yükle' iznini açın.",
                        Toast.LENGTH_LONG
                    ).show()

                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(manageIntent)
                    return
                }
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Kurulum başlatılamadı: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
