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

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()
}
