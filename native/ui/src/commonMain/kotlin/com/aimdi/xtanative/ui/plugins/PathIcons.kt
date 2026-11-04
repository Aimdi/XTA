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

/** Material's glyphs (Apache 2.0) that more than one plugin draws and neither Material's core set nor the app's sets have. */
object PluginIcons {
    /** A settings page's "Test connection", network_check. */
    val NetworkCheck: ImageVector by lazy {
        pathIcon(
            "NetworkCheck",
            "M15.9,5c-0.17,0 -0.32,0.09 -0.41,0.23l-0.07,0.15 -5.18,11.65c-0.16,0.29 -0.26,0.61 -0.26,0.96 0,1.11 0.9,2.01 2.01,2.01" +
                " 0.96,0 1.77,-0.68 1.96,-1.59l0.01,-0.03L16.4,5.5c0,-0.28 -0.22,-0.5 -0.5,-0.5zM1,9l2,2c2.88,-2.88 6.79,-4.08 10.53,-3.62" +
                "l1.19,-2.68C9.89,3.84 4.74,5.27 1,9zM21,11l2,-2c-1.64,-1.64 -3.55,-2.82 -5.59,-3.57l-0.53,2.82c1.5,0.62 2.9,1.53 4.12,2.75z" +
                "M17,15l2,-2c-0.8,-0.8 -1.7,-1.42 -2.66,-1.89l-0.55,2.92c0.42,0.27 0.83,0.59 1.21,0.97zM5,13l2,2c1.13,-1.13 2.56,-1.79 4.03,-2" +
                "l1.28,-2.88c-2.63,-0.08 -5.3,0.87 -7.31,2.88z",
        )
    }
}
