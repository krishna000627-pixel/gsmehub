package com.krishna.webhub.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.krishna.webhub.data.Prefs

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB4BEFF),
    onPrimary = Color(0xFF16206B),
    primaryContainer = Color(0xFF2E3A8C),
    onPrimaryContainer = Color(0xFFDDE0FF),
    secondary = Color(0xFFC3C5DD),
    tertiary = Color(0xFFE5BAD8),
    background = Color(0xFF0E1015),
    onBackground = Color(0xFFE3E2E9),
    surface = Color(0xFF0E1015),
    onSurface = Color(0xFFE3E2E9),
    onSurfaceVariant = Color(0xFFC6C5D0),
    surfaceContainerLow = Color(0xFF14161C),
    surfaceContainer = Color(0xFF191B22),
    surfaceContainerHigh = Color(0xFF23252D),
    surfaceContainerHighest = Color(0xFF2D2F38),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF4455BB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72),
    tertiary = Color(0xFF77536D),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE3E1E9),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0)
)

@Composable
fun WebHubTheme(content: @Composable () -> Unit) {
    val mode by Prefs.theme.collectAsState()
    val dynamic by Prefs.dynamicColor.collectAsState()
    val dark = when (mode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkScheme
        else -> LightScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
