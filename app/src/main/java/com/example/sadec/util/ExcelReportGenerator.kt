package com.example.sadec.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.sadec.data.model.Order
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

object ExcelReportGenerator {

    /**
     * Generates a fully detailed Excel-compatible CSV file (with UTF-8 BOM for Microsoft Excel)
     * containing weekly orders grouped and separated DAY-BY-DAY (Gün Gün Ayrılmış):
     * - Gün Başlığı (Örn: === 25.08.2026 SALI ===)
     * - Tarih & Saat
     * - Masa Adı / No
     * - Müşteri Adı
     * - Sipariş Edilen Ürünler ve Kalemler
     * - Kaç Adet Sipariş Edildi
     * - Ödeme Yöntemi (Nakit, Kredi Kartı, Havale, İkram)
     * - İndirim / İkram Tutarı (TL)
     * - Net Tahsilat Tutarı (₺)
     * - Günlük Alt Toplam ve Genel Haftalık Özet Tablosu
     */
    fun generateAndShareExcelReport(
        context: Context,
        orders: List<Order>,
        restaurantName: String = "Sade.C Kahve Gerze",
        weekPeriod: String = "",
        onSuccess: () -> Unit = {}
    ): File? {
        val completedOrders = orders.filter { !it.isArchived && it.status != "cancelled" }
            .sortedBy { it.createdAt?.time ?: 0L }

        val sdfDate = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
        val sdfDayName = SimpleDateFormat("EEEE", Locale("tr", "TR"))
        val sdfTime = SimpleDateFormat("HH:mm", Locale("tr", "TR"))
        val sdfFull = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("tr", "TR"))
        val nowFormatted = sdfFull.format(Date())

        try {
            val cacheDir = File(context.cacheDir, "weekly_excel_reports")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val cleanPeriod = if (weekPeriod.isNotBlank()) weekPeriod.replace(" ", "_") else SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())
            val fileName = "SadeC_Haftalik_Kasa_Raporu_${cleanPeriod}.csv"
            val file = File(cacheDir, fileName)

            val fos = FileOutputStream(file)
            // Write UTF-8 BOM so Microsoft Excel opens Turkish characters correctly (ç, ğ, ı, ö, ş, ü, İ)
            fos.write(0xEF)
            fos.write(0xBB)
            fos.write(0xBF)

            val writer = OutputStreamWriter(fos, StandardCharsets.UTF_8)

            // Header Section
            writer.write("\"$restaurantName - HAFTALIK RESMİ SATIŞ & KASA GELİR RAPORU (GÜN GÜN AYRILMIŞ)\"\n")
            writer.write("\"Rapor Dönemi:\";\"${if (weekPeriod.isNotBlank()) weekPeriod else "Cari Hafta"}\"\n")
            writer.write("\"Rapor Oluşturma Tarihi:\";\"$nowFormatted\"\n")
            writer.write("\"Toplam Adisyon Sayısı:\";\"${completedOrders.size} Adet\"\n\n")

            // Column Headers
            writer.write("\"Sıra No\";\"Satış Tarihi\";\"Gün\";\"Satış Saati\";\"Masa\";\"Müşteri Adı\";\"Sipariş Edilen Ürünler\";\"Toplam Ürün Adedi\";\"Ödeme Yöntemi\";\"İndirim/İkram (TL)\";\"Net Tahsilat (TL)\";\"Ödeme Durumu\";\"Müşteri/Garson Notu\"\n")

            var totalNetRevenue = 0.0
            var totalItemCount = 0
            var totalCard = 0.0
            var totalCash = 0.0
            var totalTransfer = 0.0
            var totalComplimentaryOrDiscount = 0.0

            // Group orders day-by-day
            val ordersByDay = completedOrders.groupBy { order ->
                order.createdAt?.let { sdfDate.format(it) } ?: "Tarihsiz"
            }

            var globalRowIndex = 1

            ordersByDay.forEach { (dateKey, dayOrders) ->
                val sampleDate = dayOrders.firstOrNull()?.createdAt
                val dayNameStr = sampleDate?.let { sdfDayName.format(it).uppercase(Locale("tr")) } ?: ""

                // Day Banner in CSV
                writer.write("\n\"---\";\"=== $dateKey $dayNameStr ===\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"\n")

                var dayRevenue = 0.0
                var dayItemsCount = 0
                var dayCard = 0.0
                var dayCash = 0.0
                var dayTransfer = 0.0

                dayOrders.forEach { order ->
                    val dateStr = order.createdAt?.let { sdfDate.format(it) } ?: "--"
                    val dayStr = order.createdAt?.let { sdfDayName.format(it) } ?: "--"
                    val timeStr = order.createdAt?.let { sdfTime.format(it) } ?: "--"
                    val tableStr = order.tableLabel.ifBlank { "Masa" }
                    val customerStr = order.customerName.ifBlank { "Misafir" }

                    val productsDetail = order.items.joinToString(" + ") {
                        val compTag = if (it.isComplimentary) " (İKRAM)" else if (it.discountAmount > 0) " (-₺${"%.2f".format(it.discountAmount)})" else ""
                        "${it.quantity}x ${it.name}$compTag (₺${"%.2f".format(it.effectivePrice())})"
                    }
                    val orderItemsCount = order.items.sumOf { it.quantity }
                    val orderNetRevenue = order.paidAmount().let { if (it > 0) it else order.totalPrice }
                    val orderDiscount = order.items.sumOf { it.discountAmount } + order.complimentaryAmount()

                    val methodsUsed = order.items.mapNotNull {
                        when (it.paymentMethod) {
                            "card" -> "Kredi Kartı"
                            "cash" -> "Nakit"
                            "transfer" -> "Havale"
                            "complimentary" -> "İkram"
                            else -> null
                        }
                    }.distinct().joinToString(", ").ifBlank {
                        when (order.paymentMethod) {
                            "card" -> "Kredi Kartı"
                            "cash" -> "Nakit"
                            "transfer" -> "Havale"
                            "complimentary" -> "İkram"
                            else -> "Kredi Kartı"
                        }
                    }

                    val paymentStatus = if (order.isFullyPaid() || order.status == "delivered") "Tahsil Edildi (Ödendi)" else "Açık / Bekliyor"
                    val noteStr = order.note.ifBlank { "-" }.replace("\"", "'")

                    dayRevenue += orderNetRevenue
                    dayItemsCount += orderItemsCount
                    dayCard += order.cardPaidAmount()
                    dayCash += order.cashPaidAmount()
                    dayTransfer += order.transferPaidAmount()

                    totalNetRevenue += orderNetRevenue
                    totalItemCount += orderItemsCount
                    totalCard += order.cardPaidAmount()
                    totalCash += order.cashPaidAmount()
                    totalTransfer += order.transferPaidAmount()
                    totalComplimentaryOrDiscount += orderDiscount

                    writer.write("$globalRowIndex;\"$dateStr\";\"$dayStr\";\"$timeStr\";\"$tableStr\";\"$customerStr\";\"$productsDetail\";$orderItemsCount;\"$methodsUsed\";${"%.2f".format(orderDiscount).replace(".", ",")};${"%.2f".format(orderNetRevenue).replace(".", ",")};\"$paymentStatus\";\"$noteStr\"\n")
                    globalRowIndex++
                }

                // Day Subtotal Row
                writer.write("\"*\";\"[GÜN TOPLAMI: $dateKey $dayNameStr]\";\"\";\"\";\"\";\"Satılan: $dayItemsCount Adet\";\"\";\"\";\"Kart: ₺${"%.2f".format(dayCard).replace(".", ",")} | Nakit: ₺${"%.2f".format(dayCash).replace(".", ",")}\";\"\";\"₺${"%.2f".format(dayRevenue).replace(".", ",")}\";\"GÜN CİROSU\";\"\"\n")
            }

            // Summary Section at bottom
            writer.write("\n\"=====================\";\"=====================\";\"=====================\";\"=====================\";\"=====================\";\"=====================\";\"=====================\";\"=====================\";\"=====================\";\"=====================\";\"=====================\";\"=====================\";\"=====================\"\n")
            writer.write("\"GENEL HAFTALIK ÖZET MUTABAKAT TABLOSU\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"\n")
            writer.write("\"Haftalık Kredi Kartı / POS Tahsilatı:\";\"₺${"%.2f".format(totalCard).replace(".", ",")}\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"\n")
            writer.write("\"Haftalık Nakit Kasa Tahsilatı:\";\"₺${"%.2f".format(totalCash).replace(".", ",")}\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"\n")
            writer.write("\"Haftalık Havale/EFT/FAST Tahsilatı:\";\"₺${"%.2f".format(totalTransfer).replace(".", ",")}\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"\n")
            writer.write("\"Haftalık İkram / İndirim Toplamı:\";\"₺${"%.2f".format(totalComplimentaryOrDiscount).replace(".", ",")}\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"\n")
            writer.write("\"HAFTALIK TOPLAM SATILAN ÜRÜN:\";\"$totalItemCount Adet\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"\n")
            writer.write("\"HAFTALIK GENEL NET CİRO:\";\"₺${"%.2f".format(totalNetRevenue).replace(".", ",")}\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"\n")

            writer.flush()
            writer.close()
            fos.close()

            ReportDownloader.saveToDownloadsAndOpen(
                context = context,
                sourceFile = file,
                mimeType = "text/csv",
                displayName = fileName,
                successMessage = "📥 Haftalık Excel raporu İndirilenler klasörüne kaydedildi! 📊✅"
            )

            onSuccess()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Excel raporu oluşturulurken hata: ${e.message}", Toast.LENGTH_LONG).show()
            return null
        }
    }
}
