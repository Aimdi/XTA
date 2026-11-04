package com.aimdi.xtanative.plugins.rss

import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.pathIcon

/** The Material icons (Apache 2.0) XTA's RSS screens draw that Compose's core set lacks. */
internal object RssIcons {
    val Feed: ImageVector by lazy {
        pathIcon(
            "RssFeed",
            "M6.18,17.82m-2.18,0a2.18,2.18 0,1 1,4.36 0a2.18,2.18 0,1 1,-4.36 0" +
                "M4,4.44v2.83c7.03,0 12.73,5.7 12.73,12.73h2.83c0,-8.59 -6.97,-15.56 -15.56,-15.56z" +
                "M4,10.1v2.83c3.9,0 7.07,3.17 7.07,7.07h2.83c0,-5.47 -4.43,-9.9 -9.9,-9.9z",
        )
    }
    val Article: ImageVector by lazy {
        pathIcon(
            "Article",
            "M19,5v14H5V5H19M19,3H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3L19,3z" +
                "M14,17H7v-2h7V17zM17,13H7v-2h10V13zM17,9H7V7h10V9z",
        )
    }
    val FilterList: ImageVector by lazy { pathIcon("FilterList", "M10,18h4v-2h-4v2zM3,6v2h18L21,6L3,6zM6,13h12v-2L6,11v2z") }
    val FilterListOff: ImageVector by lazy {
        pathIcon(
            "FilterListOff",
            "M10.83,8H21V6H8.83L10.83,8zM15.83,13H18v-2h-4.17L15.83,13zM14,16.83V18h-4v-2h3.17l-3,-3H6v-2h2.17l-3,-3H3V6h0.17L1.39,4.22" +
                "l1.41,-1.41l18.38,18.38l-1.41,1.41L14,16.83z",
        )
    }
    val Upload: ImageVector by lazy { pathIcon("Upload", "M5,20h14v-2H5V20zM5,10h4v6h6v-6h4l-7,-7L5,10z") }
    val Download: ImageVector by lazy { pathIcon("Download", "M5,20h14v-2H5V20zM19,9h-4V3H9v6H5l7,7L19,9z") }
}
