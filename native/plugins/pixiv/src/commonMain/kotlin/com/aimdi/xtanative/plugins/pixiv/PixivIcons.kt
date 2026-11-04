package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The icons XTA's Pixiv screens show that neither the core icon set nor the app's own icons have, from Material's (Apache 2.0) paths. */
internal object PixivIcons {
    val Brush: ImageVector by lazy {
        icon(
            "Brush",
            "M7,14c-1.66,0 -3,1.34 -3,3 0,1.31 -1.16,2 -2,2 0.92,1.22 2.49,2 4,2 2.21,0 4,-1.79 4,-4 0,-1.66 -1.34,-3 -3,-3z" +
                "M20.71,4.63l-1.34,-1.34c-0.39,-0.39 -1.02,-0.39 -1.41,0L9,12.25 11.75,15l8.96,-8.96c0.39,-0.39 0.39,-1.02 0,-1.41z",
        )
    }
    val MenuBook: ImageVector by lazy {
        icon(
            "MenuBook",
            "M21,5c-1.11,-0.35 -2.33,-0.5 -3.5,-0.5 -1.95,0 -4.05,0.4 -5.5,1.5 -1.45,-1.1 -3.55,-1.5 -5.5,-1.5S2.45,4.9 1,6v14.65" +
                "c0,0.25 0.25,0.5 0.5,0.5 0.1,0 0.15,-0.05 0.25,-0.05C3.1,20.45 5.05,20 6.5,20c1.95,0 4.05,0.4 5.5,1.5 1.35,-0.85 3.8,-1.5 5.5,-1.5" +
                " 1.65,0 3.35,0.3 4.75,1.05 0.1,0.05 0.15,0.05 0.25,0.05 0.25,0 0.5,-0.25 0.5,-0.5V6c-0.6,-0.45 -1.25,-0.75 -2,-1z" +
                "M21,18.5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5 -1.7,0 -4.15,0.65 -5.5,1.5V8c1.35,-0.85 3.8,-1.5 5.5,-1.5 1.2,0 2.4,0.15 3.5,0.5v11.5z",
        )
    }
    val Label: ImageVector by lazy {
        icon(
            "Label",
            "M17.63,5.84C17.27,5.33 16.67,5 16,5L5,5.01C3.9,5.01 3,5.9 3,7v10c0,1.1 0.9,1.99 2,1.99L16,19c0.67,0 1.27,-0.33 1.63,-0.84" +
                "L22,12l-4.37,-6.16zM16,17H5V7h11l3.55,5L16,17z",
        )
    }
    val VolumeOff: ImageVector by lazy {
        icon(
            "VolumeOff",
            "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63zM19,12c0,0.94 -0.2,1.82 -0.54,2.64" +
                "l1.51,1.51C20.63,14.91 21,13.5 21,12c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 5,3.54 5,6.71zM4.27,3L3,4.27 7.73,9H3v6h4l5,5" +
                "v-6.73l4.25,4.25c-0.67,0.52 -1.42,0.93 -2.25,1.18v2.06c1.38,-0.31 2.63,-0.95 3.69,-1.81L19.73,21 21,19.73l-9,-9L4.27,3zM12,4L9.91,6.09 12,8.18V4z",
        )
    }
    val Collections: ImageVector by lazy {
        icon(
            "Collections",
            "M22,16V4c0,-1.1 -0.9,-2 -2,-2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2zM11,12l2.03,2.71L16,11l4,5H8l3,-4z" +
                "M2,6v14c0,1.1 0.9,2 2,2h14v-2H4V6H2z",
        )
    }
    val PlayCircle: ImageVector by lazy {
        icon("PlayCircle", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,16.5v-9l6,4.5 -6,4.5z")
    }
    val Tag: ImageVector by lazy {
        icon("Tag", "M20,10V8h-4V4h-2v4h-4V4H8v4H4v2h4v4H4v2h4v4h2v-4h4v4h2v-4h4v-2h-4v-4h4zM14,14h-4v-4h4v4z")
    }
    val ContentCopy: ImageVector by lazy {
        icon(
            "ContentCopy",
            "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2z" +
                "M19,21H8V7h11v14z",
        )
    }
    val ContentPaste: ImageVector by lazy {
        icon(
            "ContentPaste",
            "M19,2h-4.18C14.4,0.84 13.3,0 12,0c-1.3,0 -2.4,0.84 -2.82,2H5c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V4" +
                "c0,-1.1 -0.9,-2 -2,-2zM12,2c0.55,0 1,0.45 1,1s-0.45,1 -1,1 -1,-0.45 -1,-1 0.45,-1 1,-1zM19,20H5V4h2v3h10V4h2v16z",
        )
    }
    val ArrowDownward: ImageVector by lazy { icon("ArrowDownward", "M20,12l-1.41,-1.41L13,16.17V4h-2v12.17l-5.58,-5.59L4,12l8,8 8,-8z") }
    val Hd: ImageVector by lazy {
        icon(
            "Hd",
            "M19,3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM11,15H9.5v-2h-2v2H6V9h1.5v2.5h2V9" +
                "H11v6zM13,9h4c0.55,0 1,0.45 1,1v4c0,0.55 -0.45,1 -1,1h-4V9zM14.5,13.5h2v-3h-2v3z",
        )
    }
    val AspectRatio: ImageVector by lazy {
        icon(
            "AspectRatio",
            "M19,12h-2v3h-3v2h5v-5zM7,9h3V7H5v5h2V9zM21,3H3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z" +
                "M21,19.01H3V4.99h18v14.02z",
        )
    }
    val AllInclusive: ImageVector by lazy {
        icon(
            "AllInclusive",
            "M18.6,6.62c-1.44,0 -2.8,0.56 -3.77,1.53L12,10.66 10.48,12h0.01L7.8,14.39c-0.64,0.64 -1.49,0.99 -2.4,0.99 -1.87,0 -3.39,-1.51 -3.39,-3.38" +
                "S3.53,8.62 5.4,8.62c0.91,0 1.76,0.35 2.44,1.03l1.13,1 1.51,-1.34L9.22,8.2C8.2,7.18 6.84,6.62 5.4,6.62 2.42,6.62 0,9.04 0,12" +
                "s2.42,5.38 5.4,5.38c1.44,0 2.8,-0.56 3.77,-1.53l2.83,-2.5 0.01,0.01L13.52,12h-0.01l2.69,-2.39c0.64,-0.64 1.49,-0.99 2.4,-0.99" +
                " 1.87,0 3.39,1.51 3.39,3.38s-1.52,3.38 -3.39,3.38c-0.9,0 -1.76,-0.35 -2.44,-1.03l-1.14,-1.01 -1.51,1.34 1.27,1.12" +
                "c1.02,1.01 2.37,1.57 3.82,1.57 2.98,0 5.4,-2.41 5.4,-5.38s-2.42,-5.37 -5.4,-5.37z",
        )
    }
    val Pause: ImageVector by lazy { icon("Pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z") }
    val Smartphone: ImageVector by lazy {
        icon("Smartphone", "M17,1.01L7,1c-1.1,0 -2,0.9 -2,2v18c0,1.1 0.9,2 2,2h10c1.1,0 2,-0.9 2,-2V3c0,-1.1 -0.9,-1.99 -2,-1.99zM17,19H7V5h10v14z")
    }

    val Whatshot: ImageVector by lazy {
        icon(
            "Whatshot",
            "M13.5,0.67s0.74,2.65 0.74,4.8c0,2.06 -1.35,3.73 -3.41,3.73 -2.07,0 -3.63,-1.67 -3.63,-3.73l0.03,-0.36C5.21,7.51 4,10.62 4,14" +
                "c0,4.42 3.58,8 8,8s8,-3.58 8,-8C20,8.61 17.41,3.8 13.5,0.67zM11.71,19c-1.78,0 -3.22,-1.4 -3.22,-3.14 0,-1.62 1.05,-2.76 2.81,-3.12" +
                " 1.77,-0.36 3.6,-1.21 4.62,-2.58 0.39,1.29 0.59,2.65 0.59,4.04 0,2.65 -2.15,4.8 -4.8,4.8z",
        )
    }
    val Notes: ImageVector by lazy { icon("Notes", "M3,18h12v-2L3,16v2zM3,6v2h18L21,6L3,6zM3,13h18v-2L3,11v2z") }

    val TextFields: ImageVector by lazy { icon("TextFields", "M2.5,4v3h5v12h3L10.5,7h5L15.5,4h-13zM21.5,9h-9v3h3v7h3v-7h3L21.5,9z") }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()
}
