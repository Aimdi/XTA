package com.aimdi.xtanative.plugins.threads

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The glyphs XTA's Threads screens draw that neither the core icons nor the app's have, from Material's (Apache 2.0) paths. */
internal object ThreadsIcons {
    /** XTA's mark for Threads: the @ of a handle. */
    val Mark: ImageVector by lazy {
        icon(
            "AlternateEmail",
            "M12,1.95c-5.52,0 -10,4.48 -10,10s4.48,10 10,10h5v-2h-5c-4.34,0 -8,-3.66 -8,-8s3.66,-8 8,-8 8,3.66 8,8v1.43" +
                "c0,0.79 -0.71,1.57 -1.5,1.57s-1.5,-0.78 -1.5,-1.57v-1.43c0,-2.76 -2.24,-5 -5,-5s-5,2.24 -5,5 2.24,5 5,5" +
                "c1.38,0 2.64,-0.56 3.54,-1.47 0.65,0.89 1.77,1.47 2.96,1.47 1.97,0 3.5,-1.6 3.5,-3.57v-1.43c0,-5.52 -4.48,-10 -10,-10z" +
                "M12,14.95c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3z",
        )
    }
    val PersonAdd: ImageVector by lazy {
        icon(
            "PersonAdd",
            "M15,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM6,10L6,7L4,7v3L1,10v2h3v3h2v-3h3v-2L6,10z" +
                "M15,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z",
        )
    }
    val PersonRemove: ImageVector by lazy {
        icon("PersonRemove", "M14,8c0,-2.21 -1.79,-4 -4,-4S6,5.79 6,8s1.79,4 4,4S14,10.21 14,8zM17,10v2h6v-2H17zM2,18v2h16v-2c0,-2.66 -5.33,-4 -8,-4S2,15.34 2,18z")
    }
    val Public: ImageVector by lazy {
        icon(
            "Public",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM11,19.93c-3.95,-0.49 -7,-3.85 -7,-7.93 0,-0.62 0.08,-1.21 0.21,-1.79" +
                "L9,15v1c0,1.1 0.9,2 2,2v1.93zM17.9,17.39c-0.26,-0.81 -1,-1.39 -1.9,-1.39h-1v-3c0,-0.55 -0.45,-1 -1,-1L8,12v-2h2c0.55,0 1,-0.45 1,-1" +
                "L11,7h2c1.1,0 2,-0.9 2,-2v-0.41c2.93,1.19 5,4.06 5,7.41 0,2.08 -0.8,3.97 -2.1,5.39z",
        )
    }
    val PauseCircle: ImageVector by lazy {
        icon(
            "PauseCircle",
            "M9,16h2L11,8L9,8v8zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8" +
                " -3.59,8 -8,8zM13,16h2L15,8h-2v8z",
        )
    }

    /** Every glyph, for the test that draws them all. */
    internal val all: List<ImageVector> get() = listOf(Mark, PersonAdd, PersonRemove, Public, PauseCircle)

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()
}
