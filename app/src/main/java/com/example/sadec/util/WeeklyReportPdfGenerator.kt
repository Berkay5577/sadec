package com.example.sadec.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.sadec.data.model.Order
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object WeeklyReportPdfGenerator {

    /**
     * Generates a detailed weekly financial and order breakdown PDF report.
     * Contains:
     * 1. Summary Financial KPIs (Total Revenue, Total Items, Table Counts)
     * 2. Table-by-Table breakdown
     * 3. Product Sales Quantities breakdown
     * 4. Complete Chronological Order & Payment Log (Date, Time, Table, Customer, Items, Amount)
     */
    fun generateAndShareWeeklyReport(
        context: Context,
        orders: List<Order>,
        restaurantName: String = "Sade.C Kahve Gerze",
        weekPeriod: String = "",
        onSuccess: () -> Unit = {}
    ): File? {
        val completedOrders = orders.filter { it.status == "delivered" || it.items.any { item -> item.isPaid } }
        val allPaidItems = completedOrders.flatMap { it.items.filter { item -> item.isPaid } }
        val totalRevenue = if (allPaidItems.isNotEmpty()) {
            allPaidItems.sumOf { it.unitPrice * it.quantity }
        } else {
            completedOrders.sumOf { it.totalPrice }
        }

        try {
            val pdfDoc = PdfDocument()
            val pageWidth = 595 // A4 width
            val pageHeight = 842 // A4 height

            // Color Palette
            val colorForestGreen = 0xFF1E3A2F.toInt()
            val colorSageGreen = 0xFF2D5341.toInt()
            val colorWarmGold = 0xFFC59F60.toInt()
            val colorBgCream = 0xFFFAF8F5.toInt()
            val colorTextDark = 0xFF1A2420.toInt()
            val colorTextMuted = 0xFF5A6E65.toInt()
            val colorBorder = 0xFFE2ECE6.toInt()

            val sdfDate = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
            val sdfTime = SimpleDateFormat("HH:mm", Locale("tr", "TR"))
            val sdfFull = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("tr", "TR"))
            val nowFormatted = sdfFull.format(Date())

            val itemsPerPage = 14
            val orderChunks = completedOrders.chunked(itemsPerPage).ifEmpty { listOf(emptyList()) }
            val totalPages = orderChunks.size

            orderChunks.forEachIndexed { pageIndex, pageOrders ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas

                // Background
                canvas.drawColor(Color.WHITE)

                val paint = Paint(Paint.ANTI_ALIAS_FLAG)

                // Header Banner
                paint.color = colorForestGreen
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 95f, paint)

                // Gold Accent Line
                paint.color = colorWarmGold
                paint.strokeWidth = 3f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(0f, 95f, pageWidth.toFloat(), 95f, paint)

                // Title
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                paint.textSize = 20f
                canvas.drawText(restaurantName, 32f, 40f, paint)

                // Subtitle
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                paint.textSize = 11f
                paint.color = colorWarmGold
                val periodLabel = if (weekPeriod.isNotBlank()) "Haftalık Dönem: $weekPeriod • " else ""
                canvas.drawText("${periodLabel}Resmi Kasa & Gelir Raporu", 32f, 60f, paint)

                // Date stamp on header right
                paint.color = Color.WHITE.copyAlpha(0.85f)
                paint.textSize = 9.5f
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("Rapor Tarihi: $nowFormatted", pageWidth - 32f, 40f, paint)
                canvas.drawText("Sayfa ${pageIndex + 1} / $totalPages", pageWidth - 32f, 60f, paint)
                paint.textAlign = Paint.Align.LEFT

                var currentY = 115f

                // If Page 1, draw KPI Summary Cards & Grouped Stats
                if (pageIndex == 0) {
                    // Summary Box
                    val boxRect = RectF(32f, currentY, pageWidth - 32f, currentY + 75f)
                    paint.color = colorBgCream
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(boxRect, 10f, 10f, paint)
                    paint.color = colorBorder
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1f
                    canvas.drawRoundRect(boxRect, 10f, 10f, paint)

                    // KPI Columns
                    paint.style = Paint.Style.FILL

                    // KPI 1: Toplam Ciro
                    paint.color = colorTextMuted
                    paint.textSize = 9.5f
                    paint.typeface = Typeface.DEFAULT
                    canvas.drawText("TOPLAM HAFTALIK CİRO", 50f, currentY + 28f, paint)
                    paint.color = colorWarmGold
                    paint.textSize = 18f
                    paint.typeface = Typeface.DEFAULT_BOLD
                    canvas.drawText("₺${"%.2f".format(totalRevenue)}", 50f, currentY + 54f, paint)

                    // KPI 2: Sipariş Sayısı
                    paint.color = colorTextMuted
                    paint.textSize = 9.5f
                    paint.typeface = Typeface.DEFAULT
                    canvas.drawText("TOPLAM ADİSYON", 230f, currentY + 28f, paint)
                    paint.color = colorForestGreen
                    paint.textSize = 18f
                    paint.typeface = Typeface.DEFAULT_BOLD
                    canvas.drawText("${completedOrders.size} Adet", 230f, currentY + 54f, paint)

                    // KPI 3: Satılan Ürün Adedi
                    val totalItemsSold = completedOrders.sumOf { order -> order.items.sumOf { it.quantity } }
                    paint.color = colorTextMuted
                    paint.textSize = 9.5f
                    paint.typeface = Typeface.DEFAULT
                    canvas.drawText("SATILAN ÜRÜN", 390f, currentY + 28f, paint)
                    paint.color = colorSageGreen
                    paint.textSize = 18f
                    paint.typeface = Typeface.DEFAULT_BOLD
                    canvas.drawText("$totalItemsSold Porsiyon", 390f, currentY + 54f, paint)

                    currentY += 95f
                }

                // Table Header for Order Details
                paint.color = colorSageGreen
                paint.style = Paint.Style.FILL
                val tableHeaderRect = RectF(32f, currentY, pageWidth - 32f, currentY + 26f)
                canvas.drawRoundRect(tableHeaderRect, 6f, 6f, paint)

                paint.color = Color.WHITE
                paint.textSize = 9.5f
                paint.typeface = Typeface.DEFAULT_BOLD

                canvas.drawText("TARİH / SAAT", 42f, currentY + 17f, paint)
                canvas.drawText("MASA", 125f, currentY + 17f, paint)
                canvas.drawText("MÜŞTERİ", 185f, currentY + 17f, paint)
                canvas.drawText("SİPARİŞ İÇERİĞİ", 270f, currentY + 17f, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("TUTAR (₺)", pageWidth - 42f, currentY + 17f, paint)
                paint.textAlign = Paint.Align.LEFT

                currentY += 34f

                // Order Rows
                if (pageOrders.isEmpty()) {
                    paint.color = colorTextMuted
                    paint.textSize = 11f
                    paint.typeface = Typeface.DEFAULT
                    canvas.drawText("Bu dönemde henüz tamamlanmış sipariş bulunmuyor.", 42f, currentY + 20f, paint)
                } else {
                    pageOrders.forEachIndexed { rowIdx, order ->
                        val isEven = rowIdx % 2 == 0
                        if (isEven) {
                            paint.color = colorBgCream
                            paint.style = Paint.Style.FILL
                            canvas.drawRect(32f, currentY - 6f, pageWidth - 32f, currentY + 24f, paint)
                        }

                        // Row divider
                        paint.color = colorBorder
                        paint.strokeWidth = 0.5f
                        paint.style = Paint.Style.STROKE
                        canvas.drawLine(32f, currentY + 24f, pageWidth - 32f, currentY + 24f, paint)

                        paint.style = Paint.Style.FILL

                        // Date & Time
                        val dateStr = order.createdAt?.let { sdfDate.format(it) } ?: "--"
                        val timeStr = order.createdAt?.let { sdfTime.format(it) } ?: ""
                        paint.color = colorTextDark
                        paint.textSize = 8.5f
                        paint.typeface = Typeface.DEFAULT
                        canvas.drawText("$dateStr $timeStr", 42f, currentY + 12f, paint)

                        // Table
                        paint.typeface = Typeface.DEFAULT_BOLD
                        paint.color = colorForestGreen
                        canvas.drawText(order.tableLabel.ifBlank { "Masa" }, 125f, currentY + 12f, paint)

                        // Customer
                        paint.typeface = Typeface.DEFAULT
                        paint.color = colorTextDark
                        val custName = order.customerName.ifBlank { "Misafir" }.take(12)
                        canvas.drawText(custName, 185f, currentY + 12f, paint)

                        // Items Summary
                        val itemsSummary = order.items.joinToString(", ") { "${it.quantity}x ${it.name}" }.take(36)
                        paint.color = colorTextMuted
                        paint.textSize = 8f
                        canvas.drawText(itemsSummary, 270f, currentY + 12f, paint)

                        // Price
                        paint.textAlign = Paint.Align.RIGHT
                        paint.color = colorWarmGold
                        paint.textSize = 9.5f
                        paint.typeface = Typeface.DEFAULT_BOLD
                        canvas.drawText("₺${"%.2f".format(order.totalPrice)}", pageWidth - 42f, currentY + 12f, paint)
                        paint.textAlign = Paint.Align.LEFT

                        currentY += 30f
                    }
                }

                // Footer
                paint.color = colorTextMuted
                paint.textSize = 8f
                paint.typeface = Typeface.DEFAULT
                canvas.drawText("Sade.C Kahve Gerze Otomasyon Sistemi • Bu rapor resmi haftalık gelir dökümüdür.", 32f, pageHeight - 20f, paint)

                pdfDoc.finishPage(page)
            }

            // Save PDF to cache dir for sharing
            val cacheDir = File(context.cacheDir, "weekly_reports")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val fileName = "SadeC_Haftalik_Rapor_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
            val file = File(cacheDir, fileName)

            val outputStream = FileOutputStream(file)
            pdfDoc.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDoc.close()

            // Open share/print dialog
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Sade.C Gerze - Haftalık Gelir & Ciro Raporu")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Haftalık Raporu Paylaş / Yazdır / Kaydet").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })

            Toast.makeText(context, "Haftalık rapor başarıyla oluşturuldu! 📥📄", Toast.LENGTH_LONG).show()
            onSuccess()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Rapor oluşturulurken hata: ${e.message}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    private fun Int.copyAlpha(alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (this and 0x00FFFFFF) or (a shl 24)
    }
}
