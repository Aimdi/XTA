package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The glyphs XTA's Bluesky screens use that the app's sets lack, from Material's (Apache 2.0) paths. */
internal object BlueskyIcons {
    val Sort: ImageVector by lazy { icon("Sort", "M3,18h6v-2H3v2zM3,6v2h18V6H3zM3,13h12v-2H3v2z") }

    val ListAlt: ImageVector by lazy {
        icon("ListAlt", "M19,5v14H5V5h14m1.1,-2H3.9c-0.5,0 -0.9,0.4 -0.9,0.9v16.2c0,0.4 0.4,0.9 0.9,0.9h16.2c0.4,0 0.9,-0.5 0.9,-0.9V3.9c0,-0.5 -0.5,-0.9 -0.9,-0.9zM11,7h6v2h-6V7zM11,11h6v2h-6v-2zM11,15h6v2h-6zM7,7h2v2H7zM7,11h2v2H7zM7,15h2v2H7z")
    }

    val CloudOutlined: ImageVector by lazy {
        icon("CloudOutlined", "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96zM19,18H6c-2.21,0 -4,-1.79 -4,-4 0,-2.05 1.53,-3.76 3.56,-3.97l1.07,-0.11 0.5,-0.95C8.08,7.14 9.94,6 12,6c2.62,0 4.88,1.86 5.39,4.43l0.3,1.5 1.53,0.11c1.56,0.1 2.78,1.41 2.78,2.96 0,1.65 -1.35,3 -3,3z")
    }

    val ExploreOutlined: ImageVector by lazy {
        icon("ExploreOutlined", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM6.5,17.5l7.51,-3.49L17.5,6.5 9.99,9.99 6.5,17.5zM12,10.9c0.61,0 1.1,0.49 1.1,1.1s-0.49,1.1 -1.1,1.1 -1.1,-0.49 -1.1,-1.1 0.49,-1.1 1.1,-1.1z")
    }

    val Visibility: ImageVector by lazy {
        icon("Visibility", "M12,6c3.79,0 7.17,2.13 8.82,5.5C19.17,14.87 15.79,17 12,17s-7.17,-2.13 -8.82,-5.5C4.83,8.13 8.21,6 12,6m0,-2C7,4 2.73,7.11 1,11.5 2.73,15.89 7,19 12,19s9.27,-3.11 11,-7.5C21.27,7.11 17,4 12,4zM12,9c1.38,0 2.5,1.12 2.5,2.5S13.38,14 12,14s-2.5,-1.12 -2.5,-2.5S10.62,9 12,9m0,-2c-2.48,0 -4.5,2.02 -4.5,4.5S9.52,16 12,16s4.5,-2.02 4.5,-4.5S14.48,7 12,7z")
    }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()
}
