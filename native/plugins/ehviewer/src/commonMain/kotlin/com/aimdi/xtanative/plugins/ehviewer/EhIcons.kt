package com.aimdi.xtanative.plugins.ehviewer

import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.pathIcon

/** The icons XTA's EH screens draw that the core set lacks, from Material's (Apache 2.0) paths. */
internal object EhIcons {
    /** XTA's mark for EH, collections_bookmark. */
    val Mark: ImageVector by lazy {
        pathIcon("Mark", "M20,2H8C6.9,2 6,2.9 6,4v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4C22,2.9 21.1,2 20,2zM20,16H8V4h3v8l2.5,-1.5L16,12V4h4V16zM4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6z")
    }
    val Popular: ImageVector by lazy {
        pathIcon(
            "Popular",
            "M13.5,0.67s0.74,2.65 0.74,4.8c0,2.06 -1.35,3.73 -3.41,3.73 -2.07,0 -3.63,-1.67 -3.63,-3.73l0.03,-0.36C5.21,7.51 4,10.62 4,14" +
                "c0,4.42 3.58,8 8,8s8,-3.58 8,-8C20,8.61 17.41,3.8 13.5,0.67zM11.71,19c-1.78,0 -3.22,-1.4 -3.22,-3.14 0,-1.62 1.05,-2.76 2.81,-3.12" +
                " 1.77,-0.36 3.6,-1.21 4.62,-2.58 0.39,1.29 0.59,2.65 0.59,4.04 0,2.65 -2.15,4.8 -4.8,4.8z",
        )
    }
    val Toplist: ImageVector by lazy {
        pathIcon(
            "Toplist",
            "M19,5h-2V3H7v2H5C3.9,5 3,5.9 3,7v1c0,2.55 1.92,4.63 4.39,4.94c0.63,1.5 1.98,2.63 3.61,2.96V19H7v2h10v-2h-4v-3.1" +
                "c1.63,-0.33 2.98,-1.46 3.61,-2.96C19.08,12.63 21,10.55 21,8V7C21,5.9 20.1,5 19,5zM5,8V7h2v3.82C5.84,10.4 5,9.3 5,8z" +
                "M12,14c-1.65,0 -3,-1.35 -3,-3V5h6v6C15,12.65 13.65,14 12,14zM19,8c0,1.3 -0.84,2.4 -2,2.82V7h2V8z",
        )
    }
    val Watched: ImageVector by lazy {
        pathIcon(
            "Watched",
            "M12,6c3.79,0 7.17,2.13 8.82,5.5C19.17,14.87 15.79,17 12,17s-7.17,-2.13 -8.82,-5.5C4.83,8.13 8.21,6 12,6m0,-2C7,4 2.73,7.11 1,11.5" +
                " 2.73,15.89 7,19 12,19s9.27,-3.11 11,-7.5C21.27,7.11 17,4 12,4zM12,9c1.38,0 2.5,1.12 2.5,2.5S13.38,14 12,14s-2.5,-1.12 -2.5,-2.5" +
                "S10.62,9 12,9m0,-2c-2.48,0 -4.5,2.02 -4.5,4.5S9.52,16 12,16s4.5,-2.02 4.5,-4.5S14.48,7 12,7z",
        )
    }
    val Person: ImageVector by lazy {
        pathIcon(
            "Person",
            "M12,5.9c1.16,0 2.1,0.94 2.1,2.1s-0.94,2.1 -2.1,2.1S9.9,9.16 9.9,8s0.94,-2.1 2.1,-2.1m0,9c2.97,0 6.1,1.46 6.1,2.1v1.1H5.9V17" +
                "c0,-0.64 3.13,-2.1 6.1,-2.1M12,4C9.79,4 8,5.79 8,8s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4zM12,13c-2.67,0 -8,1.34 -8,4v3h16v-3" +
                "c0,-2.66 -5.33,-4 -8,-4z",
        )
    }
    val Pages: ImageVector by lazy {
        pathIcon("Pages", "M8,16h8v2H8zM8,12h8v2H8zM14,2H6c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6zM18,20H6V4h7v5h5v11z")
    }
    val Storage: ImageVector by lazy {
        pathIcon("Storage", "M18,4v16H6V8.83L10.83,4H18M18,2h-8L4,8v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM9,7h2v4H9zM12,7h2v4h-2zM15,7h2v4h-2z")
    }
    val Calendar: ImageVector by lazy {
        pathIcon("Calendar", "M20,3h-1V1h-2v2H7V1H5v2H4c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM20,21H4V10h16v11zM20,8H4V5h16v3z")
    }
    val Read: ImageVector by lazy {
        pathIcon(
            "Read",
            "M21,5c-1.11,-0.35 -2.33,-0.5 -3.5,-0.5c-1.95,0 -4.05,0.4 -5.5,1.5c-1.45,-1.1 -3.55,-1.5 -5.5,-1.5S2.45,4.9 1,6v14.65" +
                "c0,0.25 0.25,0.5 0.5,0.5c0.1,0 0.15,-0.05 0.25,-0.05C3.1,20.45 5.05,20 6.5,20c1.95,0 4.05,0.4 5.5,1.5c1.35,-0.85 3.8,-1.5 5.5,-1.5" +
                "c1.65,0 3.35,0.3 4.75,1.05c0.1,0.05 0.15,0.05 0.25,0.05c0.25,0 0.5,-0.25 0.5,-0.5V6C22.4,5.55 21.75,5.25 21,5zM21,18.5" +
                "c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5c-1.7,0 -4.15,0.65 -5.5,1.5V8c1.35,-0.85 3.8,-1.5 5.5,-1.5c1.2,0 2.4,0.15 3.5,0.5V18.5z",
        )
    }
    val StarHalf: ImageVector by lazy {
        pathIcon("StarHalf", "M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24zM12,15.4V6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z")
    }
    val StarEmpty: ImageVector by lazy {
        pathIcon(
            "StarEmpty",
            "M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24zM12,15.4l-3.76,2.27 1,-4.28" +
                " -3.32,-2.88 4.38,-0.38L12,6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z",
        )
    }
    val BrokenImage: ImageVector by lazy {
        pathIcon("BrokenImage", "M21,5v6.59l-3,-3.01 -4,4.01 -4,-4 -4,4 -3,-3.01V5c0,-1.1 0.9,-2 2,-2h14c1.1,0 2,0.9 2,2zM18,11.42l3,3.01V19c0,1.1 -0.9,2 -2,2H5c-1.1,0 -2,-0.9 -2,-2v-6.58l3,2.99 4,-4 4,4 4,-3.99z")
    }
    val Collections: ImageVector by lazy {
        pathIcon("Collections", "M22,16V4c0,-1.1 -0.9,-2 -2,-2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2zM11,12l2.03,2.71L16,11l4,5H8l3,-4zM2,6v14c0,1.1 0.9,2 2,2h14v-2H4V6H2z")
    }
    val LeftToRight: ImageVector by lazy { pathIcon("LeftToRight", "M9,10v5h2V4h2v11h2V4h2V2H9C6.79,2 5,3.79 5,6s1.79,4 4,4zM21,18l-4,-4v3H5v2h12v3l4,-4z") }
    val RightToLeft: ImageVector by lazy { pathIcon("RightToLeft", "M10,10v5h2V4h2v11h2V4h2V2h-8C7.79,2 6,3.79 6,6s1.79,4 4,4zM8,17v-3l-4,4 4,4v-3h12v-2H8z") }
    val Vertical: ImageVector by lazy {
        pathIcon("Vertical", "M3,21h17v-2H3v2zM19,8H4c-0.55,0 -1,0.45 -1,1v6c0,0.55 0.45,1 1,1h15c0.55,0 1,-0.45 1,-1V9c0,-0.55 -0.45,-1 -1,-1zM18,14H5v-4h13v4zM3,3v2h17V3H3z")
    }
}
