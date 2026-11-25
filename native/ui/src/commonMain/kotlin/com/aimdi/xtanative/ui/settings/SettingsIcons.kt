package com.aimdi.xtanative.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Outlined icons for Settings' rows that the core icon set lacks or draws
 * filled (its "outlined" star, heart and plus are solid), from Material's
 * outlined set (Apache 2.0), so every row's icon is drawn the same way.
 */
internal object SettingsIcons {
    val PaletteOutlined: ImageVector by lazy {
        icon(
            "PaletteOutlined",
            "M12 22C6.49 22 2 17.51 2 12S6.49 2 12 2s10 4.04 10 9c0 3.31-2.69 6-6 6h-1.77c-.28 0-.5.22-.5.5 0 .12.05.23.13.33.41.47.64 1.06.64 1.67" +
                "A2.5 2.5 0 0 1 12 22zm0-18c-4.41 0-8 3.59-8 8s3.59 8 8 8c.28 0 .5-.22.5-.5a.54.54 0 0 0-.14-.35c-.41-.46-.63-1.05-.63-1.65" +
                "a2.5 2.5 0 0 1 2.5-2.5H16c2.21 0 4-1.79 4-4 0-3.86-3.59-7-8-7z",
            "M5 11.5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0z",
            "M8 7.5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0z",
            "M13 7.5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0z",
            "M16 11.5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0z",
        )
    }
    val Accessibility: ImageVector by lazy {
        icon("Accessibility", "M12 2c1.1 0 2 .9 2 2s-.9 2-2 2-2-.9-2-2 .9-2 2-2zm9 7h-6v13h-2v-6h-2v6H9V9H3V7h18v2z")
    }
    val ExtensionOutlined: ImageVector by lazy {
        icon(
            "ExtensionOutlined",
            "M10.5 4.5c.28 0 .5.22.5.5v2h6v6h2c.28 0 .5.22.5.5s-.22.5-.5.5h-2v6h-2.12c-.68-1.75-2.39-3-4.38-3s-3.7 1.25-4.38 3H4v-2.12" +
                "c1.75-.68 3-2.39 3-4.38 0-1.99-1.24-3.7-2.99-4.38L4 7h6V5c0-.28.22-.5.5-.5m0-2C9.12 2.5 8 3.62 8 5H4c-1.1 0-1.99.9-1.99 2v3.8h.29" +
                "c1.49 0 2.7 1.21 2.7 2.7s-1.21 2.7-2.7 2.7H2V20c0 1.1.9 2 2 2h3.8v-.3c0-1.49 1.21-2.7 2.7-2.7s2.7 1.21 2.7 2.7v.3H17" +
                "c1.1 0 2-.9 2-2v-4c1.38 0 2.5-1.12 2.5-2.5S20.38 11 19 11V7c0-1.1-.9-2-2-2h-4c0-1.38-1.12-2.5-2.5-2.5z",
        )
    }
    val FolderOutlined: ImageVector by lazy {
        icon("FolderOutlined", "M9.17 6l2 2H20v10H4V6h5.17M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z")
    }
    val Sync: ImageVector by lazy {
        icon(
            "Sync",
            "M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6" +
                " 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z",
        )
    }

    private fun icon(name: String, vararg paths: String): ImageVector =
        paths.fold(ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)) { builder, path ->
            builder.addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        }.build()
}
