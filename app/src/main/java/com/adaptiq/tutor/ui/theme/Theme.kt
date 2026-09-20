package com.adaptiq.tutor.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AdaptIQLightColorScheme = lightColorScheme(
    primary = Color(0xFF4C4DDC),      // Deep Blue/Purple
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E2FF),
    onPrimaryContainer = Color(0xFF00006E),
    
    secondary = Color(0xFF20D489),    // Vibrant Green
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F8E8),
    onSecondaryContainer = Color(0xFF002111),
    
    tertiary = Color(0xFFFF9800),     // Orange accent
    onTertiary = Color.White,
    
    background = Color(0xFFF7F8FA),   // Off-white/gray background
    onBackground = Color(0xFF1B1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFF0F1F5),
    onSurfaceVariant = Color(0xFF45464F),
    
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    
    outline = Color(0xFFC4C6D0)
)

@Composable
fun AdaptIQTheme(
    darkTheme: Boolean = false, // Force light theme for now to match designs
    content: @Composable () -> Unit
) {
    val colorScheme = AdaptIQLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
