package com.example.sadec.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.sadec.data.model.Order
import com.example.sadec.ui.screens.ProductSaleStat
import com.example.sadec.ui.screens.TableSaleStat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object DailyZReportPdfGenerator {

    /**
     * Generates a formal, beautiful A4 Daily Z-Report PDF ("GÜNLÜK RESMİ Z-RAPORU").
     * Contains:
     * 1. Official Header & Z-Report Number & Date/Time
     * 2. Complete Financial Summary (Total Net Revenue, Card, Cash, Transfer, Complimentary/Discount)
     * 3. Operational KPIs (Total Orders, Total Products Sold)
     * 4. Top Selling Products Table
     * 5. Table-by-Table Revenue Breakdown
     * 6. Detailed Chronological Orders Log
     * 7. Official Closing Signature & Verification Field
     */
    fun generateAndShareDailyZReportPdf(
        context: Context,
        orders: List<Order>,
        productStats: List<ProductSaleStat> = emptyList(),
        tableStats: List<TableSaleStat> = emptyList(),
        restaurantName: String = "Sade.C Kahve Gerze",
        onSuccess: () -> Unit = {}
    ): File? {
        val completedOrders = orders.filter { it.status == "delivered" || it.items.any { item -> item.isPaid } }

        val totalNet = completedOrders.sumOf { it.paidAmount().let { p -> if (p > 0) p else it.totalPrice } }
        val totalCard = completedOrders.sumOf { it.cardPaidAmount() }
        val totalCash = completedOrders.sumOf { it.cashPaidAmount() }
        val totalTransfer = completedOrders.sumOf { it.transferPaidAmount() }
        val totalComp = completedOrders.sumOf { it.complimentaryAmount() }
        val totalItemsSold = completedOrders.sumOf { it.items.sumOf { i -> i.quantity } }

        try {
            val pdfDoc = PdfDocument()
            val pageWidth = 595 // A4 standard width
            val pageHeight = 842 // A4 standard height

            // Palette
            val colorForestGreen = 0xFF1E3A2F.toInt()
            val colorSageGreen = 0xFF2D5341.toInt()
            val colorWarmGold = 0xFFC59F60.toInt()
            val colorSoftMint = 0xFFEBF3EE.toInt()
            val colorTextDark = 0xFF1A2420.toInt()
            val colorTextMuted = 0xFF5A6E65.toInt()
            val colorBorder = 0xFFD2E0D8.toInt()

            val sdfDate = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
            val sdfDateFull = SimpleDateFormat("dd MMMM yyyy, EEEE", Locale("tr", "TR"))
            val sdfTime = SimpleDateFormat("HH:mm", Locale("tr", "TR"))
            val sdfFile = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
            val dateStr = sdfDate.format(Date())
            val dateFullStr = sdfDateFull.format(Date())
            val timeStr = sdfTime.format(Date())
            val zReportNo = "Z-${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}"

            // Chunk orders for pagination if there are many orders
            val itemsPerPage = 16
            val orderChunks = completedOrders.chunked(itemsPerPage).ifEmpty { listOf(emptyList()) }
            val totalPages = maxOf(1, orderChunks.size)

            orderChunks.forEachIndexed { pageIndex, pageOrders ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas

                canvas.drawColor(Color.WHITE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)

                // 1. Header Banner
                paint.color = colorForestGreen
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 95f, paint)

                // Gold Accent Stripe
                paint.color = colorWarmGold
                paint.strokeWidth = 3f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(0f, 95f, pageWidth.toFloat(), 95f, paint)

                // Header Title
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                paint.textSize = 20f
                canvas.drawText(restaurantName.uppercase(), 32f, 42f, paint)

                // Subtitle / Document Type
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                paint.textSize = 12f
                paint.color = colorWarmGold
                canvas.drawText("GÜNLÜK RESMİ Z-RAPORU & KASA MUTABAKAT BELGESİ", 32f, 62f, paint)

                // Date and Z-No on Top Right
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                paint.textSize = 9.5f
                paint.color = Color.WHITE
                canvas.drawText("Rapor No: $zReportNo", pageWidth - 160f, 38f, paint)
                canvas.drawText("Tarih: $dateStr • $timeStr", pageWidth - 160f, 54f, paint)
                canvas.drawText("Sayfa: ${pageIndex + 1} / $totalPages", pageWidth - 160f, 70f, paint)

                var yPos = 118f

                // PAGE 1: Financial Summary & Analytics Breakdown
                if (pageIndex == 0) {
                    // Date banner
                    paint.color = colorSoftMint
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(32f, yPos, pageWidth - 32f, yPos + 30f, 8f, 8f, paint)

                    paint.color = colorForestGreen
                    paint.textSize = 11f
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    canvas.drawText("📅 Gün Sonu Kasa Dönemi: $dateFullStr", 44f, yPos + 19f, paint)

                    yPos += 42f

                    // 4 Financial Summary Boxes (Card, Cash, Transfer, Complimentary) + Net Total
                    val boxW = (pageWidth - 64f - 24f) / 3f
                    val boxH = 50f

                    // Box 1: Kredi Kartı
                    drawSummaryCard(canvas, paint, 32f, yPos, boxW, boxH, "KREDİ KARTI / POS", "₺${"%.2f".format(totalCard)}", colorForestGreen, colorSoftMint, colorBorder)
                    // Box 2: Nakit Kasa
                    drawSummaryCard(canvas, paint, 32f + boxW + 12f, yPos, boxW, boxH, "NAKİT TAHSİLAT", "₺${"%.2f".format(totalCash)}", colorForestGreen, colorSoftMint, colorBorder)
                    // Box 3: Havale / EFT
                    drawSummaryCard(canvas, paint, 32f + (boxW + 12f) * 2, yPos, boxW, boxH, "HAVALE / EFT / FAST", "₺${"%.2f".format(totalTransfer)}", colorForestGreen, colorSoftMint, colorBorder)

                    yPos += boxH + 10f

                    // Box 4: İkram / İndirim & Box 5: TOPLAM NET CİRO
                    val largeBoxW = (pageWidth - 64f - 12f) / 2f
                    drawSummaryCard(canvas, paint, 32f, yPos, largeBoxW, boxH, "İKRAM & İNDİRİM TOPLAMI", "₺${"%.2f".format(totalComp)}", colorTextMuted, colorSoftMint, colorBorder)
                    drawSummaryCard(canvas, paint, 32f + largeBoxW + 12f, yPos, largeBoxW, boxH, "★ GÜNLÜK NET CİRO", "₺${"%.2f".format(totalNet)}", colorWarmGold, colorForestGreen, colorWarmGold, isDark = true)

                    yPos += boxH + 20f

                    // Mini Stats Row (Adisyon & Ürün)
                    paint.color = colorTextMuted
                    paint.textSize = 10f
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    canvas.drawText("TOPLAM ADİSYON / MASA: ${completedOrders.size} Adet", 34f, yPos, paint)
                    canvas.drawText("TOPLAM SATILAN ÜRÜN: $totalItemsSold Adet", 280f, yPos, paint)

                    yPos += 14f

                    // Top Products Section
                    paint.color = colorForestGreen
                    paint.textSize = 11.5f
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    canvas.drawText("GÜNÜN EN ÇOK SATAN ÜRÜNLERİ", 32f, yPos, paint)

                    yPos += 8f
                    paint.color = colorWarmGold
                    paint.strokeWidth = 1.5f
                    canvas.drawLine(32f, yPos, 220f, yPos, paint)

                    yPos += 14f

                    // Top Products Mini Table Header
                    paint.style = Paint.Style.FILL
                    paint.color = colorForestGreen
                    canvas.drawRect(32f, yPos, pageWidth - 32f, yPos + 18f, paint)

                    paint.color = Color.WHITE
                    paint.textSize = 9f
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    canvas.drawText("Sıra", 40f, yPos + 12f, paint)
                    canvas.drawText("Ürün Adı", 75f, yPos + 12f, paint)
                    canvas.drawText("Satılan Adet", 340f, yPos + 12f, paint)
                    canvas.drawText("Toplam Tutar (TL)", pageWidth - 120f, yPos + 12f, paint)

                    yPos += 18f

                    val topProducts = productStats.take(6)
                    if (topProducts.isEmpty()) {
                        paint.color = colorTextMuted
                        paint.textSize = 9.5f
                        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                        canvas.drawText("Günün tamamlanmış ürün satışı bulunmuyor.", 40f, yPos + 14f, paint)
                        yPos += 20f
                    } else {
                        topProducts.forEachIndexed { pIdx, pStat ->
                            paint.color = if (pIdx % 2 == 0) Color.WHITE else 0xFFF7FAF8.toInt()
                            paint.style = Paint.Style.FILL
                            canvas.drawRect(32f, yPos, pageWidth - 32f, yPos + 18f, paint)

                            paint.color = colorBorder
                            paint.strokeWidth = 0.5f
                            paint.style = Paint.Style.STROKE
                            canvas.drawRect(32f, yPos, pageWidth - 32f, yPos + 18f, paint)

                            paint.style = Paint.Style.FILL
                            paint.color = colorTextDark
                            paint.textSize = 9f
                            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                            canvas.drawText("${pIdx + 1}", 42f, yPos + 12f, paint)
                            canvas.drawText(pStat.name, 75f, yPos + 12f, paint)
                            canvas.drawText("${pStat.totalQuantity} adet", 340f, yPos + 12f, paint)

                            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                            paint.color = colorForestGreen
                            canvas.drawText("₺${"%.2f".format(pStat.totalRevenue)}", pageWidth - 120f, yPos + 12f, paint)

                            yPos += 18f
                        }
                    }

                    yPos += 16f
                }

                // Table for Chronological Order Log
                paint.color = colorForestGreen
                paint.textSize = 11f
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                val logHeaderTitle = if (totalPages > 1) "GÜNLÜK ADİSYON & TAHSİLAT DÖKÜMÜ (Bölüm ${pageIndex + 1}/$totalPages)" else "GÜNLÜK ADİSYON & TAHSİLAT DÖKÜMÜ"
                canvas.drawText(logHeaderTitle, 32f, yPos, paint)

                yPos += 6f
                paint.color = colorWarmGold
                paint.strokeWidth = 1.5f
                canvas.drawLine(32f, yPos, 230f, yPos, paint)

                yPos += 12f

                // Table Header
                paint.style = Paint.Style.FILL
                paint.color = colorForestGreen
                canvas.drawRect(32f, yPos, pageWidth - 32f, yPos + 18f, paint)

                paint.color = Color.WHITE
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                canvas.drawText("Saat", 40f, yPos + 12f, paint)
                canvas.drawText("Masa", 75f, yPos + 12f, paint)
                canvas.drawText("Müşteri", 125f, yPos + 12f, paint)
                canvas.drawText("Sipariş Kalemleri", 200f, yPos + 12f, paint)
                canvas.drawText("Ödeme", 410f, yPos + 12f, paint)
                canvas.drawText("Tutar (TL)", pageWidth - 80f, yPos + 12f, paint)

                yPos += 18f

                pageOrders.forEachIndexed { oIdx, order ->
                    val oTime = order.createdAt?.let { sdfTime.format(it) } ?: "--"
                    val oTable = order.tableLabel.ifBlank { "Masa" }
                    val oCustomer = order.customerName.ifBlank { "Misafir" }.take(12)
                    val oProducts = order.items.joinToString(", ") { "${it.quantity}x ${it.name}" }.take(32)
                    val oMethod = when {
                        order.cardPaidAmount() > 0 -> "Kredi Kartı"
                        order.cashPaidAmount() > 0 -> "Nakit"
                        order.transferPaidAmount() > 0 -> "Havale"
                        order.complimentaryAmount() > 0 -> "İkram"
                        else -> "Kart"
                    }
                    val oTotal = order.paidAmount().let { if (it > 0) it else order.totalPrice }

                    paint.color = if (oIdx % 2 == 0) Color.WHITE else 0xFFF8FAF9.toInt()
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(32f, yPos, pageWidth - 32f, yPos + 17f, paint)

                    paint.color = colorBorder
                    paint.strokeWidth = 0.5f
                    paint.style = Paint.Style.STROKE
                    canvas.drawRect(32f, yPos, pageWidth - 32f, yPos + 17f, paint)

                    paint.style = Paint.Style.FILL
                    paint.color = colorTextDark
                    paint.textSize = 8f
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    canvas.drawText(oTime, 40f, yPos + 11f, paint)
                    canvas.drawText(oTable, 75f, yPos + 11f, paint)
                    canvas.drawText(oCustomer, 125f, yPos + 11f, paint)
                    canvas.drawText(oProducts, 200f, yPos + 11f, paint)
                    canvas.drawText(oMethod, 410f, yPos + 11f, paint)

                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    paint.color = colorForestGreen
                    canvas.drawText("₺${"%.2f".format(oTotal)}", pageWidth - 80f, yPos + 11f, paint)

                    yPos += 17f
                }

                // Official Signature & Confirmation Stamp on the Last Page
                if (pageIndex == totalPages - 1) {
                    val sigY = pageHeight - 110f

                    paint.color = colorBorder
                    paint.strokeWidth = 1f
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(32f, sigY, pageWidth - 32f, sigY, paint)

                    paint.style = Paint.Style.FILL
                    paint.color = colorTextMuted
                    paint.textSize = 8.5f
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

                    // Signatures
                    canvas.drawText("Kasiyer / Barista Teslim:", 50f, sigY + 20f, paint)
                    canvas.drawText("İmza: ___________________", 50f, sigY + 50f, paint)

                    canvas.drawText("İşletme Yetkilisi Onay:", pageWidth - 200f, sigY + 20f, paint)
                    canvas.drawText("İmza: ___________________", pageWidth - 200f, sigY + 50f, paint)

                    // Footer text
                    paint.color = colorTextMuted
                    paint.textSize = 7.5f
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
                    canvas.drawText("Bu belge $restaurantName Gerze şubesi resmi gün sonu Z-Raporu mutabakat dökümüdür. © 2026 Sade.C", 32f, pageHeight - 20f, paint)
                }

                pdfDoc.finishPage(page)
            }

            // Save PDF to cache and launch share intent
            val cacheDir = File(context.cacheDir, "daily_z_reports")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val fileName = "SadeC_GunSonu_Z_Raporu_${sdfFile.format(Date())}.pdf"
            val file = File(cacheDir, fileName)
            val fos = FileOutputStream(file)
            pdfDoc.writeTo(fos)
            pdfDoc.close()
            fos.flush()
            fos.close()

            ReportDownloader.saveToDownloadsAndOpen(
                context = context,
                sourceFile = file,
                mimeType = "application/pdf",
                displayName = fileName,
                successMessage = "📥 Gün Sonu Z-Raporu İndirilenler klasörüne kaydedildi! 📄✨"
            )

            onSuccess()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF oluşturulurken hata: ${e.message}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    private fun drawSummaryCard(
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
        // Card Background
        paint.color = bgColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(x, y, x + w, y + h, 8f, 8f, paint)

        // Card Border
        paint.color = borderColor
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(x, y, x + w, y + h, 8f, 8f, paint)

        // Title
        paint.style = Paint.Style.FILL
        paint.color = if (isDark) Color.WHITE.copyAlpha(0.85f) else 0xFF5A6E65.toInt()
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(title, x + 10f, y + 18f, paint)

        // Amount
        paint.color = textColor
        paint.textSize = 13.5f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(amount, x + 10f, y + 38f, paint)
    }

    private fun Int.copyAlpha(alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (this and 0x00FFFFFF) or (a shl 24)
    }
}
