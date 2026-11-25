package com.aimdi.xtanative.ui.chrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.LocalOnline
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.chrome_offline_bar
import com.aimdi.xtanative.ui.settings.isDesktop
import org.jetbrains.compose.resources.stringResource

/**
 * [content] under a slim bar while the connection is gone, saying what the
 * screens show meanwhile: what was read before. It slides in and out, or
 * simply appears where motion is reduced. On a phone the bar stands under
 * the status bar, whose room it takes over from the screens while it shows.
 */
@Composable
internal fun OfflineFrame(content: @Composable () -> Unit) {
    val flow = LocalOnline.current
    val online by produceState(true, flow) { flow.collect { value = it } }
    val reduced = LocalReducedMotion.current
    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !online,
            enter = if (reduced) EnterTransition.None else expandVertically() + fadeIn(),
            exit = if (reduced) ExitTransition.None else shrinkVertically() + fadeOut(),
        ) { OfflineBar() }
        val below = if (online || isDesktop) Modifier else Modifier.consumeWindowInsets(WindowInsets.statusBars)
        Box(Modifier.fillMaxWidth().weight(1f).then(below)) { content() }
    }
}

/** The bar itself: one line, quiet colors, read out once by a screen reader. */
@Composable
private fun OfflineBar() {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        Box(Modifier.fillMaxWidth().statusBarsPadding().height(28.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(Res.string.chrome_offline_bar),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
