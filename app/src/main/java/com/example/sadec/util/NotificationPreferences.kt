package com.example.sadec.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Bu cihaza özel bildirim, ses ve kilit ekranı tercihlerini yöneten yardımcı sınıf.
 * Birden fazla garson / dükkan sahibi telefonu olduğunda, her telefonun bağımsız
 * olarak bildirim alıp almamasını kontrol eder.
 */
object NotificationPreferences {
    private const val PREFS_NAME = "sadec_device_notification_prefs"
    private const val KEY_DEVICE_NOTIFICATIONS_ENABLED = "key_device_notifications_enabled"
    private const val KEY_SOUND_ENABLED = "key_sound_enabled"
    private const val KEY_WAKE_SCREEN_ENABLED = "key_wake_screen_enabled"
    private const val KEY_PERMISSION_ASKED = "key_permission_asked"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Bu cihazda yeni sipariş bildirimlerinin aktif olup olmadığını döndürür.
     * Varsayılan olarak açık (true) gelir.
     */
    fun isDeviceNotificationsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DEVICE_NOTIFICATIONS_ENABLED, true)
    }

    fun setDeviceNotificationsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DEVICE_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    /**
     * Sipariş geldiğinde alarm sesinin çalıp çalmayacağını döndürür.
     */
    fun isSoundEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    /**
     * Ekran kapalıyken kilit ekranının aydınlatılıp aydınlatılmayacağını döndürür.
     */
    fun isWakeScreenEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WAKE_SCREEN_ENABLED, true)
    }

    fun setWakeScreenEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WAKE_SCREEN_ENABLED, enabled).apply()
    }

    /**
     * Bildirim izninin daha önce kullanıcıya sorulup sorulmadığını kontrol eder.
     * Uygulama her açıldığında sürekli izin istemeyi önlemek için kullanılır.
     */
    fun isPermissionAsked(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PERMISSION_ASKED, false)
    }

    fun setPermissionAsked(context: Context, asked: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PERMISSION_ASKED, asked).apply()
    }

    /**
     * Bu cihazın Garson / Çalışan modunda olup olmadığını döndürür.
     * Garson modunda ciro ve kasa dashboard'u gizlenir.
     * Varsayılan olarak Yönetici Modu (false) gelir.
     */
    fun isStaffMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_STAFF_MODE, false)
    }

    fun setStaffMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_STAFF_MODE, enabled).apply()
    }

    private const val KEY_STAFF_MODE = "key_staff_mode"
}
