package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.pathIcon

/** The glyphs XTA's Mastodon screens draw that the core icon set lacks, from Material's paths (Apache 2.0). */
internal object MastodonIcons {
    /** The globe XTA marks the plugin with. */
    val Public: ImageVector by lazy {
        icon(
            "Public",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM11,19.93c-3.95,-0.49 -7,-3.85 -7,-7.93 0,-0.62 0.08,-1.21" +
                " 0.21,-1.79L9,15v1c0,1.1 0.9,2 2,2v1.93zM17.9,17.39c-0.26,-0.81 -1,-1.39 -1.9,-1.39h-1v-3c0,-0.55 -0.45,-1 -1,-1L8,12v-2h2" +
                "c0.55,0 1,-0.45 1,-1L11,7h2c1.1,0 2,-0.9 2,-2v-0.41c2.93,1.19 5,4.06 5,7.41 0,2.08 -0.8,3.97 -2.1,5.39z",
        )
    }
    val Explore: ImageVector by lazy {
        icon(
            "Explore",
            "M12,10.9c-0.61,0 -1.1,0.49 -1.1,1.1s0.49,1.1 1.1,1.1c0.61,0 1.1,-0.49 1.1,-1.1s-0.49,-1.1 -1.1,-1.1zM12,2C6.48,2 2,6.48 2,12" +
                "s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM14.19,14.19L6,18l3.81,-8.19L18,6l-3.81,8.19z",
        )
    }
    val Home: ImageVector by lazy { icon("Home", "M12,5.69l5,4.5V18h-2v-6H9v6H7v-7.81l5,-4.5M12,3L2,12h3v8h6v-6h2v6h6v-8h3L12,3z") }
    val Tag: ImageVector by lazy { icon("Tag", "M20,10V8h-4V4h-2v4h-4V4H8v4H4v2h4v4H4v2h4v4h2v-4h4v4h2v-4h4v-2h-4v-4H20zM14,14h-4v-4h4V14z") }
    val Sort: ImageVector by lazy { icon("Sort", "M3,18h6v-2H3v2zM3,6v2h18V6H3zM3,13h12v-2H3v2z") }
    val Stream: ImageVector by lazy {
        icon("Stream", "M3,17v-2c0,-1.1 0.9,-2 2,-2h14c1.1,0 2,0.9 2,2v2c0,1.1 -0.9,2 -2,2H5C3.9,19 3,18.1 3,17zM3,7v2c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2H5C3.9,5 3,5.9 3,7z")
    }
    val PersonAdd: ImageVector by lazy {
        icon("PersonAdd", "M15,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM6,10V7H4v3H1v2h3v3h2v-3h3v-2H6zM15,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z")
    }
    val PersonRemove: ImageVector by lazy {
        icon("PersonRemove", "M14,8c0,-2.21 -1.79,-4 -4,-4S6,5.79 6,8s1.79,4 4,4S14,10.21 14,8zM17,10v2h6v-2H17zM2,18v2h16v-2c0,-2.66 -5.33,-4 -8,-4S2,15.34 2,18z")
    }
    val ToTop: ImageVector by lazy { icon("ToTop", "M8,11h3v10h2V11h3l-4,-4 -4,4zM4,3v2h16V3H4z") }
    val Collections: ImageVector by lazy {
        icon(
            "Collections",
            "M22,16V4c0,-1.1 -0.9,-2 -2,-2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2zM11,12l2.03,2.71L16,11l4,5H8l3,-4z" +
                "M2,6v14c0,1.1 0.9,2 2,2h14v-2H4V6H2z",
        )
    }

    private fun icon(name: String, path: String): ImageVector = pathIcon(name, path)
}
