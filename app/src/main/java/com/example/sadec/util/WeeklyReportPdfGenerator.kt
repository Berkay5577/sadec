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
     * Generates a detailed weekly financial and order breakdown PDF report grouped DAY-BY-DAY:
     * 1. Summary Financial KPIs (Total Revenue, Card, Cash, Transfer, Complimentary)
     * 2. Day-by-Day Revenue & Sales Breakdown Table (Pazartesi, Salı, Çarşamba, Perşembe, Cuma, Cumartesi, Pazar)
     * 3. Complete Chronological Order & Payment Log separated by day
     * 4. Official Signatures
     */
    fun generateAndShareWeeklyReport(
        context: Context,
        orders: List<Order>,
        restaurantName: String = "Sade.C Kahve Gerze",
        weekPeriod: String = "",
        onSuccess: () -> Unit = {}
    ): File? {
        val completedOrders = orders.filter { it.status == "delivered" || it.items.any { item -> item.isPaid } }
            .sortedBy { it.createdAt?.time ?: 0L }

        val totalRevenue = completedOrders.sumOf { it.paidAmount().let { p -> if (p > 0) p else it.totalPrice } }
        val totalCard = completedOrders.sumOf { it.cardPaidAmount() }
        val totalCash = completedOrders.sumOf { it.cashPaidAmount() }
        val totalTransfer = completedOrders.sumOf { it.transferPaidAmount() }
        val totalComp = completedOrders.sumOf { it.complimentaryAmount() }
        val totalItemsSold = completedOrders.sumOf { it.items.sumOf { item -> item.quantity } }

        try {
            val pdfDoc = PdfDocument()
            val pageWidth = 595 // A4 width
            val pageHeight = 842 // A4 height

            // Color Palette
            val colorForestGreen = 0xFF1E3A2F.toInt()
            val colorSageGreen = 0xFF2D5341.toInt()
            val colorWarmGold = 0xFFC59F60.toInt()
            val colorSoftMint = 0xFFEBF3EE.toInt()
            val colorBgCream = 0xFFFAF8F5.toInt()
            val colorTextDark = 0xFF1A2420.toInt()
            val colorTextMuted = 0xFF5A6E65.toInt()
            val colorBorder = 0xFFE2ECE6.toInt()

            val sdfDate = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
            val sdfDayName = SimpleDateFormat("EEEE", Locale("tr", "TR"))
            val sdfTime = SimpleDateFormat("HH:mm", Locale("tr", "TR"))
            val sdfFull = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("tr", "TR"))
            val nowFormatted = sdfFull.format(Date())

            val itemsPerPage = 14
            val orderChunks = completedOrders.chunked(itemsPerPage).ifEmpty { listOf(emptyList()) }
            val totalPages = orderChunks.size

            // Compute Day-by-Day Stats for the week
            val dayGroups = completedOrders.groupBy { order ->
                order.createdAt?.let { sdfDate.format(it) } ?: "Tarihsiz"
            }

            orderChunks.forEachIndexed { pageIndex, pageOrders ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas

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
                canvas.drawText(restaurantName.uppercase(), 32f, 40f, paint)

                // Subtitle
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                paint.textSize = 11f
                paint.color = colorWarmGold
                val periodLabel = if (weekPeriod.isNotBlank()) "Haftalık Dönem: $weekPeriod • " else ""
                canvas.drawText("${periodLabel}Resmi Satış & Gelir Raporu (Gün Gün Dökümlü)", 32f, 60f, paint)

                // Date stamp on header right
                paint.color = Color.WHITE.copyAlpha(0.85f)
                paint.textSize = 9.5f
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("Rapor Tarihi: $nowFormatted", pageWidth - 32f, 40f, paint)
                canvas.drawText("Sayfa ${pageIndex + 1} / $totalPages", pageWidth - 32f, 60f, paint)
                paint.textAlign = Paint.Align.LEFT

                var currentY = 112f

                // If Page 1, draw KPI Summary Cards & Day-by-Day Table
                if (pageIndex == 0) {
                    // KPI Row (Kredi Kartı, Nakit, İkram, Toplam Ciro)
                    val cardW = (pageWidth - 64f - 18f) / 4f
                    val cardH = 46f

                    drawMiniKpi(canvas, paint, 32f, currentY, cardW, cardH, "KREDİ KARTI / POS", "₺${"%.2f".format(totalCard)}", colorForestGreen, colorSoftMint, colorBorder)
                    drawMiniKpi(canvas, paint, 32f + cardW + 6f, currentY, cardW, cardH, "NAKİT TAHSİLAT", "₺${"%.2f".format(totalCash)}", colorForestGreen, colorSoftMint, colorBorder)
                    drawMiniKpi(canvas, paint, 32f + (cardW + 6f) * 2, currentY, cardW, cardH, "İKRAM/İNDİRİM", "₺${"%.2f".format(totalComp)}", colorTextMuted, colorSoftMint, colorBorder)
                    drawMiniKpi(canvas, paint, 32f + (cardW + 6f) * 3, currentY, cardW, cardH, "HAFTALIK NET CİRO", "₺${"%.2f".format(totalRevenue)}", colorWarmGold, colorForestGreen, colorWarmGold, isDark = true)

                    currentY += cardH + 16f

                    // Day-by-Day Summary Section
                    paint.color = colorForestGreen
                    paint.textSize = 11.5f
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    canvas.drawText("GÜN GÜN HAFTALIK SATIŞ & KASA MUTABAKATI", 32f, currentY, paint)

                    currentY += 6f
                    paint.color = colorWarmGold
                    paint.strokeWidth = 1.5f
                    canvas.drawLine(32f, currentY, 260f, currentY, paint)

                    currentY += 10f

                    // Day Table Header
                    paint.style = Paint.Style.FILL
                    paint.color = colorForestGreen
                    canvas.drawRect(32f, currentY, pageWidth - 32f, currentY + 18f, paint)

                    paint.color = Color.WHITE
                    paint.textSize = 8.5f
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    canvas.drawText("Tarih & Gün", 40f, currentY + 12f, paint)
                    canvas.drawText("Adisyon", 170f, currentY + 12f, paint)
                    canvas.drawText("Satılan Ürün", 240f, currentY + 12f, paint)
                    canvas.drawText("Kart", 330f, currentY + 12f, paint)
                    canvas.drawText("Nakit", 410f, currentY + 12f, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("Günlük Ciro (TL)", pageWidth - 40f, currentY + 12f, paint)
                    paint.textAlign = Paint.Align.LEFT

                    currentY += 18f

                    dayGroups.entries.toList().forEachIndexed { dIdx, entry ->
                        val dKey = entry.key
                        val dOrders = entry.value
                        val sampleDate = dOrders.firstOrNull()?.createdAt
                        val dName = sampleDate?.let { sdfDayName.format(it).uppercase(Locale("tr")) } ?: ""
                        val dCount = dOrders.size
                        val dItems = dOrders.sumOf { ord -> ord.items.sumOf { i -> i.quantity } }
                        val dCard = dOrders.sumOf { ord -> ord.cardPaidAmount() }
                        val dCash = dOrders.sumOf { ord -> ord.cashPaidAmount() }
                        val dRev = dOrders.sumOf { ord -> ord.paidAmount().let { p -> if (p > 0) p else ord.totalPrice } }

                        paint.color = if (dIdx % 2 == 0) Color.WHITE else 0xFFF7FAF8.toInt()
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(32f, currentY, pageWidth - 32f, currentY + 17f, paint)

                        paint.color = colorBorder
                        paint.strokeWidth = 0.5f
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(32f, currentY, pageWidth - 32f, currentY + 17f, paint)

                        paint.style = Paint.Style.FILL
                        paint.color = colorTextDark
                        paint.textSize = 8.5f
                        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                        canvas.drawText("$dKey $dName", 40f, currentY + 11f, paint)
                        canvas.drawText("$dCount adet", 170f, currentY + 11f, paint)
                        canvas.drawText("$dItems porsiyon", 240f, currentY + 11f, paint)
                        canvas.drawText("₺${"%.2f".format(dCard)}", 330f, currentY + 11f, paint)
                        canvas.drawText("₺${"%.2f".format(dCash)}", 410f, currentY + 11f, paint)

                        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        paint.color = colorForestGreen
                        paint.textAlign = Paint.Align.RIGHT
                        canvas.drawText("₺${"%.2f".format(dRev)}", pageWidth - 40f, currentY + 11f, paint)
                        paint.textAlign = Paint.Align.LEFT

                        currentY += 17f
                    }

                    currentY += 16f
                }

                // Table Header for Order Details
                paint.color = colorForestGreen
                paint.style = Paint.Style.FILL
                val tableHeaderRect = RectF(32f, currentY, pageWidth - 32f, currentY + 20f)
                canvas.drawRoundRect(tableHeaderRect, 6f, 6f, paint)

                paint.color = Color.WHITE
                paint.textSize = 8.5f
                paint.typeface = Typeface.DEFAULT_BOLD

                canvas.drawText("TARİH / SAAT", 42f, currentY + 13f, paint)
                canvas.drawText("MASA", 130f, currentY + 13f, paint)
                canvas.drawText("MÜŞTERİ", 195f, currentY + 13f, paint)
                canvas.drawText("SİPARİŞ İÇERİĞİ", 280f, currentY + 13f, paint)
                canvas.drawText("ÖDEME", 430f, currentY + 13f, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("TUTAR (₺)", pageWidth - 42f, currentY + 13f, paint)
                paint.textAlign = Paint.Align.LEFT

                currentY += 24f

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
                            canvas.drawRect(32f, currentY - 4f, pageWidth - 32f, currentY + 16f, paint)
                        }

                        val dateStr = order.createdAt?.let { sdfDate.format(it) } ?: "--"
                        val timeStr = order.createdAt?.let { sdfTime.format(it) } ?: "--"
                        val tableLabel = order.tableLabel.ifBlank { "Masa" }
                        val customer = order.customerName.ifBlank { "Misafir" }.take(12)
                        val itemsSummary = order.items.joinToString(", ") { "${it.quantity}x ${it.name}" }.take(32)
                        val methodTag = when {
                            order.cardPaidAmount() > 0 -> "Kart"
                            order.cashPaidAmount() > 0 -> "Nakit"
                            order.transferPaidAmount() > 0 -> "Havale"
                            order.complimentaryAmount() > 0 -> "İkram"
                            else -> "Kart"
                        }
                        val orderPrice = order.paidAmount().let { if (it > 0) it else order.totalPrice }

                        paint.color = colorTextDark
                        paint.textSize = 8f
                        paint.typeface = Typeface.DEFAULT

                        canvas.drawText("$dateStr $timeStr", 42f, currentY + 8f, paint)
                        canvas.drawText(tableLabel, 130f, currentY + 8f, paint)
                        canvas.drawText(customer, 195f, currentY + 8f, paint)
                        canvas.drawText(itemsSummary, 280f, currentY + 8f, paint)
                        canvas.drawText(methodTag, 430f, currentY + 8f, paint)

                        paint.typeface = Typeface.DEFAULT_BOLD
                        paint.color = colorForestGreen
                        paint.textAlign = Paint.Align.RIGHT
                        canvas.drawText("₺${"%.2f".format(orderPrice)}", pageWidth - 42f, currentY + 8f, paint)
                        paint.textAlign = Paint.Align.LEFT

                        currentY += 18f
                    }
                }

                // Official Signature & Confirmation Stamp on the Last Page
                if (pageIndex == totalPages - 1) {
                    val sigY = pageHeight - 90f

                    paint.color = colorBorder
                    paint.strokeWidth = 1f
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(32f, sigY, pageWidth - 32f, sigY, paint)

                    paint.style = Paint.Style.FILL
                    paint.color = colorTextMuted
                    paint.textSize = 8f
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

                    // Signatures
                    canvas.drawText("Kasa / Barista Teslim:", 50f, sigY + 18f, paint)
                    canvas.drawText("İmza: ___________________", 50f, sigY + 45f, paint)

                    canvas.drawText("İşletme Sahibi Onay:", pageWidth - 190f, sigY + 18f, paint)
                    canvas.drawText("İmza: ___________________", pageWidth - 190f, sigY + 45f, paint)

                    // Footer text
                    paint.color = colorTextMuted
                    paint.textSize = 7f
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
                    canvas.drawText("Bu belge $restaurantName Gerze şubesine ait resmi haftalık kasa mutabakat raporudur. © 2026 Sade.C", 32f, pageHeight - 16f, paint)
                }

                pdfDoc.finishPage(page)
            }

            // Save PDF to cache and launch share intent
            val cacheDir = File(context.cacheDir, "weekly_pdf_reports")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val cleanPeriod = if (weekPeriod.isNotBlank()) weekPeriod.replace(" ", "_") else SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())
            val fileName = "SadeC_Haftalik_Rapor_${cleanPeriod}.pdf"
            val file = File(cacheDir, fileName)
            val fos = FileOutputStream(file)
            pdfDoc.writeTo(fos)
            pdfDoc.close()
            fos.flush()
            fos.close()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$restaurantName - Haftalık Satış Raporu ($cleanPeriod)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Haftalık PDF Raporunu İndir / Paylaş").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })

            Toast.makeText(context, "Haftalık PDF raporu gün gün dökümlü olarak hazırlandı! 📄✅", Toast.LENGTH_LONG).show()
            onSuccess()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF oluşturulurken hata: ${e.message}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    private fun drawMiniKpi(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        title: String,
        amount: String,
        textColor: Int,
        bgColor: Int,
        borderColor: Int,
        isDark: Boolean = false
    ) {
        paint.color = bgColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(x, y, x + w, y + h, 6f, 6f, paint)

        paint.color = borderColor
        paint.strokeWidth = 0.8f
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(x, y, x + w, y + h, 6f, 6f, paint)

        paint.style = Paint.Style.FILL
        paint.color = if (isDark) Color.WHITE.copyAlpha(0.85f) else 0xFF5A6E65.toInt()
        paint.textSize = 7f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(title, x + 6f, y + 15f, paint)

        paint.color = textColor
        paint.textSize = 11.5f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(amount, x + 6f, y + 34f, paint)
    }

    private fun Int.copyAlpha(alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (this and 0x00FFFFFF) or (a shl 24)
    }
}
