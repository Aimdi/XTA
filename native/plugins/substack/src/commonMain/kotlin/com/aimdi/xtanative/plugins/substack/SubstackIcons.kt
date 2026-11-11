package com.aimdi.xtanative.plugins.substack

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.plugins.brandMarkIcon

/** The Material icons XTA's Substack screens draw that Compose's core set lacks (24 × 24). */
internal object SubstackIcons {
    /** XTA's mark for Substack: its bars over folded paper (plugin_marks.dart's _substack, Simple Icons, CC0), fitted as every mark is. */
    val Mark: ImageVector by lazy {
        brandMarkIcon(
            "Mark",
            "M1.46,0H22.54V2.836H1.46ZM1.46,5.406H22.54V8.242H1.46ZM1.46,10.812V24L12,18.11L22.54,24V10.812Z",
            left = 0f,
            top = 0f,
            side = 24f,
        )
    }
    val Newspaper: ImageVector by lazy {
        icon(
            "Newspaper",
            "M22,3l-1.67,1.67L18.67,3L17,4.67L15.33,3l-1.66,1.67L12,3l-1.67,1.67L8.67,3L7,4.67L5.33,3L3.67,4.67L2,3v16c0,1.1 0.9,2 2,2l16,0" +
                "c1.1,0 2,-0.9 2,-2V3zM11,19H4v-6h7V19zM20,19h-7v-2h7V19zM20,15h-7v-2h7V15zM20,11H4V8h16V11z",
        )
    }
    val Inbox: ImageVector by lazy {
        icon("Inbox", "M19,3H4.99c-1.11,0 -1.98,0.89 -1.98,2L3,19c0,1.1 0.88,2 1.99,2H19c1.1,0 2,-0.9 2,-2V5c0,-1.11 -0.9,-2 -2,-2zM19,15h-4c0,1.66 -1.35,3 -3,3s-3,-1.34 -3,-3H4.99V5H19v10z")
    }
    val Notes: ImageVector by lazy { icon("Notes", "M3,18h12v-2H3v2zM3,6v2h18V6H3zM3,13h18v-2H3v2z") }
    val Bookmark: ImageVector by lazy { icon("Bookmark", "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z") }
    val BookmarkBorder: ImageVector by lazy {
        icon("BookmarkBorder", "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2zM17,18l-5,-2.18L7,18V5h10v13z")
    }
    val PushPin: ImageVector by lazy {
        icon("PushPin", "M16,9V4l1,0c0.55,0 1,-0.45 1,-1v0c0,-0.55 -0.45,-1 -1,-1H7C6.45,2 6,2.45 6,3v0c0,0.55 0.45,1 1,1l1,0v5c0,1.66 -1.34,3 -3,3h0v2h5.97v7l1,1l1,-1v-7H19v-2h0C17.34,12 16,10.66 16,9z")
    }
    val Comment: ImageVector by lazy { icon("Comment", "M21.99,4c0,-1.1 -0.89,-2 -1.99,-2H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h14l4,4 -0.01,-18zM20,4v13.17L18.83,16H4V4h16z") }
    val Sort: ImageVector by lazy { icon("Sort", "M3,18h6v-2H3v2zM3,6v2h18V6H3zM3,13h12v-2H3v2z") }
    val Tune: ImageVector by lazy {
        icon("Tune", "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z")
    }
    val OpenInNew: ImageVector by lazy {
        icon("OpenInNew", "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z")
    }
    val Quote: ImageVector by lazy { icon("Quote", "M6,17h3l2,-4V7H5v6h3zM14,17h3l2,-4V7h-6v6h3z") }
    val Contents: ImageVector by lazy {
        icon(
            "Contents",
            "M4,10.5c-0.83,0 -1.5,0.67 -1.5,1.5s0.67,1.5 1.5,1.5 1.5,-0.67 1.5,-1.5 -0.67,-1.5 -1.5,-1.5zM4,4.5c-0.83,0 -1.5,0.67 -1.5,1.5S3.17,7.5 4,7.5 5.5,6.83 5.5,6 " +
                "4.83,4.5 4,4.5zM4,16.67c-0.74,0 -1.33,0.6 -1.33,1.33s0.6,1.33 1.33,1.33 1.33,-0.6 1.33,-1.33 -0.59,-1.33 -1.33,-1.33zM7,19h14v-2H7v2zM7,13h14v-2H7v2zM7,5v2h14V5H7z",
        )
    }
    val Find: ImageVector by lazy {
        icon(
            "Find",
            "M7,9H2V7h5V9zM7,12H2v2h5V12zM20.59,19l-3.83,-3.83C15.96,15.69 15.02,16 14,16c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5s5,2.24 5,5c0,1.02 -0.31,1.96 -0.83,2.75" +
                "L22,17.59L20.59,19zM17,11c0,-1.65 -1.35,-3 -3,-3s-3,1.35 -3,3s1.35,3 3,3S17,12.65 17,11zM2,19h10v-2H2V19z",
        )
    }
    val PlayCircle: ImageVector by lazy { icon("PlayCircle", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,16.5v-9l6,4.5 -6,4.5z") }
    val PauseCircle: ImageVector by lazy { icon("PauseCircle", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM11,16H9V8h2v8zM15,16h-2V8h2v8z") }
    val Podcast: ImageVector by lazy {
        icon("Podcast", "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z")
    }
    val Explore: ImageVector by lazy {
        icon("Explore", "M12,10.9c-0.61,0 -1.1,0.49 -1.1,1.1s0.49,1.1 1.1,1.1c0.61,0 1.1,-0.49 1.1,-1.1s-0.49,-1.1 -1.1,-1.1zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM14.19,14.19L6,18l3.81,-8.19L18,6l-3.81,8.19z")
    }
    val Similar: ImageVector by lazy {
        icon(
            "Similar",
            "M10,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM10.35,14.01C7.62,13.91 2,15.27 2,18v2h9.54c-2.47,-2.76 -1.23,-5.89 -1.19,-5.99zM19.43,18.02" +
                "c0.36,-0.59 0.57,-1.28 0.57,-2.02 0,-2.21 -1.79,-4 -4,-4s-4,1.79 -4,4 1.79,4 4,4c0.74,0 1.43,-0.22 2.02,-0.57L20.59,22 22,20.59l-2.57,-2.57zM16,18c-1.1,0 -2,-0.9 -2,-2" +
                "s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z",
        )
    }
    val Upload: ImageVector by lazy { icon("Upload", "M9,16h6v-6h4l-7,-7 -7,7h4zM5,18h14v2H5z") }
    val Download: ImageVector by lazy { icon("Download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z") }
    val MarkRead: ImageVector by lazy {
        icon("MarkRead", "M12,14L3,8v10h9v2H3c-1.1,0 -2,-0.9 -2,-2V6c0,-1.1 0.9,-2 2,-2h16c1.1,0 2,0.9 2,2v7h-2V8L12,14zM12,12l7,-6H5L12,12zM17.34,22l-3.54,-3.54l1.41,-1.41l2.12,2.12l4.24,-4.24L23,16.34L17.34,22z")
    }
    val MarkUnread: ImageVector by lazy {
        icon("MarkUnread", "M22,8.98V18c0,1.1 -0.9,2 -2,2H4c-1.1,0 -2,-0.9 -2,-2V6c0,-1.1 0.9,-2 2,-2h10.1C14.04,4.32 14,4.66 14,5c0,1.48 0.65,2.79 1.67,3.71L12,11L4,6v2l8,5l5.3,-3.32C17.84,9.88 18.4,10 19,10C20.13,10 21.16,9.61 22,8.98zM16,5c0,1.66 1.34,3 3,3s3,-1.34 3,-3s-1.34,-3 -3,-3S16,3.34 16,5z")
    }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name = "Substack$name", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()
}
