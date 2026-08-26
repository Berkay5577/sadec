package com.example.sadec.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Tek ve tutarlı Sade.C Zümrüt Yeşili & Krem/Altın Tasarımı (Telefon ne modda olursa olsun asla değişmez)
private val SadeCScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = WarmGold,
    primaryContainer = SoftMintGreen,
    onPrimaryContainer = ForestGreen,
    secondary = SageGreen,
    onSecondary = Color.White,
    secondaryContainer = SoftMintGreen,
    background = Color(0xFFFBF8F3), // Sade.C Doğal Krem
    surface = Color.White,
    onBackground = ForestGreen,
    onSurface = ForestGreen,
    surfaceVariant = Color(0xFFEBF3EE),
    onSurfaceVariant = ForestGreen
)

@Composable
fun SadecTheme(
    darkTheme: Boolean = false, // Karanlık mod devre dışı
    content: @Composable () -> Unit
) {
    val colorScheme = SadeCScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
