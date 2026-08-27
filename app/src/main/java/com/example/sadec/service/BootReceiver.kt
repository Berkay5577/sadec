package com.example.sadec.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

/**
 * Telefon açıldığında (cihaz yeniden başlatıldığında) veya uygulama güncellendiğinde
 * kullanıcı giriş yapmışsa Sipariş Dinleme Servisini otomatik olarak arka planda başlatır.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                OrderBackgroundService.startService(context)
            }
        }
    }
}
