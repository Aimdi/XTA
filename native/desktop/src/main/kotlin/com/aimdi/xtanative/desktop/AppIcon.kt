package com.aimdi.xtanative.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

/**
 * The launcher icon (app/src/main/res/drawable/ic_launcher_foreground.xml):
 * an X of two strokes on black, cut to the part a launcher shows. The menu's
 * icon is the same drawing (packaging/linux/com.aimdi.xtanative.svg).
 */
private val icon: ImageVector by lazy {
    ImageVector.Builder(name = "XtaNative", defaultWidth = 72.dp, defaultHeight = 72.dp, viewportWidth = 72f, viewportHeight = 72f)
        .addPath(pathData = nodes("M16,0 H56 A16,16 0 0 1 72,16 V56 A16,16 0 0 1 56,72 H16 A16,16 0 0 1 0,56 V16 A16,16 0 0 1 16,0 Z"), fill = SolidColor(Color.Black))
        .addPath(pathData = nodes("M20,18 L28,18 L52,54 L44,54 Z"), fill = SolidColor(Color.White))
        .addPath(pathData = nodes("M44,18 L52,18 L28,54 L20,54 Z"), fill = SolidColor(Color(0xFF1D9BF0)))
        .build()
}

private fun nodes(path: String) = PathParser().parsePathString(path).toNodes()

/** The window's icon, for the task bar and the window switcher. */
@Composable
internal fun appIcon(): Painter = rememberVectorPainter(icon)
