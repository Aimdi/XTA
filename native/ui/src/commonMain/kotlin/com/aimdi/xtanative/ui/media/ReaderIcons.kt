package com.aimdi.xtanative.ui.media

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The icons the links, reading aloud and the media viewer draw that the core
 * icon set lacks: Material's (Apache 2.0) paths, and X's mark from Simple
 * Icons (CC0).
 */
internal object ReaderIcons {
    val MoreHoriz: ImageVector by lazy {
        icon(
            "MoreHoriz",
            "M6,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM18,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z" +
                "M12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
        )
    }
    val Lock: ImageVector by lazy {
        icon(
            "Lock",
            "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2z" +
                "M9,6c0,-1.66 1.34,-3 3,-3s3,1.34 3,3v2H9V6zM18,20H6V10h12v10zM12,17c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2z",
        )
    }
    val LockOpen: ImageVector by lazy {
        icon(
            "LockOpen",
            "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6h2c0,-1.66 1.34,-3 3,-3s3,1.34 3,3v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12" +
                "c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM18,20H6V10h12v10zM12,17c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2z",
        )
    }
    val OpenInNew: ImageVector by lazy {
        icon("OpenInNew", "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z")
    }
    val Pause: ImageVector by lazy { icon("Pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z") }
    val Replay: ImageVector by lazy {
        icon("Replay", "M12,5V1L7,6l5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6H4c0,4.42 3.58,8 8,8s8,-3.58 8,-8 -3.58,-8 -8,-8z")
    }
    val VolumeUp: ImageVector by lazy {
        icon(
            "VolumeUp",
            "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02z" +
                "M14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z",
        )
    }
    val VolumeOff: ImageVector by lazy {
        icon(
            "VolumeOff",
            "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63zM19,12c0,0.94 -0.2,1.82 -0.54,2.64l1.51,1.51" +
                "C20.63,14.91 21,13.5 21,12c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 5,3.54 5,6.71zM4.27,3L3,4.27 7.73,9H3v6h4l5,5v-6.73" +
                "l4.25,4.25c-0.67,0.52 -1.42,0.93 -2.25,1.18v2.06c1.38,-0.31 2.63,-0.95 3.69,-1.81L19.73,21 21,19.73l-9,-9L4.27,3zM12,4L9.91,6.09 12,8.18V4z",
        )
    }
    val Speed: ImageVector by lazy {
        icon(
            "Speed",
            "M20.38,8.57l-1.23,1.85a8,8 0,0 1,-0.22 7.58H5.07A8,8 0,0 1,15.58 6.85l1.85,-1.23A10,10 0,0 0,3.35 19a2,2 0,0 0,1.72 1h13.85" +
                "a2,2 0,0 0,1.74 -1a10,10 0,0 0,-0.27 -10.44zM10.59,15.41a2,2 0,0 0,2.83 0l5.66,-8.49 -8.49,5.66a2,2 0,0 0,0 2.83z",
        )
    }
    val HighQuality: ImageVector by lazy {
        icon(
            "HighQuality",
            "M19,4H5c-1.11,0 -2,0.9 -2,2v12c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM11,15H9.5v-2h-2v2H6V9h1.5v2.5h2V9H11v6z" +
                "M18,14c0,0.55 -0.45,1 -1,1h-0.75v1.5h-1.5V15H14c-0.55,0 -1,-0.45 -1,-1v-4c0,-0.55 0.45,-1 1,-1h3c0.55,0 1,0.45 1,1v4zM14.5,13.5h2v-3h-2v3z",
        )
    }
    val AspectRatio: ImageVector by lazy {
        icon(
            "AspectRatio",
            "M19,12h-2v3h-3v2h5v-5zM7,9h3V7H5v5h2V9zM21,3H3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z" +
                "M21,19.01H3V4.99h18v14.02z",
        )
    }
    val FullscreenExit: ImageVector by lazy {
        icon("FullscreenExit", "M5,16h3v3h2v-5H5v2zM8,8H5v2h5V5H8v3zM14,19h2v-3h3v-2h-5v5zM16,8V5h-2v5h5V8h-3z")
    }
    val Download: ImageVector by lazy { icon("Download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z") }
    val FastRewind: ImageVector by lazy { icon("FastRewind", "M11,18V6l-8.5,6 8.5,6zM11.5,12l8.5,6V6l-8.5,6z") }
    val FastForward: ImageVector by lazy { icon("FastForward", "M4,18l8.5,-6L4,6v12zM13,6v12l8.5,-6L13,6z") }
    val RecordVoiceOver: ImageVector by lazy {
        icon(
            "RecordVoiceOver",
            "M9,13c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM9,15c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z" +
                "M16.76,5.36l-1.68,1.69c0.84,1.18 0.84,2.71 0,3.89l1.68,1.69c2.02,-2.02 2.02,-5.07 0,-7.27zM20.07,2l-1.63,1.63c2.77,3.02 2.77,7.56 0,10.74" +
                "L20.07,16c3.9,-3.89 3.91,-9.95 0,-14z",
        )
    }
    val RecordVoiceOverOutlined: ImageVector by lazy {
        icon(
            "RecordVoiceOverOutlined",
            "M9,13c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM9,7c1.1,0 2,0.9 2,2s-0.9,2 -2,2 -2,-0.9 -2,-2 0.9,-2 2,-2z" +
                "M9,15c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4zM3,19c0.22,-0.72 3.31,-2 6,-2 2.7,0 5.8,1.29 6,2H3z" +
                "M15.08,7.05c0.84,1.18 0.84,2.71 0,3.89l1.68,1.69c2.02,-2.02 2.02,-5.07 0,-7.27l-1.68,1.69zM20.07,2l-1.63,1.63c2.77,3.02 2.77,7.56 0,10.74" +
                "L20.07,16c3.9,-3.89 3.91,-9.95 0,-14z",
        )
    }
    val StopCircle: ImageVector by lazy {
        icon(
            "StopCircle",
            "M8,16h8V8H8v8zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z",
        )
    }
    val Tune: ImageVector by lazy {
        icon("Tune", "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z")
    }
    val ClosedCaption: ImageVector by lazy {
        icon(
            "ClosedCaption",
            "M19,4H5c-1.11,0 -2,0.9 -2,2v12c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM11,11H9.5v-0.5h-2v3h2V13H11v1" +
                "c0,0.55 -0.45,1 -1,1H7c-0.55,0 -1,-0.45 -1,-1v-4c0,-0.55 0.45,-1 1,-1h3c0.55,0 1,0.45 1,1v1zM18,11h-1.5v-0.5h-2v3h2V13H18v1" +
                "c0,0.55 -0.45,1 -1,1h-3c-0.55,0 -1,-0.45 -1,-1v-4c0,-0.55 0.45,-1 1,-1h3c0.55,0 1,0.45 1,1v1z",
        )
    }
    val ClosedCaptionOff: ImageVector by lazy {
        icon(
            "ClosedCaptionOff",
            "M19,4H5C3.89,4 3,4.9 3,6v12c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6C21,4.9 20.1,4 19,4zM19,18H5V6h14V18z" +
                "M7,15h3c0.55,0 1,-0.45 1,-1v-1H9.5v0.5h-2v-3h2V11H11v-1c0,-0.55 -0.45,-1 -1,-1H7c-0.55,0 -1,0.45 -1,1v4C6,14.55 6.45,15 7,15z" +
                "M14,15h3c0.55,0 1,-0.45 1,-1v-1h-1.5v0.5h-2v-3h2V11H18v-1c0,-0.55 -0.45,-1 -1,-1h-3c-0.55,0 -1,0.45 -1,1v4C13,14.55 13.45,15 14,15z",
        )
    }
    val BrokenImage: ImageVector by lazy {
        icon(
            "BrokenImage",
            "M21,5v6.59l-3,-3.01 -4,4.01 -4,-4 -4,4 -3,-3.01V5c0,-1.1 0.9,-2 2,-2h14c1.1,0 2,0.9 2,2zM18,11.42l3,3.01V19c0,1.1 -0.9,2 -2,2H5" +
                "c-1.1,0 -2,-0.9 -2,-2v-6.58l3,2.99 4,-4 4,4 4,-3.99z",
        )
    }
    val SendOutlined: ImageVector by lazy {
        icon("SendOutlined", "M4.01,6.03l7.51,3.22 -7.52,-1 0.01,-2.22m7.5,8.72L4,17.97v-2.22l7.51,-1M2.01,3L2,10l15,2 -15,2 0.01,7L23,12 2.01,3z")
    }
    val UnfoldMore: ImageVector by lazy {
        icon("UnfoldMore", "M12,5.83L15.17,9l1.41,-1.41L12,3 7.41,7.59 8.83,9 12,5.83zM12,18.17L8.83,15l-1.41,1.41L12,21l4.59,-4.59L15.17,15 12,18.17z")
    }

    /** X's mark, drawn in the text's colour as X draws it. */
    val XMark: ImageVector by lazy {
        icon("XMark", "M18.901,1.153h3.68l-8.04,9.19L24,22.846h-7.406l-5.8,-7.584 -6.638,7.584H0.474l8.6,-9.83L0,1.154h7.594l5.243,6.932z" + "M17.61,20.644h2.039L6.486,3.24H4.298z")
    }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()
}
