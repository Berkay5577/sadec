package com.example.sadec

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class SadecApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:5087463503:android:f3f8b02604627f0d55423a")
                    .setApiKey("AIzaSyDP8uQbnP6IrT127fpIyVgrmFIcBlPMN7w")
                    .setProjectId("sadec-9b458")
                    .setStorageBucket("sadec-9b458.firebasestorage.app")
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
