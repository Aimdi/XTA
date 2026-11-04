package com.aimdi.xtanative.ui.plugins

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * A glyph drawn from an SVG [path] in a [viewport]-wide square, as Material's
 * icons and the networks' marks are written: 24 dp, tinted where it is drawn.
 */
fun pathIcon(name: String, path: String, viewport: Float = 24f): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = viewport, viewportHeight = viewport)
        .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .build()
