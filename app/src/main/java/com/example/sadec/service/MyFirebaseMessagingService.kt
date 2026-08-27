package com.example.sadec.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.sadec.MainActivity
import com.example.sadec.R
import com.example.sadec.data.repository.FirestoreRepository
import com.example.sadec.util.SoundPlayer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val firestoreRepository = FirestoreRepository()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val restId = "sadec-gerze"
            CoroutineScope(Dispatchers.IO).launch {
                firestoreRepository.updateStaffFcmToken(user.uid, restId, user.email ?: "Dükkan Sahibi", token)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "🔔 Yeni Sipariş Geldi!"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "Bir masa yeni sipariş verdi."
        val orderId = remoteMessage.data["orderId"]

        // 1. Ekranı Uyandır (WakeLock)
        wakeUpDeviceScreen()

        // 2. Ses ve Titreşim Çal
        SoundPlayer.playOrderAlert(this)

        // 3. Yüksek Öncelikli Kilit Ekranı Bildirimi Göster
        showNotification(title, body, orderId)
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
                    "sadec:FCMOrderWakeLock"
                )
                wakeLock.acquire(10000)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showNotification(title: String, body: String, orderId: String?) {
        val channelId = "orders_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = SoundPlayer.getOrderSoundUri(this)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                channelId,
                "Sipariş Bildirimleri (Ring of Silence)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Yeni gelen masa siparişleri için anlık yüksek sesli uyarılar"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("orderId", orderId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = SoundPlayer.getOrderSoundUri(this)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n\nDetayları görmek için dokunun."))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 800))
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
