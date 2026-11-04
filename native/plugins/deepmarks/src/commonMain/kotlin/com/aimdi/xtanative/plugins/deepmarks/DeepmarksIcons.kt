package com.aimdi.xtanative.plugins.deepmarks

import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.pathIcon

/** The glyphs XTA's Deepmarks plugin draws, from Material's paths (Apache 2.0). */
internal object DeepmarksIcons {
    /** XTA's mark for the plugin (hub): bookmarking on Nostr's relays. */
    val Mark: ImageVector by lazy {
        pathIcon(
            "Hub",
            "M8.4,18.2C8.78,18.7 9,19.32 9,20c0,1.66 -1.34,3 -3,3s-3,-1.34 -3,-3s1.34,-3 3,-3c0.44,0 0.85,0.09 1.23,0.26l1.41,-1.77" +
                "c-0.92,-1.03 -1.29,-2.39 -1.09,-3.69l-2.03,-0.68C4.98,11.95 4.06,12.5 3,12.5c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3s3,1.34 3,3" +
                "c0,0.07 0,0.14 -0.01,0.21l2.03,0.68c0.64,-1.21 1.82,-2.1 3.22,-2.32l0,-2.16C9.96,5.57 9,4.4 9,3c0,-1.66 1.34,-3 3,-3" +
                "s3,1.34 3,3c0,1.4 -0.96,2.57 -2.25,2.91v2.16c1.4,0.22 2.58,1.11 3.22,2.32l2.03,-0.68C18,9.64 18,9.57 18,9.5" +
                "c0,-1.66 1.34,-3 3,-3s3,1.34 3,3s-1.34,3 -3,3c-1.06,0 -1.98,-0.55 -2.52,-1.37l-2.03,0.68c0.2,1.29 -0.16,2.65 -1.09,3.69" +
                "l1.41,1.77C17.15,17.09 17.56,17 18,17c1.66,0 3,1.34 3,3s-1.34,3 -3,3s-3,-1.34 -3,-3c0,-0.68 0.22,-1.3 0.6,-1.8" +
                "l-1.41,-1.77c-1.35,0.75 -3.01,0.76 -4.37,0L8.4,18.2z",
        )
    }

    /** Save to Deepmarks in a post's sheet (bookmarks_outlined). */
    val Save: ImageVector by lazy {
        pathIcon(
            "BookmarksOutlined",
            "M15,7v12.97l-4.21,-1.81l-0.79,-0.34l-0.79,0.34L5,19.97V7H15M19,1H8.99C7.89,1 7,1.9 7,3h10c1.1,0 2,0.9 2,2v13l2,1V3" +
                "C21,1.9 20.1,1 19,1L19,1zM15,5H5C3.9,5 3,5.9 3,7v16l7,-3l7,3V7C17,5.9 16.1,5 15,5L15,5z",
        )
    }
}
