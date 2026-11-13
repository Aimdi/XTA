package com.aimdi.xtanative.plugins.karakeep

import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.pathIcon

/** The glyphs XTA's Karakeep plugin draws, from Material's paths (Apache 2.0). */
internal object KarakeepIcons {
    /** XTA's mark for the plugin (bookmark_add). */
    val Mark: ImageVector by lazy {
        pathIcon(
            "BookmarkAdd",
            "M21,7h-2v2h-2V7h-2V5h2V3h2v2h2V7zM19,21l-7,-3l-7,3V5c0,-1.1 0.9,-2 2,-2l7,0c-0.63,0.84 -1,1.87 -1,3" +
                "c0,2.76 2.24,5 5,5c0.34,0 0.68,-0.03 1,-0.08V21z",
        )
    }

    /** Save to Karakeep in a post's sheet (bookmark_add_outlined). */
    val Save: ImageVector by lazy {
        pathIcon(
            "BookmarkAddOutlined",
            "M17,11v6.97l-5,-2.14l-5,2.14V5h6V3H7C5.9,3 5,3.9 5,5v16l7,-3l7,3V11H17zM21,7h-2v2h-2V7h-2V5h2V3h2v2h2V7z",
        )
    }
}
