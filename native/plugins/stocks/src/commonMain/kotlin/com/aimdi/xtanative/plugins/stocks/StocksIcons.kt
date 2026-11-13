package com.aimdi.xtanative.plugins.stocks

import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.pathIcon

/** The Material icons (Apache 2.0) XTA's Stocks screens draw that Compose's core set lacks. */
internal object StocksIcons {
    val CandlestickChart: ImageVector by lazy { pathIcon("CandlestickChart", "M9,4H7v2H5v12h2v2h2v-2h2V6H9V4zM19,8h-2V4h-2v4h-2v7h2v5h2v-5h2V8z") }
    val ShowChart: ImageVector by lazy { pathIcon("ShowChart", "M3.5,18.49l6,-6.01l4,4L22,6.92l-1.41,-1.41l-7.09,7.97l-4,-4L2,16.99L3.5,18.49z") }
    val ContentCopy: ImageVector by lazy {
        pathIcon(
            "ContentCopy",
            "M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1zM19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7C21,5.9 20.1,5 19,5zM19,21H8V7h11V21z",
        )
    }
    val CloudOff: ImageVector by lazy {
        pathIcon(
            "CloudOff",
            "M24,15c0,-2.64 -2.05,-4.78 -4.65,-4.96C18.67,6.59 15.64,4 12,4c-1.33,0 -2.57,0.36 -3.65,0.97l1.49,1.49C10.51,6.17 11.23,6 12,6" +
                "c3.04,0 5.5,2.46 5.5,5.5v0.5H19c1.66,0 3,1.34 3,3c0,0.99 -0.48,1.85 -1.21,2.4l1.41,1.41C23.29,17.92 24,16.55 24,15z" +
                "M4.41,3.86L3,5.27l2.77,2.77h-0.42C2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h11.73l2,2l1.41,-1.41L4.41,3.86z" +
                "M6,18c-2.21,0 -4,-1.79 -4,-4s1.79,-4 4,-4h1.73l8,8H6z",
        )
    }
    /** The Posts tab: forum, outlined. */
    val ForumOutlined: ImageVector by lazy {
        pathIcon(
            "ForumOutlined",
            "M15,4v7H5.17L4,12.17V4h11M16,2H3C2.45,2 2,2.45 2,3v14l4,-4h10c0.55,0 1,-0.45 1,-1V3C17,2.45 16.55,2 16,2z" +
                "M21,6h-2v9H6v2c0,0.55 0.45,1 1,1h11l4,4V7C22,6.45 21.55,6 21,6z",
        )
    }

    /** The Markets tab: public. */
    val Public: ImageVector by lazy {
        pathIcon(
            "Public",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM4,12c0,-0.61 0.08,-1.21 0.21,-1.78L8.99,15v1" +
                "c0,1.1 0.9,2 2,2v1.93C7.06,19.43 4,16.07 4,12zM17.89,17.4c-0.26,-0.81 -1,-1.4 -1.9,-1.4h-1v-3c0,-0.55 -0.45,-1 -1,-1h-6v-2h2" +
                "c0.55,0 1,-0.45 1,-1V7h2c1.1,0 2,-0.9 2,-2v-0.41C17.92,5.77 20,8.65 20,12c0,2.08 -0.81,3.98 -2.11,5.4z",
        )
    }
}
