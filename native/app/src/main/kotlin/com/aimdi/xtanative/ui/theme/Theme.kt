package com.aimdi.xtanative.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val XBlue = Color(0xFF1D9BF0)

/** XTA's Lights out: true black, X's greys. */
private val Black = darkColorScheme(
    primary = XBlue,
    onPrimary = Color.White,
    secondary = XBlue,
    onSecondary = Color.White,
    background = Color.Black,
    onBackground = Color(0xFFE7E9EA),
    surface = Color.Black,
    onSurface = Color(0xFFE7E9EA),
    surfaceVariant = Color(0xFF16181C),
    onSurfaceVariant = Color(0xFF71767B),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF16181C),
    surfaceContainerHigh = Color(0xFF202327),
    surfaceContainerHighest = Color(0xFF2A2D31),
    outline = Color(0xFF536471),
    outlineVariant = Color(0xFF2F3336),
    error = Color(0xFFF4212E),
)

private val Light = lightColorScheme(
    primary = XBlue,
    onPrimary = Color.White,
    secondary = XBlue,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF0F1419),
    surface = Color.White,
    onSurface = Color(0xFF0F1419),
    surfaceVariant = Color(0xFFF7F9F9),
    onSurfaceVariant = Color(0xFF536471),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF7F9F9),
    surfaceContainerHigh = Color(0xFFEFF3F4),
    surfaceContainerHighest = Color(0xFFE1E8ED),
    outline = Color(0xFF8B98A5),
    outlineVariant = Color(0xFFEFF3F4),
    error = Color(0xFFF4212E),
)

private val Type = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp),
)

@Composable
fun XtaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Black else Light, typography = Type, content = content)
}
