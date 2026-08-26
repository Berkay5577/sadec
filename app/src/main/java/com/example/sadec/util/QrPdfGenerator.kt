package com.example.sadec.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.sadec.data.model.TableItem
import java.io.File
import java.io.FileOutputStream

object QrPdfGenerator {

    /**
     * Generates a luxury, high-resolution printable PDF containing QR stand cards for all tables.
     * Layout: 2 Table Cards per A4 Page (perfect stand size: ~105mm x 148mm each).
     */
    fun generateAndSharePdf(
        context: Context,
        tables: List<TableItem>,
        restaurantId: String,
        baseUrl: String,
        restaurantName: String = "Sade.C Kahve Gerze"
    ) {
        if (tables.isEmpty()) {
            Toast.makeText(context, "Yazdırılacak masa bulunamadı.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pdfDoc = PdfDocument()
            val pageWidth = 595 // A4 standard width in points
            val pageHeight = 842 // A4 standard height in points

            // Colors
            val colorForestGreen = 0xFF1E3A2F.toInt()
            val colorSageGreen = 0xFF2D5341.toInt()
            val colorWarmGold = 0xFFC59F60.toInt()
            val colorBgCream = 0xFFFAF8F5.toInt()

            // Sort tables nicely
            val sortedTables = tables.sortedBy { it.label }
            val chunks = sortedTables.chunked(2) // 2 cards per A4 page

            chunks.forEachIndexed { pageIndex, pageTables ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas

                // Background
                canvas.drawColor(Color.WHITE)

                val cardWidth = 515f
                val cardHeight = 370f
                val leftMargin = 40f
                val topPositions = listOf(35f, 435f)

                pageTables.forEachIndexed { cardIdx, table ->
                    val topMargin = topPositions[cardIdx]
                    val cardRect = RectF(leftMargin, topMargin, leftMargin + cardWidth, topMargin + cardHeight)

                    // 1. Card Container (Soft Cream with Double Gold/Green Frame)
                    val bgPaint = Paint().apply {
                        color = colorBgCream
                        style = Paint.Style.FILL
                    }
                    canvas.drawRoundRect(cardRect, 24f, 24f, bgPaint)

                    val borderPaint = Paint().apply {
                        color = colorForestGreen
                        style = Paint.Style.STROKE
                        strokeWidth = 3.5f
                        isAntiAlias = true
                    }
                    canvas.drawRoundRect(cardRect, 24f, 24f, borderPaint)

                    val innerBorderPaint = Paint().apply {
                        color = colorWarmGold
                        style = Paint.Style.STROKE
                        strokeWidth = 1.2f
                        isAntiAlias = true
                    }
                    val innerRect = RectF(leftMargin + 8f, topMargin + 8f, leftMargin + cardWidth - 8f, topMargin + cardHeight - 8f)
                    canvas.drawRoundRect(innerRect, 18f, 18f, innerBorderPaint)

                    // 2. Cafe Header Title & Subtitle
                    val titlePaint = Paint().apply {
                        color = colorForestGreen
                        textSize = 22f
                        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    canvas.drawText(restaurantName.uppercase(), leftMargin + (cardWidth / 2f), topMargin + 38f, titlePaint)

                    val subPaint = Paint().apply {
                        color = colorWarmGold
                        textSize = 10f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        letterSpacing = 0.2f
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    canvas.drawText("KAHVENİN EN SAF HALİ • GERZE", leftMargin + (cardWidth / 2f), topMargin + 54f, subPaint)

                    // 3. Generate QR Code Bitmap
                    val qrUrl = "$baseUrl?restId=$restaurantId&tableId=${table.id}&key=${table.qrKey}"
                    val qrBitmap = QrCodeGenerator.generateQrBitmap(qrUrl, 400)

                    if (qrBitmap != null) {
                        val qrSize = 175f
                        val qrLeft = leftMargin + (cardWidth - qrSize) / 2f
                        val qrTop = topMargin + 66f
                        val qrDestRect = RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize)

                        // White background for QR code
                        val qrBgPaint = Paint().apply {
                            color = Color.WHITE
                            style = Paint.Style.FILL
                        }
                        val qrBgRect = RectF(qrLeft - 8f, qrTop - 8f, qrLeft + qrSize + 8f, qrTop + qrSize + 8f)
                        canvas.drawRoundRect(qrBgRect, 14f, 14f, qrBgPaint)

                        val qrBorderPaint = Paint().apply {
                            color = colorWarmGold
                            style = Paint.Style.STROKE
                            strokeWidth = 1f
                            isAntiAlias = true
                        }
                        canvas.drawRoundRect(qrBgRect, 14f, 14f, qrBorderPaint)

                        canvas.drawBitmap(qrBitmap, null, qrDestRect, Paint(Paint.FILTER_BITMAP_FLAG))
                    }

                    // 4. Large Bold Table Number Badge (ÜSTTE QR KOD, ALTTA MASA NUMARASI)
                    val badgeRect = RectF(
                        leftMargin + (cardWidth / 2f) - 130f,
                        topMargin + 258f,
                        leftMargin + (cardWidth / 2f) + 130f,
                        topMargin + 312f
                    )
                    val badgeBg = Paint().apply {
                        color = colorForestGreen
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    canvas.drawRoundRect(badgeRect, 16f, 16f, badgeBg)

                    val tableTextPaint = Paint().apply {
                        color = Color.WHITE
                        textSize = 24f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    canvas.drawText("MASA: ${table.label.uppercase()}", leftMargin + (cardWidth / 2f), topMargin + 294f, tableTextPaint)

                    // 5. Instruction text below
                    val instructionPaint = Paint().apply {
                        color = colorSageGreen
                        textSize = 10.5f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    canvas.drawText("📱 Telefonunuzun kamerası ile QR kodu okutarak sipariş verebilirsiniz.", leftMargin + (cardWidth / 2f), topMargin + 338f, instructionPaint)

                    val securityPaint = Paint().apply {
                        color = Color.GRAY
                        textSize = 8f
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    canvas.drawText("Güvenli Masa Kodu: ${table.qrKey.ifBlank { table.id }}", leftMargin + (cardWidth / 2f), topMargin + 352f, securityPaint)
                }

                // Dotted cutting line between the 2 cards
                val cutLinePaint = Paint().apply {
                    color = Color.LTGRAY
                    strokeWidth = 1f
                    style = Paint.Style.STROKE
                    pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
                }
                canvas.drawLine(20f, 418f, 575f, 418f, cutLinePaint)

                pdfDoc.finishPage(page)
            }

            // Save PDF to cache dir
            val outputFile = File(context.cacheDir, "sadec_tum_masalar_qr.pdf")
            val outputStream = FileOutputStream(outputFile)
            pdfDoc.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDoc.close()

            // Share / Print via Intent
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "$restaurantName - Masa QR Kodları")
                putExtra(Intent.EXTRA_TEXT, "$restaurantName tüm masalar için QR kod PDF çıktısı.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Masa QR Kodları PDF'ini Paylaş / Yazdır")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF oluşturulurken hata: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
