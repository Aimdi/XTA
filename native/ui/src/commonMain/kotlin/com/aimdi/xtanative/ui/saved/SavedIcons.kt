package com.aimdi.xtanative.ui.saved

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The icons Saved, the offline library and downloads draw that the core icon set lacks, from Material's (Apache 2.0) paths. */
object SavedIcons {
    val Bookmark by lazy { icon("Bookmark", "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z") }
    val BookmarkBorder by lazy { icon("BookmarkBorder", "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2zM17,18l-5,-2.18L7,18V5h10v13z") }
    val Folder by lazy {
        icon("Folder", "M9.17,6l2,2H20v10H4V6h5.17M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z")
    }
    val FolderOpen by lazy {
        icon("FolderOpen", "M20,6h-8l-2,-2L4,4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2L22,8c0,-1.1 -0.9,-2 -2,-2zM20,18L4,18L4,8h16v10z")
    }
    val NoFolder by lazy {
        icon(
            "NoFolder",
            "M20,6h-8l-2,-2H7.17l2,2H20v10.83l1.76,1.76C21.91,18.42 22,18.22 22,18V8C22,6.9 21.1,6 20,6zM2.1,2.1L0.69,3.51L2.04,4.86" +
                "C2.02,4.91 2,4.95 2,5v13c0,1.1 0.9,2 2,2h13.17l3.32,3.32l1.41,-1.41L2.1,2.1zM4,18V6.83L15.17,18H4z",
        )
    }
    val NewFolder by lazy {
        icon(
            "NewFolder",
            "M20,6h-8l-2,-2L4,4c-1.11,0 -1.99,0.89 -1.99,2L2,18c0,1.11 0.89,2 2,2h16c1.11,0 2,-0.89 2,-2L22,8c0,-1.11 -0.89,-2 -2,-2z" +
                "M20,18L4,18L4,6h5.17l2,2L20,8v10zM12,14h2v2h2v-2h2v-2h-2v-2h-2v2h-2z",
        )
    }
    val MoveToFolder by lazy {
        icon(
            "MoveToFolder",
            "M20,6h-8l-2,-2H4C2.9,4 2.01,4.9 2.01,6L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8C22,6.9 21.1,6 20,6zM20,18H4V6h5.17l2,2H20V18z" +
                "M12.16,12H8v2h4.16l-1.59,1.59L11.99,17L16,13.01L11.99,9l-1.41,1.41L12.16,12z",
        )
    }
    val Download by lazy { icon("Download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z") }
    val DownloadDone by lazy { icon("DownloadDone", "M5,18h14v2H5v-2zM9.6,15.3L5,10.7l2,-1.9 2.6,2.6L17,4l2,2 -9.4,9.3z") }
    val ErrorOutline by lazy {
        icon(
            "ErrorOutline",
            "M11,15h2v2h-2zM11,7h2v6h-2zM11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2z" +
                "M12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z",
        )
    }
    val Cancelled by lazy {
        icon(
            "Cancelled",
            "M12,2C6.47,2 2,6.47 2,12s4.47,10 10,10 10,-4.47 10,-10S17.53,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z" +
                "M15.59,7L12,10.59 8.41,7 7,8.41 10.59,12 7,15.59 8.41,17 12,13.41 15.59,17 17,15.59 13.41,12 17,8.41z",
        )
    }
    val OfflinePin by lazy {
        icon("OfflinePin", "M12,2C6.5,2 2,6.5 2,12s4.5,10 10,10 10,-4.5 10,-10S17.5,2 12,2zM17,18L7,18v-2h10v2zM10.3,14L7,10.7l1.4,-1.4 1.9,1.9 5.3,-5.3L17,7.3 10.3,14z")
    }
    val OfflinePinOutline by lazy {
        icon(
            "OfflinePinOutline",
            "M12,2C6.5,2 2,6.5 2,12s4.5,10 10,10 10,-4.5 10,-10S17.5,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z" +
                "M7,15h10v2H7zM10.3,11.2L8.4,9.3 7,10.7l3.3,3.3L17,7.3 15.6,5.9z",
        )
    }
    val Tune by lazy {
        icon(
            "Tune",
            "M3,17v2h6v-2L3,17zM3,5v2h10L13,5L3,5zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2L3,11v2h4v2h2L9,9L7,9zM21,13v-2L11,11v2h10z" +
                "M15,9h2L17,7h4L21,5h-4L17,3h-2v6z",
        )
    }
    val Checklist by lazy {
        icon(
            "Checklist",
            "M22,7h-9v2h9V7zM22,15h-9v2h9V15zM5.54,11L2,7.46l1.41,-1.41l2.12,2.12l4.24,-4.24l1.41,1.41L5.54,11zM5.54,19L2,15.46" +
                "l1.41,-1.41l2.12,2.12l4.24,-4.24l1.41,1.41L5.54,19z",
        )
    }
    val SelectAll by lazy {
        icon(
            "SelectAll",
            "M3,5h2L5,3c-1.1,0 -2,0.9 -2,2zM3,13h2v-2L3,11v2zM7,21h2v-2L7,19v2zM3,9h2L5,7L3,7v2zM13,3h-2v2h2L13,3zM19,3v2h2c0,-1.1 -0.9,-2 -2,-2z" +
                "M5,21v-2L3,19c0,1.1 0.9,2 2,2zM3,17h2v-2L3,15v2zM9,3L7,3v2h2L9,3zM11,21h2v-2h-2v2zM19,13h2v-2h-2v2zM19,21c1.1,0 2,-0.9 2,-2h-2v2z" +
                "M19,9h2L21,7h-2v2zM19,17h2v-2h-2v2zM15,21h2v-2h-2v2zM15,5h2L17,3h-2v2zM7,17h10L17,7L7,7v10zM9,9h6v6L9,15L9,9z",
        )
    }
    val Deselect by lazy {
        icon(
            "Deselect",
            "M3,13h2v-2H3V13zM7,21h2v-2H7V21zM13,3h-2v2h2V3zM19,3v2h2C21,3.9 20.1,3 19,3zM5,21v-2H3C3,20.1 3.9,21 5,21zM3,17h2v-2H3V17z" +
                "M11,21h2v-2h-2V21zM19,13h2v-2h-2V13zM19,9h2V7h-2V9zM15,5h2V3h-2V5zM7.83,5L7,4.17V3h2v2H7.83zM19.83,17L19,16.17V15h2v2H19.83z" +
                "M21.19,21.19L2.81,2.81L1.39,4.22L4.17,7H3v2h2V7.83l2,2V17h7.17l2,2H15v2h2v-1.17l2.78,2.78L21.19,21.19zM9,15v-3.17L12.17,15H9z" +
                "M15,12.17V9h-3.17l-2,-2H17v7.17L15,12.17z",
        )
    }
    val EditNote by lazy {
        icon(
            "EditNote",
            "M3,10h11v2H3V10zM3,8h11V6H3V8zM3,16h7v-2H3V16zM18.01,12.87l0.71,-0.71c0.39,-0.39 1.02,-0.39 1.41,0l0.71,0.71" +
                "c0.39,0.39 0.39,1.02 0,1.41l-0.71,0.71L18.01,12.87zM17.3,13.58l-5.3,5.3V21h2.12l5.3,-5.3L17.3,13.58z",
        )
    }
    val NoteAdd by lazy {
        icon(
            "NoteAdd",
            "M13,11h-2v3H8v2h3v3h2v-3h3v-2h-3V11zM14,2H6C4.9,2 4,2.9 4,4v16c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8L14,2zM18,20H6V4h7v5h5V20z",
        )
    }
    val ManageSearch by lazy {
        icon(
            "ManageSearch",
            "M7,9H2V7h5V9zM7,12H2v2h5V12zM20.59,19l-3.83,-3.83C15.96,15.69 15.02,16 14,16c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5s5,2.24 5,5" +
                "c0,1.02 -0.31,1.96 -0.83,2.75L22,17.59L20.59,19zM17,11c0,-1.65 -1.35,-3 -3,-3s-3,1.35 -3,3s1.35,3 3,3S17,12.65 17,11zM2,19h10v-2H2V19z",
        )
    }
    val Cleaning by lazy {
        icon(
            "Cleaning",
            "M16,11h-1V3c0,-1.1 -0.9,-2 -2,-2h-2C9.9,1 9,1.9 9,3v8H8c-2.76,0 -5,2.24 -5,5v7h18v-7C21,13.24 18.76,11 16,11zM11,3h2v8h-2V3z" +
                "M19,21h-2v-3c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1v3h-2v-3c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1v3H9v-3c0,-0.55 -0.45,-1 -1,-1" +
                "s-1,0.45 -1,1v3H5v-5c0,-1.65 1.35,-3 3,-3h8c1.65,0 3,1.35 3,3V21z",
        )
    }
    val DeleteSweep by lazy {
        icon("DeleteSweep", "M15,16h4v2h-4zM15,8h7v2h-7zM15,12h6v2h-6zM3,18c0,1.1 0.9,2 2,2h6c1.1,0 2,-0.9 2,-2L13,8L3,8v10zM14,5h-3l-1,-1L6,4L5,5L2,5v2h12z")
    }
    val PlaylistRemove by lazy {
        icon(
            "PlaylistRemove",
            "M14,10H3v2h11V10zM14,6H3v2h11V6zM3,16h7v-2H3V16zM14.41,22L17,19.41L19.59,22L21,20.59L18.41,18L21,15.41L19.59,14L17,16.59" +
                "L14.41,14L13,15.41L15.59,18L13,20.59L14.41,22z",
        )
    }
    val OpenInNew by lazy {
        icon("OpenInNew", "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z")
    }
    val OpenInFull by lazy { icon("OpenInFull", "M21,11V3h-8l3.29,3.29l-10,10L3,13v8h8l-3.29,-3.29l10,-10L21,11z") }
    val Article by lazy {
        icon(
            "Article",
            "M19,5v14H5V5H19M19,3H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3L19,3zM14,17H7v-2h7V17z" +
                "M17,13H7v-2h10V13zM17,9H7V7h10V9z",
        )
    }
    val PermMedia by lazy {
        icon(
            "PermMedia",
            "M2,6H0v5h0.01L0,20c0,1.1 0.9,2 2,2h18v-2H2V6zM7,15h14l-3.5,-4.5 -2.5,3.01L12.5,10.5zM22,4h-8l-2,-2H6c-1.1,0 -1.99,0.9 -1.99,2L4,16" +
                "c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM22,16H6V4h5.17l1.41,1.41 0.59,0.59H22v10z",
        )
    }
    val Visibility by lazy {
        icon(
            "Visibility",
            "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5" +
                "s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
        )
    }
    val VisibilityOff by lazy {
        icon(
            "VisibilityOff",
            "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75 -1.73,-4.39 -6,-7.5 -11,-7.5" +
                " -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7zM2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12" +
                "c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3 4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27zM7.53,9.8l1.55,1.55" +
                "c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03 0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53" +
                " -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2zM11.84,9.02l3.15,3.15 0.02,-0.16c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z",
        )
    }
    val DragHandle by lazy { icon("DragHandle", "M20,9H4v2h16V9zM4,15h16v-2H4V15z") }
    val AttachMedia by lazy {
        icon(
            "AttachMedia",
            "M16.5,6v11.5c0,2.21 -1.79,4 -4,4s-4,-1.79 -4,-4V5c0,-1.38 1.12,-2.5 2.5,-2.5s2.5,1.12 2.5,2.5v10.5c0,0.55 -0.45,1 -1,1" +
                "s-1,-0.45 -1,-1V6H10v9.5c0,1.38 1.12,2.5 2.5,2.5s2.5,-1.12 2.5,-2.5V5c0,-2.21 -1.79,-4 -4,-4S7,2.79 7,5v12.5c0,3.04 2.46,5.5 5.5,5.5" +
                "s5.5,-2.46 5.5,-5.5V6h-1.5z",
        )
    }
    val Videocam by lazy { icon("Videocam", "M17,10.5V7c0,-0.55 -0.45,-1 -1,-1H4c-0.55,0 -1,0.45 -1,1v10c0,0.55 0.45,1 1,1h12c0.55,0 1,-0.45 1,-1v-3.5l4,4v-11l-4,4z") }
    val Image by lazy {
        icon("Image", "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z")
    }
    val Scan by lazy {
        icon(
            "Scan",
            "M7,3H4v3H2V1h5V3zM22,6V1h-5v2h3v3H22zM7,21H4v-3H2v5h5V21zM20,18v3h-3v2h5v-5H20zM17,6H7v12h10V6zM19,18c0,1.1 -0.9,2 -2,2H7" +
                "c-1.1,0 -2,-0.9 -2,-2V6c0,-1.1 0.9,-2 2,-2h10c1.1,0 2,0.9 2,2V18zM15,8H9v2h6V8zM15,11H9v2h6V11zM15,14H9v2h6V14z",
        )
    }
    val Public by lazy {
        icon(
            "Public",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM11,19.93c-3.95,-0.49 -7,-3.85 -7,-7.93 0,-0.62 0.08,-1.21 0.21,-1.79" +
                "L9,15v1c0,1.1 0.9,2 2,2v1.93zM17.9,17.39c-0.26,-0.81 -1,-1.39 -1.9,-1.39h-1v-3c0,-0.55 -0.45,-1 -1,-1L8,12v-2h2c0.55,0 1,-0.45 1,-1" +
                "L11,7h2c1.1,0 2,-0.9 2,-2v-0.41c2.93,1.19 5,4.06 5,7.41 0,2.08 -0.8,3.97 -2.1,5.39z",
        )
    }
    val Extension by lazy {
        icon(
            "Extension",
            "M20.5,11H19V7c0,-1.1 -0.9,-2 -2,-2h-4V3.5C13,2.12 11.88,1 10.5,1S8,2.12 8,3.5V5H4c-1.1,0 -1.99,0.9 -1.99,2v3.8H3.5" +
                "c1.49,0 2.7,1.21 2.7,2.7s-1.21,2.7 -2.7,2.7H2V20c0,1.1 0.9,2 2,2h3.8v-1.5c0,-1.49 1.21,-2.7 2.7,-2.7 1.49,0 2.7,1.21 2.7,2.7V22H17" +
                "c1.1,0 2,-0.9 2,-2v-4h1.5c1.38,0 2.5,-1.12 2.5,-2.5S21.88,11 20.5,11z",
        )
    }
    val MoreHoriz by lazy {
        icon(
            "MoreHoriz",
            "M6,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM18,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z" +
                "M12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
        )
    }

    /** X's mark, for the network picker. */
    val XMark by lazy {
        icon(
            "XMark",
            "M18.244,2.25h3.308l-7.227,8.26 8.502,11.24H16.17l-5.214,-6.817L4.99,21.75H1.68l7.73,-8.835L1.254,2.25H8.08l4.713,6.231z" +
                "M17.083,19.77h1.833L7.084,4.126H5.117z",
        )
    }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()
}
