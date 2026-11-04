package com.aimdi.xtanative.plugins.tiktok

import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.pathIcon

/** The icons XTA's TikTok screens draw that the core set lacks, from Material's (Apache 2.0) paths. */
internal object TikTokIcons {
    /** XTA's mark for TikTok, music_video. */
    val Mark: ImageVector by lazy {
        pathIcon(
            "Mark",
            "M21,3H3C1.89,3 1,3.89 1,5v14c0,1.1 0.89,2 2,2h18c1.1,0 2,-0.9 2,-2V5C23,3.89 22.1,3 21,3zM21,19H3V5h18V19z" +
                "M8,15c0,-1.66 1.34,-3 3,-3c0.35,0 0.69,0.07 1,0.18V6h5v2h-3v7.03c-0.02,1.64 -1.35,2.97 -3,2.97C9.34,18 8,16.66 8,15z",
        )
    }
    val Cookie: ImageVector by lazy {
        pathIcon(
            "Cookie",
            "M21.95,10.99c-1.79,-0.03 -3.7,-1.95 -2.68,-4.22c-2.98,1 -5.77,-1.59 -5.19,-4.56C6.95,0.71 2,6.58 2,12c0,5.52 4.48,10 10,10" +
                "C17.89,22 22.54,16.92 21.95,10.99zM8.5,15C7.67,15 7,14.33 7,13.5S7.67,12 8.5,12s1.5,0.67 1.5,1.5S9.33,15 8.5,15zM10.5,10" +
                "C9.67,10 9,9.33 9,8.5S9.67,7 10.5,7S12,7.67 12,8.5S11.33,10 10.5,10zM15,16c-0.55,0 -1,-0.45 -1,-1c0,-0.55 0.45,-1 1,-1" +
                "s1,0.45 1,1C16,15.55 15.55,16 15,16z",
        )
    }
}
