package com.aimdi.xtanative.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The icons XTA's home and menu use that the core icon set lacks, from Material's (Apache 2.0) paths. */
internal object HomeIcons {
    val People: ImageVector by lazy {
        icon(
            "People",
            "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3zM8,11c1.66,0 2.99,-1.34 2.99,-3S9.66,5 8,5" +
                "C6.34,5 5,6.34 5,8s1.34,3 3,3zM8,13c-2.33,0 -7,1.17 -7,3.5L1,19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5zM16,13c-0.29,0 -0.62,0.02 -0.97,0.05" +
                "c1.16,0.84 1.97,1.97 1.97,3.45L17,19h6v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z",
        )
    }
    val TrendingUp: ImageVector by lazy { icon("TrendingUp", "M16,6l2.29,2.29 -4.88,4.88 -4,-4L2,16.59 3.41,18l6,-6 4,4 6.3,-6.29L22,12L22,6z") }
    val Bookmark: ImageVector by lazy {
        icon("Bookmark", "M17,3L7,3c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3L19,5c0,-1.1 -0.9,-2 -2,-2zM17,18l-5,-2.18L7,18L7,5h10v13z")
    }
    val Download: ImageVector by lazy { icon("Download", "M19,9h-4L15,3L9,3v6L5,9l7,7 7,-7zM5,18v2h14v-2L5,18z") }
    val Sensors: ImageVector by lazy {
        icon(
            "Sensors",
            "M7.76,16.24C6.67,15.16 6,13.66 6,12s0.67,-3.16 1.76,-4.24l1.42,1.42C8.45,9.9 8,10.9 8,12c0,1.1 0.45,2.1 1.17,2.83L7.76,16.24z" +
                "M16.24,16.24C17.33,15.16 18,13.66 18,12s-0.67,-3.16 -1.76,-4.24l-1.42,1.42C15.55,9.9 16,10.9 16,12c0,1.1 -0.45,2.1 -1.17,2.83L16.24,16.24z" +
                "M12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2s2,-0.9 2,-2S13.1,10 12,10z" +
                "M20,12c0,2.21 -0.9,4.21 -2.35,5.65l1.42,1.42C20.88,17.26 22,14.76 22,12s-1.12,-5.26 -2.93,-7.07l-1.42,1.42C19.1,7.79 20,9.79 20,12z" +
                "M6.35,6.35L4.93,4.93C3.12,6.74 2,9.24 2,12s1.12,5.26 2.93,7.07l1.42,-1.42C4.9,16.21 4,14.21 4,12S4.9,7.79 6.35,6.35z",
        )
    }
    val Extension: ImageVector by lazy {
        icon(
            "Extension",
            "M20.5,11L19,11L19,7c0,-1.1 -0.9,-2 -2,-2h-4L13,3.5C13,2.12 11.88,1 10.5,1S8,2.12 8,3.5L8,5L4,5c-1.1,0 -1.99,0.9 -1.99,2v3.8L3.5,10.8" +
                "c1.49,0 2.7,1.21 2.7,2.7s-1.21,2.7 -2.7,2.7L2,16.2L2,20c0,1.1 0.9,2 2,2h3.8v-1.5c0,-1.49 1.21,-2.7 2.7,-2.7 1.49,0 2.7,1.21 2.7,2.7L13.2,22" +
                "L17,22c1.1,0 2,-0.9 2,-2v-4h1.5c1.38,0 2.5,-1.12 2.5,-2.5S21.88,11 20.5,11z",
        )
    }
    val ViewColumn: ImageVector by lazy { icon("ViewColumn", "M10,18h5L15,5h-5v13zM4,18h5L9,5L4,5v13zM16,5v13h5L21,5h-5z") }
    val RemoveCircle: ImageVector by lazy {
        icon(
            "RemoveCircleOutline",
            "M7,11v2h10v-2L7,11zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8" +
                " 8,3.59 8,8 -3.59,8 -8,8z",
        )
    }
    val OpenInNew: ImageVector by lazy {
        icon("OpenInNew", "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z")
    }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()
}
