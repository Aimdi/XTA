package com.aimdi.xtanative.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The bottom bar's own icons for Home, Subscriptions, Discover and Saved
 * (the "new icons" canvas): drawn on a 24 grid with a 1.5 stroke, an outline
 * while a tab is idle and filled while it is selected. A network or group on
 * the bar keeps its own mark.
 */
internal object HomeBarIcons {
    /** The outlines' stroke, on the 24 grid. */
    const val STROKE = 1.5f

    /** Home: a rounded house with a short bar for its door. */
    val Home: ImageVector by lazy {
        icon(
            "BarHome",
            strokes = listOf(
                "M2.75 12.2C2.75 10 2.9 9.25 4.05 8.25L8.85 4.1C10.15 2.95 11 2.5 12 2.5C13 2.5 13.85 2.95 15.15 4.1L19.95 8.25" +
                    "C21.1 9.25 21.25 10 21.25 12.2C21.25 20.2 19.75 21.25 14 21.25H10C4.25 21.25 2.75 20.2 2.75 12.2Z",
                "M9.5 17H14.5",
            ),
        )
    }
    val HomeFilled: ImageVector by lazy {
        icon(
            "BarHomeFilled",
            fills = listOf(
                "M2 12.2C2 9.7 2.2 8.55 3.55 7.4L8.35 3.25C9.75 2.05 10.8 1.75 12 1.75C13.2 1.75 14.25 2.05 15.65 3.25L20.45 7.4" +
                    "C21.8 8.55 22 9.7 22 12.2C22 20.95 20.15 22 14 22H10C3.85 22 2 20.95 2 12.2Z" +
                    "M9.5 16.25A0.75 0.75 0 0 0 9.5 17.75H14.5A0.75 0.75 0 0 0 14.5 16.25Z",
            ),
        )
    }

    /** The brackets around a person: the same in both states. */
    private val brackets = listOf(
        "M6.25 3.5C5.1 4.2 4.5 5.15 4.5 6.25",
        "M17.75 3.5C18.9 4.2 19.5 5.15 19.5 6.25",
        "M4.75 13.6C3.45 14.3 2.75 15.3 2.75 16.4C2.75 17.6 3.6 18.65 5.1 19.4",
        "M19.25 13.6C20.55 14.3 21.25 15.3 21.25 16.4C21.25 17.6 20.4 18.65 18.9 19.4",
    )

    /** Subscriptions: a person in focus brackets. */
    val Subscriptions: ImageVector by lazy {
        icon(
            "BarSubscriptions",
            strokes = listOf(
                "M12 4.25A3 3 0 1 1 11.99 4.25Z",
                "M12 13.75C14.9 13.75 17 14.95 17 16.5C17 18.05 14.9 19.25 12 19.25C9.1 19.25 7 18.05 7 16.5C7 14.95 9.1 13.75 12 13.75Z",
            ) + brackets,
        )
    }
    val SubscriptionsFilled: ImageVector by lazy {
        icon(
            "BarSubscriptionsFilled",
            fills = listOf(
                "M12 3.5A3.75 3.75 0 1 1 11.99 3.5Z" +
                    "M12 13C15.3 13 17.75 14.45 17.75 16.5C17.75 18.55 15.3 20 12 20C8.7 20 6.25 18.55 6.25 16.5C6.25 14.45 8.7 13 12 13Z",
            ),
            strokes = brackets,
        )
    }

    /** Discover: a compass. */
    val Discover: ImageVector by lazy {
        icon(
            "BarDiscover",
            strokes = listOf(
                "M12 2.75A9.25 9.25 0 1 1 11.99 2.75Z",
                "M15.55 8.45L14.15 12.95C14.05 13.3 13.75 13.6 13.4 13.7L8.45 15.55L10.05 10.6C10.15 10.25 10.45 9.95 10.8 9.85Z",
            ),
        )
    }
    val DiscoverFilled: ImageVector by lazy {
        icon(
            "BarDiscoverFilled",
            fills = listOf(
                "M12 2A10 10 0 1 1 11.99 2Z" +
                    "M15.3 7.75C15.75 7.6 16.2 8.05 16.05 8.5L14.6 13.15C14.45 13.65 14.05 14.05 13.55 14.2L8.75 16" +
                    "C8.3 16.15 7.85 15.7 8 15.25L9.6 10.45C9.75 9.95 10.15 9.55 10.65 9.4Z",
            ),
        )
    }

    /** Saved: a bookmark with a bar across its top. */
    val Saved: ImageVector by lazy {
        icon(
            "BarSaved",
            strokes = listOf(
                "M16.8 2.75C18.4 2.75 19.25 3.55 19.25 5V20.1C19.25 20.75 18.65 21.1 18.1 20.8L12 17.3L5.9 20.8" +
                    "C5.35 21.1 4.75 20.75 4.75 20.1V5C4.75 3.55 5.6 2.75 7.2 2.75Z",
                "M9 7.75H15",
            ),
        )
    }
    val SavedFilled: ImageVector by lazy {
        icon(
            "BarSavedFilled",
            fills = listOf(
                "M16.8 2C18.85 2 20 3.15 20 5V20.1C20 21.35 18.75 22.05 17.7 21.45L12 18.15L6.3 21.45C5.25 22.05 4 21.35 4 20.1V5" +
                    "C4 3.15 5.15 2 7.2 2Z" +
                    "M9 7A0.75 0.75 0 0 0 9 8.5H15A0.75 0.75 0 0 0 15 7Z",
            ),
        )
    }

    /** [fills] filled even-odd, so a cut-out stays open, then [strokes] drawn as round 1.5 lines; tinted by the icon's colour. */
    private fun icon(name: String, fills: List<String> = emptyList(), strokes: List<String> = emptyList()): ImageVector {
        val builder = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        fills.forEach { builder.addPath(pathData = nodes(it), fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) }
        strokes.forEach {
            builder.addPath(
                pathData = nodes(it),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    private fun nodes(path: String) = PathParser().parsePathString(path).toNodes()
}
