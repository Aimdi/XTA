package com.aimdi.xtanative.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun ProvidePlatformLook(content: @Composable () -> Unit) = content()

@Composable
internal actual fun systemPrefersDark(): Boolean = isSystemInDarkTheme()

/**
 * Whether the phone's own "Remove animations" is on (Settings ›
 * Accessibility), which sets the animator scale to nothing; read each time
 * the theme is drawn anew.
 */
@Composable
internal actual fun systemReducesMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
}

/**
 * The accent Android draws its own screens with since Android 12 (Material
 * You, taken from the wallpaper), as opaque ARGB; none before that. Read
 * again when the configuration changes, as a new wallpaper changes it.
 */
@Composable
internal actual fun systemAccent(): Long? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) {
        runCatching { context.getColor(android.R.color.system_accent1_400).toLong() and 0xFFFFFFFFL or 0xFF000000L }.getOrNull()
    }
}
