package com.example.sadec.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ReportDownloader {

    /**
     * Saves the generated file directly to the device's public Downloads folder
     * and immediately launches ACTION_VIEW to open/display the file.
     */
    fun saveToDownloadsAndOpen(
        context: Context,
        sourceFile: File,
        mimeType: String,
        displayName: String,
        successMessage: String = ""
    ) {
        try {
            var savedUri: Uri? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SadeC_Raporlar")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val collectionUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = resolver.insert(collectionUri, contentValues)

                if (itemUri != null) {
                    resolver.openOutputStream(itemUri)?.use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(itemUri, contentValues, null, null)
                    savedUri = itemUri
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appDir = File(downloadsDir, "SadeC_Raporlar").apply { if (!exists()) mkdirs() }
                val destFile = File(appDir, displayName)
                sourceFile.copyTo(destFile, overwrite = true)
                savedUri = Uri.fromFile(destFile)
            }

            val msg = if (successMessage.isNotBlank()) successMessage else "📥 $displayName İndirilenler klasörüne kaydedildi!"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

            // Open the file directly using FileProvider
            val openUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                sourceFile
            )

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(openUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(viewIntent)
            } catch (e: Exception) {
                // If direct ACTION_VIEW has no specific single handler, try chooser
                try {
                    val chooser = Intent.createChooser(viewIntent, "Raporu Görüntüle").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                } catch (e2: Exception) {
                    // File is already saved to Downloads, no critical error
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "İndirme sırasında hata: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
