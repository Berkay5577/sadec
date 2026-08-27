package com.example.sadec.util

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.sadec.R

object SoundPlayer {

    private var mediaPlayer: MediaPlayer? = null

    fun getOrderSoundUri(context: Context): Uri {
        return Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.ring_of_silence}")
    }

    fun playOrderAlert(context: Context) {
        try {
            // 1. Play Custom Ring of Silence Sound
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null

                mediaPlayer = MediaPlayer.create(context.applicationContext, R.raw.ring_of_silence)?.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                    isLooping = false
                    setOnCompletionListener {
                        it.release()
                        mediaPlayer = null
                    }
                    start()
                }
            } catch (e: Exception) {
                // Fallback to default ringtone if mediaPlayer fails
                val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context.applicationContext, notificationUri)
                ringtone?.play()
            }

            // 2. Vibrate
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 800), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 800), -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 500, 200, 500, 200, 800), -1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
