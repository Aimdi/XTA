package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_alt_microblogging_group
import com.aimdi.xtanative.ui.generated.resources.home_alt_microblogging_group_detail
import com.aimdi.xtanative.ui.plugins.PluginSwitchRow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** How much of the mark each network's glyph takes, and the gap cut around Mastodon's (alt_microblogging_mark.dart). */
private const val GLYPH_SHARE = 0.66f
private const val GAP_SHARE = 0.07f

/**
 * Mastodon, Bluesky and Threads' entry's mark (alt_microblogging_mark.dart):
 * Bluesky's at the top left, Mastodon's over it at the bottom right, with a
 * gap cut out of Bluesky's around it rather than a ring painted in a
 * background colour, so the two stay apart on every surface.
 */
@Composable
internal fun MicroblogMark(networks: HomeNetworks, dimension: Dp) {
    val glyph = dimension * GLYPH_SHARE
    Box(Modifier.size(dimension)) {
        Box(
            Modifier
                .align(AbsoluteAlignment.TopLeft)
                .size(glyph)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val middle = dimension.toPx() - glyph.toPx() / 2
                    drawCircle(Color.Black, radius = glyph.toPx() * 0.42f + dimension.toPx() * GAP_SHARE, center = Offset(middle, middle), blendMode = BlendMode.Clear)
                },
            contentAlignment = Alignment.Center,
        ) { networks.Mark(BLUESKY, glyph) }
        Box(Modifier.align(AbsoluteAlignment.BottomRight).size(glyph), contentAlignment = Alignment.Center) { networks.Mark(MASTODON, glyph) }
    }
}

private const val BLUESKY = "bluesky"
private const val MASTODON = "mastodon"

/**
 * Settings › Plugin store's "Group microblogging services"
 * (alt_microblogging_setting.dart): Mastodon, Bluesky and Threads as one
 * entry in Home's timeline picker and Networks, on until the reader turns it
 * off; nothing else of theirs changes.
 */
@Composable
fun MicroblogGroupingRow() {
    val settings = LocalGraph.current.settings
    val grouped by remember(settings) { settings.observe(HomePrefs.altMicrobloggingGrouped) }.collectAsStateWithLifecycle(true)
    val scope = rememberCoroutineScope()
    PluginSwitchRow(
        stringResource(Res.string.home_alt_microblogging_group),
        grouped,
        onChange = { on -> scope.launch { settings.set(HomePrefs.altMicrobloggingGrouped, on) } },
        description = stringResource(Res.string.home_alt_microblogging_group_detail),
    )
}
