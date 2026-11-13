package com.aimdi.xtanative.plugins.immich

import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.pathIcon

/** The glyphs XTA's Immich plugin draws, from Material's paths (Apache 2.0). */
internal object ImmichIcons {
    /** XTA's mark for the plugin (photo_library). */
    val Mark: ImageVector by lazy {
        pathIcon(
            "PhotoLibrary",
            "M22,16V4c0,-1.1 -0.9,-2 -2,-2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2zM11,12l2.03,2.71L16,11l4,5H8l3,-4z" +
                "M2,6v14c0,1.1 0.9,2 2,2h14v-2H4V6H2z",
        )
    }
}
