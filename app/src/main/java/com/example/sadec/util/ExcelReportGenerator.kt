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
     * containing every detail of the weekly orders:
     * - Tarih (Satıldığı Gün)
     * - Saat (Satıldığı Saat)
     * - Masa Adı / No
     * - Müşteri Adı (Kim tarafından sipariş edildi)
     * - Sipariş Edilen Ürünler ve Kalemler
     * - Kaç Adet Sipariş Edildi
     * - Ödeme Yöntemi (Nakit, Kredi Kartı, Havale, İkram)
     * - İndirim / İkram Tutarı (TL)
     * - Tahsilat Tutarı (₺)
     * - Ödeme Durumu
     * - Sipariş Notu
     *
     * Opens system share/save dialog for Excel / Sheets.
     */
    fun generateAndShareExcelReport(
        context: Context,
        orders: List<Order>,
        restaurantName: String = "Sade.C Kahve Gerze",
        weekPeriod: String = "",
        onSuccess: () -> Unit = {}
    ): File? {
        val completedOrders = orders.filter { !it.isArchived && it.status != "cancelled" }
        val sdfDate = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
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
            writer.write("\"$restaurantName - HAFTALIK RESMİ SATIŞ & KASA GELİR RAPORU\"\n")
            writer.write("\"Rapor Dönemi:\";\"${if (weekPeriod.isNotBlank()) weekPeriod else "Cari Hafta"}\"\n")
            writer.write("\"Rapor Oluşturma Tarihi:\";\"$nowFormatted\"\n")
            writer.write("\"Toplam Adisyon Sayısı:\";\"${completedOrders.size} Adet\"\n\n")

            // Column Headers
            writer.write("\"Sıra No\";\"Satış Tarihi\";\"Satış Saati\";\"Masa\";\"Müşteri Adı\";\"Sipariş Edilen Ürünler\";\"Toplam Ürün Adedi\";\"Ödeme Yöntemi\";\"İndirim/İkram (TL)\";\"Net Tahsilat (TL)\";\"Ödeme Durumu\";\"Müşteri/Garson Notu\"\n")

            var totalNetRevenue = 0.0
            var totalItemCount = 0
            var totalCard = 0.0
            var totalCash = 0.0
            var totalTransfer = 0.0
            var totalComplimentaryOrDiscount = 0.0

            completedOrders.forEachIndexed { index, order ->
                val dateStr = order.createdAt?.let { sdfDate.format(it) } ?: "--"
                val timeStr = order.createdAt?.let { sdfTime.format(it) } ?: "--"
                val tableStr = order.tableLabel.ifBlank { "Masa" }
                val customerStr = order.customerName.ifBlank { "Misafir" }

                // Products summary (e.g. 2x Espresso, 1x San Sebastian)
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

                totalNetRevenue += orderNetRevenue
                totalItemCount += orderItemsCount
                totalCard += order.cardPaidAmount()
                totalCash += order.cashPaidAmount()
                totalTransfer += order.transferPaidAmount()
                totalComplimentaryOrDiscount += orderDiscount

                // Clean CSV Row (semicolon separated for European/Turkish Excel standard)
                writer.write("${index + 1};\"$dateStr\";\"$timeStr\";\"$tableStr\";\"$customerStr\";\"$productsDetail\";$orderItemsCount;\"$methodsUsed\";${"%.2f".format(orderDiscount).replace(".", ",")};${"%.2f".format(orderNetRevenue).replace(".", ",")};\"$paymentStatus\";\"$noteStr\"\n")
            }

            // Summary Section at bottom
            writer.write("\n\"---\";\"---\";\"---\";\"---\";\"---\";\"---\";\"---\";\"---\";\"---\";\"---\";\"---\";\"---\"\n")
            writer.write("\"ÖZET TAHSİLAT TABLOSU\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"\n")
            writer.write("\"Kredi Kartı Tahsilat:\";\"₺${"%.2f".format(totalCard).replace(".", ",")}\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"\n")
            writer.write("\"Nakit Tahsilat:\";\"₺${"%.2f".format(totalCash).replace(".", ",")}\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"\n")
            writer.write("\"Havale/EFT/FAST Tahsilat:\";\"₺${"%.2f".format(totalTransfer).replace(".", ",")}\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"\n")
            writer.write("\"İkram / İndirim Toplamı:\";\"₺${"%.2f".format(totalComplimentaryOrDiscount).replace(".", ",")}\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"\n")
            writer.write("\"GENEL TOPLAM SATILAN ÜRÜN:\";\"$totalItemCount Adet\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"\n")
            writer.write("\"GENEL NET CİRO:\";\"₺${"%.2f".format(totalNetRevenue).replace(".", ",")}\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"\n")

            writer.flush()
            writer.close()
            fos.close()

            // Share / Open Intent
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$restaurantName - Haftalık Kasa Raporu ($cleanPeriod)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Haftalık Excel Raporunu İndir / Paylaş").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })

            Toast.makeText(context, "Haftalık Excel raporu oluşturuldu! 📊📥", Toast.LENGTH_LONG).show()
            onSuccess()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Excel raporu oluşturulurken hata: ${e.message}", Toast.LENGTH_LONG).show()
            return null
        }
    }
}
