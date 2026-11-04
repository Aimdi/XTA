package com.aimdi.xtanative.plugins.websources

import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.pathIcon

/** The Material icons (Apache 2.0) XTA's web sources screens draw that Compose's core set lacks. */
internal object WebSourcesIcons {
    val TravelExplore: ImageVector by lazy {
        pathIcon(
            "TravelExplore",
            "M19.3,16.9c0.4,-0.7 0.7,-1.5 0.7,-2.4c0,-2.5 -2,-4.5 -4.5,-4.5S11,12 11,14.5s2,4.5 4.5,4.5c0.9,0 1.7,-0.3 2.4,-0.7l3.2,3.2l1.4,-1.4L19.3,16.9z" +
                "M15.5,17c-1.4,0 -2.5,-1.1 -2.5,-2.5s1.1,-2.5 2.5,-2.5s2.5,1.1 2.5,2.5S16.9,17 15.5,17z" +
                "M12,20v2C6.48,22 2,17.52 2,12C2,6.48 6.48,2 12,2c4.84,0 8.87,3.44 9.8,8h-2.07c-0.64,-2.46 -2.4,-4.47 -4.73,-5.41V5c0,1.1 -0.9,2 -2,2h-2v2" +
                "c0,0.55 -0.45,1 -1,1H8v2h2v3H9l-4.79,-4.79C4.08,10.79 4,11.38 4,12C4,16.41 7.59,20 12,20z",
        )
    }
    val Public: ImageVector by lazy {
        pathIcon(
            "Public",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM11,19.93c-3.95,-0.49 -7,-3.85 -7,-7.93 0,-0.62 0.08,-1.21 0.21,-1.79L9,15v1" +
                "c0,1.1 0.9,2 2,2v1.93zM17.9,17.39c-0.26,-0.81 -1,-1.39 -1.9,-1.39h-1v-3c0,-0.55 -0.45,-1 -1,-1H8v-2h2c0.55,0 1,-0.45 1,-1V7h2c1.1,0 2,-0.9 2,-2" +
                "v-0.41c2.93,1.19 5,4.06 5,7.41 0,2.08 -0.8,3.97 -2.1,5.39z",
        )
    }
    val Inbox: ImageVector by lazy {
        pathIcon("Inbox", "M19,3H4.99c-1.11,0 -1.98,0.89 -1.98,2L3,19c0,1.1 0.88,2 1.99,2H19c1.1,0 2,-0.9 2,-2V5c0,-1.11 -0.9,-2 -2,-2zM19,15h-4c0,1.66 -1.35,3 -3,3s-3,-1.34 -3,-3H4.99V5H19v10z")
    }
    val ChatBubble: ImageVector by lazy { pathIcon("ChatBubbleOutline", "M20,2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM20,16H6l-2,2V4h16v12z") }
    val Article: ImageVector by lazy {
        pathIcon(
            "Article",
            "M19,5v14H5V5H19M19,3H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3L19,3z" +
                "M14,17H7v-2h7V17zM17,13H7v-2h10V13zM17,9H7V7h10V9z",
        )
    }
    val BrokenImage: ImageVector by lazy {
        pathIcon("BrokenImage", "M21,5v6.59l-3,-3.01 -4,4.01 -4,-4 -4,4 -3,-3.01V5c0,-1.1 0.9,-2 2,-2h14c1.1,0 2,0.9 2,2zM18,11.42l3,3.01V19c0,1.1 -0.9,2 -2,2H5c-1.1,0 -2,-0.9 -2,-2v-6.58l3,2.99 4,-4 4,4 4,-3.99z")
    }
    val AddCircle: ImageVector by lazy {
        pathIcon("AddCircleOutline", "M13,7h-2v4H7v2h4v4h2v-4h4v-2h-4V7zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z")
    }
}
