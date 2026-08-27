package com.example.sadec.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.sadec.R
import com.example.sadec.data.model.TableItem
import java.io.File
import java.io.FileOutputStream

object QrPdfGenerator {

    /**
     * Generates a luxury, high-resolution printable PDF containing QR stand cards for all tables.
     * - Formats and fits all table QR stands into exactly 2 printable A4 pages.
     * - Top Header has a distinctive Forest Green circular logo badge with gold ring.
     * - QR Codes are 100% clean and plain (no center watermark).
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
            val pageWidth = 595 // A4 standard width in points (72 DPI)
            val pageHeight = 842 // A4 standard height in points (72 DPI)

            // Brand Colors
            val colorForestGreen = 0xFF1E3A2F.toInt()
            val colorSageGreen = 0xFF2D5341.toInt()
            val colorWarmGold = 0xFFC59F60.toInt()
            val colorBgCream = 0xFFFAF8F5.toInt()

            // Decode official Sade.C Logo
            val logoBitmap = try {
                BitmapFactory.decodeResource(context.resources, R.drawable.sadec_logo)
            } catch (e: Exception) {
                null
            }

            // Natural alphanumeric sort for tables (e.g. BAR, DIŞ 1, DIŞ 2, İÇ 1...)
            val sortedTables = tables.sortedWith { t1, t2 ->
                val l1 = t1.label.trim()
                val l2 = t2.label.trim()
                val regex = "(\\D+)|(\\d+)".toRegex()
                val m1 = regex.findAll(l1).map { it.value }.toList()
                val m2 = regex.findAll(l2).map { it.value }.toList()
                var result = 0
                for (i in 0 until minOf(m1.size, m2.size)) {
                    val p1 = m1[i]
                    val p2 = m2[i]
                    val n1 = p1.toIntOrNull()
                    val n2 = p2.toIntOrNull()
                    if (n1 != null && n2 != null) {
                        result = n1.compareTo(n2)
                    } else {
                        result = p1.compareTo(p2, ignoreCase = true)
                    }
                    if (result != 0) break
                }
                if (result == 0) m1.size.compareTo(m2.size) else result
            }

            val totalTables = sortedTables.size

            // Target exactly 2 pages: determine cards per page and grid dimensions
            val cardsPerPage: Int
            val colsCount = 2
            val rowsCount: Int

            when {
                totalTables <= 4 -> {
                    cardsPerPage = 4
                    rowsCount = 2
                }
                totalTables <= 8 -> {
                    // 5 to 8 tables -> 4 cards per page (2 pages total, 2x2 grid)
                    cardsPerPage = 4
                    rowsCount = 2
                }
                totalTables <= 12 -> {
                    // 9 to 12 tables -> 6 cards per page (2 pages total, 2x3 grid)
                    cardsPerPage = 6
                    rowsCount = 3
                }
                else -> {
                    // 13 to 16+ tables -> dynamic split for 2 pages (2x4 grid)
                    cardsPerPage = Math.ceil(totalTables / 2.0).toInt().coerceIn(6, 8)
                    rowsCount = if (cardsPerPage <= 6) 3 else 4
                }
            }

            val chunks = sortedTables.chunked(cardsPerPage)
            val totalPages = chunks.size

            // Geometry calculations
            val pageMarginH = 26f
            val pageMarginV = 26f
            val colSpacing = 16f
            val rowSpacing = when (rowsCount) {
                2 -> 20f
                3 -> 12f
                else -> 10f
            }

            val availableWidth = pageWidth - (pageMarginH * 2)
            val availableHeight = pageHeight - (pageMarginV * 2) - 18f // 18f for footer info

            val cardWidth = (availableWidth - colSpacing) / colsCount
            val cardHeight = (availableHeight - (rowSpacing * (rowsCount - 1))) / rowsCount

            chunks.forEachIndexed { pageIndex, pageTables ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas

                // Background
                canvas.drawColor(Color.WHITE)

                pageTables.forEachIndexed { idx, table ->
                    val col = idx % colsCount
                    val row = idx / colsCount

                    val cardLeft = pageMarginH + col * (cardWidth + colSpacing)
                    val cardTop = pageMarginV + row * (cardHeight + rowSpacing)
                    val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)

                    // 1. Card Container (Soft Cream with Double Green/Gold Frame)
                    val bgPaint = Paint().apply {
                        color = colorBgCream
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    canvas.drawRoundRect(cardRect, 16f, 16f, bgPaint)

                    val borderPaint = Paint().apply {
                        color = colorForestGreen
                        style = Paint.Style.STROKE
                        strokeWidth = if (rowsCount == 2) 2.5f else 1.8f
                        isAntiAlias = true
                    }
                    canvas.drawRoundRect(cardRect, 16f, 16f, borderPaint)

                    val innerBorderPaint = Paint().apply {
                        color = colorWarmGold
                        style = Paint.Style.STROKE
                        strokeWidth = 0.8f
                        isAntiAlias = true
                    }
                    val innerInset = if (rowsCount == 2) 6f else 4f
                    val innerRect = RectF(
                        cardLeft + innerInset,
                        cardTop + innerInset,
                        cardLeft + cardWidth - innerInset,
                        cardTop + cardHeight - innerInset
                    )
                    canvas.drawRoundRect(innerRect, 12f, 12f, innerBorderPaint)

                    // 2. Card Content Layout with Green Circular Logo in Header
                    val cardCenterX = cardLeft + (cardWidth / 2f)

                    when (rowsCount) {
                        2 -> {
                            // LARGE 2x2 LAYOUT (~374pt height)
                            // A) Header Green Logo Badge
                            if (logoBitmap != null) {
                                val logoDiameter = 38f
                                val logoRect = RectF(
                                    cardCenterX - (logoDiameter / 2f),
                                    cardTop + 14f,
                                    cardCenterX + (logoDiameter / 2f),
                                    cardTop + 14f + logoDiameter
                                )
                                drawCircularLogoBadge(
                                    canvas = canvas,
                                    bitmap = logoBitmap,
                                    destRect = logoRect,
                                    bgFillColor = colorForestGreen,
                                    ringColor = colorWarmGold,
                                    ringWidth = 1.2f,
                                    innerPadding = 3.5f
                                )
                            }

                            // B) Title & Subtitle
                            val titlePaint = Paint().apply {
                                color = colorForestGreen
                                textSize = 16f
                                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            val titleY = if (logoBitmap != null) cardTop + 66f else cardTop + 30f
                            canvas.drawText(restaurantName.uppercase(), cardCenterX, titleY, titlePaint)

                            val subPaint = Paint().apply {
                                color = colorWarmGold
                                textSize = 8f
                                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                                letterSpacing = 0.15f
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            canvas.drawText("KAHVENİN EN SAF HALİ • GERZE", cardCenterX, titleY + 13f, subPaint)

                            // C) Pure & Clean QR Code (155pt, no center obstruction)
                            val qrSize = 155f
                            val qrLeft = cardLeft + (cardWidth - qrSize) / 2f
                            val qrTop = titleY + 22f
                            val qrDestRect = RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize)

                            val qrBgRect = RectF(qrLeft - 6f, qrTop - 6f, qrLeft + qrSize + 6f, qrTop + qrSize + 6f)
                            canvas.drawRoundRect(qrBgRect, 12f, 12f, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true })
                            canvas.drawRoundRect(qrBgRect, 12f, 12f, Paint().apply { color = colorWarmGold; style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true })

                            val qrUrl = "$baseUrl?restId=$restaurantId&tableId=${table.id}&key=${table.qrKey}"
                            val qrBitmap = QrCodeGenerator.generateQrBitmap(qrUrl, 400)
                            if (qrBitmap != null) {
                                canvas.drawBitmap(qrBitmap, null, qrDestRect, Paint(Paint.FILTER_BITMAP_FLAG))
                            }

                            // D) Table Badge
                            val badgeW = 200f
                            val badgeH = 40f
                            val badgeY = qrTop + qrSize + 14f
                            val badgeRect = RectF(cardCenterX - (badgeW / 2f), badgeY, cardCenterX + (badgeW / 2f), badgeY + badgeH)
                            canvas.drawRoundRect(badgeRect, 12f, 12f, Paint().apply { color = colorForestGreen; style = Paint.Style.FILL; isAntiAlias = true })

                            val tableTextPaint = Paint().apply {
                                color = Color.WHITE
                                textSize = 19f
                                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            canvas.drawText("MASA: ${table.label.uppercase()}", cardCenterX, badgeY + 27f, tableTextPaint)

                            // E) Footers
                            val instructionPaint = Paint().apply {
                                color = colorSageGreen
                                textSize = 9f
                                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            canvas.drawText("📱 Telefonunuzun kamerası ile QR kodu okutun", cardCenterX, badgeY + badgeH + 18f, instructionPaint)

                            val securityPaint = Paint().apply {
                                color = Color.GRAY
                                textSize = 7.5f
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            canvas.drawText("Güvenli Kod: ${table.qrKey.ifBlank { table.id }}", cardCenterX, badgeY + badgeH + 31f, securityPaint)
                        }
                        3 -> {
                            // COMPACT 2x3 LAYOUT (~248pt height)
                            // A) Header Green Logo Badge & Title
                            if (logoBitmap != null) {
                                val logoDiameter = 26f
                                val logoRect = RectF(
                                    cardCenterX - (logoDiameter / 2f),
                                    cardTop + 8f,
                                    cardCenterX + (logoDiameter / 2f),
                                    cardTop + 8f + logoDiameter
                                )
                                drawCircularLogoBadge(
                                    canvas = canvas,
                                    bitmap = logoBitmap,
                                    destRect = logoRect,
                                    bgFillColor = colorForestGreen,
                                    ringColor = colorWarmGold,
                                    ringWidth = 1f,
                                    innerPadding = 2.5f
                                )
                            }

                            val titlePaint = Paint().apply {
                                color = colorForestGreen
                                textSize = 12.5f
                                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            val titleY = if (logoBitmap != null) cardTop + 45f else cardTop + 20f
                            canvas.drawText(restaurantName.uppercase(), cardCenterX, titleY, titlePaint)

                            val subPaint = Paint().apply {
                                color = colorWarmGold
                                textSize = 6.5f
                                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                                letterSpacing = 0.1f
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            canvas.drawText("KAHVENİN EN SAF HALİ • GERZE", cardCenterX, titleY + 10f, subPaint)

                            // B) Pure & Clean QR Code (105pt, no center obstruction)
                            val qrSize = 105f
                            val qrLeft = cardLeft + (cardWidth - qrSize) / 2f
                            val qrTop = titleY + 16f
                            val qrDestRect = RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize)

                            val qrBgRect = RectF(qrLeft - 4f, qrTop - 4f, qrLeft + qrSize + 4f, qrTop + qrSize + 4f)
                            canvas.drawRoundRect(qrBgRect, 10f, 10f, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true })
                            canvas.drawRoundRect(qrBgRect, 10f, 10f, Paint().apply { color = colorWarmGold; style = Paint.Style.STROKE; strokeWidth = 0.8f; isAntiAlias = true })

                            val qrUrl = "$baseUrl?restId=$restaurantId&tableId=${table.id}&key=${table.qrKey}"
                            val qrBitmap = QrCodeGenerator.generateQrBitmap(qrUrl, 300)
                            if (qrBitmap != null) {
                                canvas.drawBitmap(qrBitmap, null, qrDestRect, Paint(Paint.FILTER_BITMAP_FLAG))
                            }

                            // C) Table Badge
                            val badgeW = 160f
                            val badgeH = 28f
                            val badgeY = qrTop + qrSize + 8f
                            val badgeRect = RectF(cardCenterX - (badgeW / 2f), badgeY, cardCenterX + (badgeW / 2f), badgeY + badgeH)
                            canvas.drawRoundRect(badgeRect, 8f, 8f, Paint().apply { color = colorForestGreen; style = Paint.Style.FILL; isAntiAlias = true })

                            val tableTextPaint = Paint().apply {
                                color = Color.WHITE
                                textSize = 14f
                                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            canvas.drawText("MASA: ${table.label.uppercase()}", cardCenterX, badgeY + 19f, tableTextPaint)

                            // D) Footers
                            val instructionPaint = Paint().apply {
                                color = colorSageGreen
                                textSize = 7.5f
                                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            canvas.drawText("📱 Kameranız ile QR okutarak sipariş verin", cardCenterX, badgeY + badgeH + 12f, instructionPaint)

                            val securityPaint = Paint().apply {
                                color = Color.GRAY
                                textSize = 6f
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            canvas.drawText("Kod: ${table.qrKey.ifBlank { table.id }}", cardCenterX, badgeY + badgeH + 22f, securityPaint)
                        }
                        else -> {
                            // DENSE 2x4 LAYOUT (~184pt height)
                            if (logoBitmap != null) {
                                val logoDiameter = 20f
                                val logoRect = RectF(
                                    cardCenterX - (logoDiameter / 2f),
                                    cardTop + 6f,
                                    cardCenterX + (logoDiameter / 2f),
                                    cardTop + 6f + logoDiameter
                                )
                                drawCircularLogoBadge(
                                    canvas = canvas,
                                    bitmap = logoBitmap,
                                    destRect = logoRect,
                                    bgFillColor = colorForestGreen,
                                    ringColor = colorWarmGold,
                                    ringWidth = 0.8f,
                                    innerPadding = 2f
                                )
                            }

                            val titlePaint = Paint().apply {
                                color = colorForestGreen
                                textSize = 11f
                                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            val titleY = if (logoBitmap != null) cardTop + 35f else cardTop + 16f
                            canvas.drawText(restaurantName.uppercase(), cardCenterX, titleY, titlePaint)

                            // QR Code (80pt, no center obstruction)
                            val qrSize = 80f
                            val qrLeft = cardLeft + (cardWidth - qrSize) / 2f
                            val qrTop = titleY + 6f
                            val qrDestRect = RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize)

                            val qrBgRect = RectF(qrLeft - 3f, qrTop - 3f, qrLeft + qrSize + 3f, qrTop + qrSize + 3f)
                            canvas.drawRoundRect(qrBgRect, 6f, 6f, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true })
                            canvas.drawRoundRect(qrBgRect, 6f, 6f, Paint().apply { color = colorWarmGold; style = Paint.Style.STROKE; strokeWidth = 0.6f; isAntiAlias = true })

                            val qrUrl = "$baseUrl?restId=$restaurantId&tableId=${table.id}&key=${table.qrKey}"
                            val qrBitmap = QrCodeGenerator.generateQrBitmap(qrUrl, 250)
                            if (qrBitmap != null) {
                                canvas.drawBitmap(qrBitmap, null, qrDestRect, Paint(Paint.FILTER_BITMAP_FLAG))
                            }

                            // Table Badge
                            val badgeW = 140f
                            val badgeH = 22f
                            val badgeY = qrTop + qrSize + 6f
                            val badgeRect = RectF(cardCenterX - (badgeW / 2f), badgeY, cardCenterX + (badgeW / 2f), badgeY + badgeH)
                            canvas.drawRoundRect(badgeRect, 6f, 6f, Paint().apply { color = colorForestGreen; style = Paint.Style.FILL; isAntiAlias = true })

                            val tableTextPaint = Paint().apply {
                                color = Color.WHITE
                                textSize = 11.5f
                                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            canvas.drawText("MASA: ${table.label.uppercase()}", cardCenterX, badgeY + 15f, tableTextPaint)

                            // Footers
                            val instructionPaint = Paint().apply {
                                color = colorSageGreen
                                textSize = 6.5f
                                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            canvas.drawText("📱 QR okutarak sipariş verin", cardCenterX, badgeY + badgeH + 9f, instructionPaint)
                        }
                    }
                }

                // Dotted cutting line between columns
                val cutLinePaint = Paint().apply {
                    color = 0xFFD1D5DB.toInt()
                    strokeWidth = 0.8f
                    style = Paint.Style.STROKE
                    pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
                }
                val midX = pageMarginH + cardWidth + (colSpacing / 2f)
                canvas.drawLine(midX, pageMarginV - 6f, midX, pageHeight - pageMarginV - 8f, cutLinePaint)

                // Dotted cutting lines between rows
                for (r in 1 until rowsCount) {
                    val midY = pageMarginV + r * cardHeight + (r - 0.5f) * rowSpacing
                    canvas.drawLine(pageMarginH - 6f, midY, pageWidth - pageMarginH + 6f, midY, cutLinePaint)
                }

                // Page Footer info
                val pageFooterPaint = Paint().apply {
                    color = Color.GRAY
                    textSize = 8.5f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                canvas.drawText(
                    "Sayfa ${pageIndex + 1} / $totalPages • $restaurantName Masa QR Standları • Toplam $totalTables Masa",
                    pageWidth / 2f,
                    pageHeight - 10f,
                    pageFooterPaint
                )

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
                putExtra(Intent.EXTRA_TEXT, "$restaurantName tüm masalar için logolu ve 2 sayfalık A4 baskı QR kod PDF çıktısı.")
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

    /**
     * Draws the Sade.C logo inside a luxury Forest Green circular badge with a Warm Gold border ring.
     */
    private fun drawCircularLogoBadge(
        canvas: Canvas,
        bitmap: Bitmap,
        destRect: RectF,
        bgFillColor: Int,
        ringColor: Int,
        ringWidth: Float = 1.2f,
        innerPadding: Float = 4f
    ) {
        // 1. Draw solid background circle (ForestGreen)
        val bgPaint = Paint().apply {
            color = bgFillColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawOval(destRect, bgPaint)

        // 2. Draw gold ring border
        val borderPaint = Paint().apply {
            color = ringColor
            style = Paint.Style.STROKE
            strokeWidth = ringWidth
            isAntiAlias = true
        }
        canvas.drawOval(destRect, borderPaint)

        // 3. Draw logo bitmap inside circle with padding
        val logoRect = RectF(
            destRect.left + innerPadding,
            destRect.top + innerPadding,
            destRect.right - innerPadding,
            destRect.bottom - innerPadding
        )
        val saveCount = canvas.save()
        val path = Path().apply {
            addOval(logoRect, Path.Direction.CW)
        }
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, null, logoRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restoreToCount(saveCount)
    }
}
