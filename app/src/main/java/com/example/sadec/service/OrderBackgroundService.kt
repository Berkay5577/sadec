package com.example.sadec.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.sadec.MainActivity
import com.example.sadec.R
import com.example.sadec.data.model.Order
import com.example.sadec.util.NotificationPreferences
import com.example.sadec.util.SoundPlayer
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Telefon ekranı kapalıyken veya uygulama arka plandayken dahi Firestore'u canlı dinleyip
 * yeni gelen siparişlerde ekranı uyandıran (WakeLock), yüksek öncelikli bildirim çıkartan ve
 * ses/titreşim çalan kesintisiz ön plan servisi (Foreground Service).
 */
class OrderBackgroundService : Service() {

    private var firestoreListener: ListenerRegistration? = null
    private val notifiedOrderIds = HashSet<String>()
    private var isFirstSnapshot = true

    companion object {
        const val CHANNEL_SERVICE_ID = "sadec_background_service_channel"
        const val CHANNEL_ORDER_ALERT_ID = "sadec_high_priority_orders"
        const val NOTIFICATION_SERVICE_ID = 9901
        private const val RESTAURANT_ID = "sadec-gerze"

        fun startService(context: Context) {
            val intent = Intent(context, OrderBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, OrderBackgroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIFICATION_SERVICE_ID, buildForegroundNotification())
        startListeningOrders()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android sistemi servisi sonlandırsa dahi otomatik yeniden başlatır
        return START_STICKY
    }

    override fun onDestroy() {
        firestoreListener?.remove()
        super.onDestroy()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Arka plan servis durum bildirimi (Sessiz)
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "Sipariş Dinleme Servisi",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Uygulama kapalıyken siparişlerin kaçmaması için arka plan servisi"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(serviceChannel)

            // 2. Yüksek Öncelikli Yeni Sipariş Uyarısı (Ekranı uyandıran, kilit ekranında görünen)
            val soundUri = SoundPlayer.getOrderSoundUri(this)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val alertChannel = NotificationChannel(
                CHANNEL_ORDER_ALERT_ID,
                "Yeni Sipariş Uyarıları (Ring of Silence)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Yeni masa siparişlerinde çalan Ring of Silence kilit ekranı uyarısı"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(true)
            }
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
            .setContentTitle("Sade.C Sipariş Takibi Aktif 🟢")
            .setContentText("Telefon kapalıyken bile siparişler anında ekrana düşer.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun startListeningOrders() {
        val db = FirebaseFirestore.getInstance()
        firestoreListener = db.collection("restaurants")
            .document(RESTAURANT_ID)
            .collection("orders")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                if (isFirstSnapshot) {
                    // İlk açılışta eski siparişleri hafızaya al, bildirim patlaması yapma
                    for (doc in snapshot.documents) {
                        notifiedOrderIds.add(doc.id)
                    }
                    isFirstSnapshot = false
                    return@addSnapshotListener
                }

                for (change in snapshot.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED) {
                        val order = change.document.toObject(Order::class.java).copy(id = change.document.id)
                        if (!notifiedOrderIds.contains(order.id) && (order.status == "pending" || order.status.isEmpty())) {
                            notifiedOrderIds.add(order.id)
                            handleNewOrderNotification(order)
                        }
                    }
                }
            }
    }

    private fun handleNewOrderNotification(order: Order) {
        if (!NotificationPreferences.isDeviceNotificationsEnabled(this)) {
            return
        }

        // 1. EKRANI UYANDIR (Ekran kapalıysa açar)
        if (NotificationPreferences.isWakeScreenEnabled(this)) {
            wakeUpDeviceScreen()
        }

        // 2. SES VE TİTREŞİM ÇAL
        if (NotificationPreferences.isSoundEnabled(this)) {
            SoundPlayer.playOrderAlert(this)
        }

        // 3. KİLİT EKRANI VE DURUM ÇUBUĞU BİLDİRİMİ GÖSTER
        showHighPriorityNotification(order)
    }

    private fun wakeUpDeviceScreen() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null) {
                @Suppress("DEPRECATION")
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            PowerManager.ON_AFTER_RELEASE,
                    "sadec:OrderWakeLock"
                )
                wakeLock.acquire(10000) // 10 saniye ekranı aydınlat
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showHighPriorityNotification(order: Order) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("orderId", order.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            order.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val itemsSummary = order.items.joinToString(", ") { "${it.quantity}x ${it.name}" }
        val customerInfo = if (order.customerName.isNotBlank()) "(${order.customerName})" else ""
        val titleText = "🔔 YENİ SİPARİŞ: ${order.tableLabel} $customerInfo"
        val bodyText = if (itemsSummary.isNotBlank()) {
            "$itemsSummary • ₺${"%.2f".format(order.totalPrice)}"
        } else {
            "₺${"%.2f".format(order.totalPrice)} tutarında yeni sipariş geldi."
        }

        val soundUri = SoundPlayer.getOrderSoundUri(this)

        val notification = NotificationCompat.Builder(this, CHANNEL_ORDER_ALERT_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$bodyText\n\nDetayları görmek için dokunun."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true) // Kilit ekranında tam ekran / heads-up banner açar
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 800))
            .build()

        notificationManager.notify(order.id.hashCode(), notification)
    }
}
