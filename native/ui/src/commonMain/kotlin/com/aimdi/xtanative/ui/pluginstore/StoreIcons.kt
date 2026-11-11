package com.aimdi.xtanative.ui.pluginstore

import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.pathIcon

/** Material's glyphs (Apache 2.0) the store draws and Material's core set lacks. */
internal object StoreIcons {
    /** A search that found nothing: search_off. */
    val SearchOff: ImageVector by lazy {
        pathIcon(
            "SearchOff",
            "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3 6.08,3 3.28,5.64 3.03,9h2.02" +
                "C5.3,6.75 7.18,5 9.5,5 11.99,5 14,7.01 14,9.5S11.99,14 9.5,14c-0.17,0 -0.33,-0.03 -0.5,-0.05v2.02c0.17,0.02 0.33,0.03 0.5,0.03" +
                " 1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM6.47,10.82 4,13.29l-2.47,-2.47 -0.71,0.71L3.29,14" +
                " 0.82,16.47l0.71,0.71L4,14.71l2.47,2.47 0.71,-0.71L4.71,14l2.47,-2.47z",
        )
    }

    /** The catalogue could not be reached: cloud_off. */
    val CloudOff: ImageVector by lazy {
        pathIcon(
            "CloudOff",
            "M19.35,10.04C18.67,6.59 15.64,4 12,4c-1.48,0 -2.85,0.43 -4.01,1.17l1.46,1.46C10.21,6.23 11.08,6 12,6c3.04,0 5.5,2.46 5.5,5.5v0.5" +
                "H19c1.66,0 3,1.34 3,3 0,1.13 -0.64,2.11 -1.56,2.62l1.45,1.45C23.16,18.16 24,16.68 24,15c0,-2.64 -2.05,-4.78 -4.65,-4.96z" +
                "M3,5.27l2.75,2.74C2.56,8.15 0,10.77 0,14c0,3.31 2.69,6 6,6h11.73l2,2L21,20.73 4.27,4 3,5.27zM7.73,10l8,8H6c-2.21,0 -4,-1.79 -4,-4" +
                "s1.79,-4 4,-4h1.73z",
        )
    }
}
