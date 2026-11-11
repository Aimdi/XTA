package com.aimdi.xtanative.plugins.hackernews

import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.pathIcon

/** The glyphs XTA's Hacker News tabs draw (hn_screen.dart) that neither the core icons nor the app's have, from Material's (Apache 2.0) paths. */
internal object HnIcons {
    /** Top: whatshot_outlined. */
    val Whatshot: ImageVector by lazy {
        pathIcon(
            "Whatshot",
            "M11.57,13.16c-1.36,0.28 -2.17,1.16 -2.17,2.41c0,1.34 1.11,2.42 2.49,2.42c2.05,0 3.71,-1.66 3.71,-3.71c0,-1.07 -0.15,-2.12 -0.46,-3.12" +
                "C14.35,12.23 12.94,12.88 11.57,13.16zM13.5,0.67c0,0 0.74,2.65 0.74,4.8c0,2.06 -1.35,3.73 -3.41,3.73c-2.07,0 -3.63,-1.67 -3.63,-3.73" +
                "l0.03,-0.36C5.21,7.51 4,10.62 4,14c0,4.42 3.58,8 8,8s8,-3.58 8,-8C20,8.61 17.41,3.8 13.5,0.67zM12,20c-3.31,0 -6,-2.69 -6,-6" +
                "c0,-1.53 0.3,-3.04 0.86,-4.43c1.01,1.01 2.41,1.63 3.97,1.63c2.66,0 4.75,-1.83 5.28,-4.43C17.34,8.97 18,11.44 18,14C18,17.31 15.31,20 12,20z",
        )
    }

    /** Best: emoji_events_outlined. */
    val EmojiEvents: ImageVector by lazy {
        pathIcon(
            "EmojiEvents",
            "M19,5h-2V3H7v2H5C3.9,5 3,5.9 3,7v1c0,2.55 1.92,4.63 4.39,4.94c0.63,1.5 1.98,2.63 3.61,2.96V19H7v2h10v-2h-4v-3.1" +
                "c1.63,-0.33 2.98,-1.46 3.61,-2.96C19.08,12.63 21,10.55 21,8V7C21,5.9 20.1,5 19,5zM5,8V7h2v3.82C5.84,10.4 5,9.3 5,8z" +
                "M12,14c-1.65,0 -3,-1.35 -3,-3V5h6v6C15,12.65 13.65,14 12,14zM19,8c0,1.3 -0.84,2.4 -2,2.82V7h2V8z",
        )
    }

    /** Ask: help_outline. */
    val HelpOutline: ImageVector by lazy {
        pathIcon(
            "HelpOutline",
            "M11,18h2v-2h-2v2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8" +
                " 8,3.59 8,8 -3.59,8 -8,8zM12,6c-2.21,0 -4,1.79 -4,4h2c0,-1.1 0.9,-2 2,-2s2,0.9 2,2c0,2 -3,1.75 -3,5h2c0,-2.25 3,-2.5 3,-5" +
                " 0,-2.21 -1.79,-4 -4,-4z",
        )
    }

    /** Show: slideshow_outlined. */
    val Slideshow: ImageVector by lazy {
        pathIcon(
            "Slideshow",
            "M10,8v8l5,-4 -5,-4zM19,3L5,3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2L21,5c0,-1.1 -0.9,-2 -2,-2zM19,19L5,19L5,5h14v14z",
        )
    }

    /** Jobs: work_outline. */
    val WorkOutline: ImageVector by lazy {
        pathIcon(
            "WorkOutline",
            "M14,6V4h-4v2h4zM4,8v11h16V8H4zM20,6c1.11,0 2,0.89 2,2v11c0,1.11 -0.89,2 -2,2H4c-1.11,0 -2,-0.89 -2,-2l0.01,-11" +
                "c0,-1.11 0.88,-2 1.99,-2h4V4c0,-1.11 0.89,-2 2,-2h4c1.11,0 2,0.89 2,2v2h4z",
        )
    }

    /** An account with nothing to show: person_outline. */
    val PersonOutline: ImageVector by lazy {
        pathIcon(
            "PersonOutline",
            "M12,5.9c1.16,0 2.1,0.94 2.1,2.1s-0.94,2.1 -2.1,2.1S9.9,9.16 9.9,8s0.94,-2.1 2.1,-2.1m0,9c2.97,0 6.1,1.46 6.1,2.1v1.1L5.9,18.1L5.9,17" +
                "c0,-0.64 3.13,-2.1 6.1,-2.1M12,4C9.79,4 8,5.79 8,8s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4zM12,13c-2.67,0 -8,1.34 -8,4v3h16v-3" +
                "c0,-2.66 -5.33,-4 -8,-4z",
        )
    }

    /** Every glyph, for the test that draws them all. */
    val all: List<ImageVector> get() = listOf(Whatshot, EmojiEvents, HelpOutline, Slideshow, WorkOutline, PersonOutline)
}
