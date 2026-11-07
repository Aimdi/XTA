package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.media.rememberAudioSessions
import com.aimdi.xtanative.ui.media.rememberContinuityPlayers
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import java.text.NumberFormat
import java.util.Locale

/**
 * A podcast post's episode above its show notes, as XTA's
 * SubstackAudioPlayer: play and pause, where it is, and its speed. The
 * playing lives in [SubstackPodcast], so leaving the article changes nothing
 * about what is heard. Where the platform plays nothing here (a desktop
 * without libmpv), the episode opens in the browser.
 */
@Composable
internal fun SubstackAudioPlayer(app: SubstackApp, url: String, title: String) {
    val players = rememberContinuityPlayers()
    val sessions = rememberAudioSessions()
    LaunchedEffect(players, sessions) { app.podcast.attach(players, sessions) }
    val playback by app.podcast.state.collectAsState()
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (players == null) return@Row OutsidePlayer(url, title)
        val mine = playback.url == url
        val known = mine && playback.durationMs > 0
        PlayButton(mine && playback.loading, mine && playback.failed, mine && playback.playing, title) { app.podcast.toggle(url, title) }
        if (known) {
            Slider(
                value = playback.positionMs.coerceIn(0, playback.durationMs).toFloat(),
                onValueChange = { app.podcast.seek(it.toLong()) },
                valueRange = 0f..playback.durationMs.toFloat(),
                enabled = !playback.loading,
                modifier = Modifier.weight(1f),
            )
            Text(
                SubstackPodcast.clock(playback.positionMs) + " / " + SubstackPodcast.clock(playback.durationMs),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(end = 8.dp),
            )
        } else {
            Text(
                if (mine && playback.failed) pluginString(Strings.load_error) else title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        SpeedMenu(playback.speed, app.podcast::setSpeed)
    }
}

@Composable
private fun PlayButton(loading: Boolean, failed: Boolean, playing: Boolean, title: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = !loading) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            else -> Icon(
                if (failed) Icons.Default.Refresh else if (playing) SubstackIcons.PauseCircle else SubstackIcons.PlayCircle,
                if (failed) pluginString(Strings.retry) else title,
                Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The row of a desktop without libmpv: the episode in the browser. */
@Composable
private fun RowScope.OutsidePlayer(url: String, title: String) {
    val outside = rememberOpenOutside()
    IconButton(onClick = { outside(url) }) { Icon(SubstackIcons.PlayCircle, title, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary) }
    Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    TextButton(onClick = { outside(url) }) {
        Icon(SubstackIcons.OpenInNew, null, Modifier.padding(end = 4.dp).size(18.dp))
        Text(pluginString(Strings.open_in_browser))
    }
}

/** The playback speed, shown as itself ("1.5×"). */
@Composable
private fun SpeedMenu(speed: Double, onSelect: (Double) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val format = NumberFormat.getNumberInstance(Locale.forLanguageTag(currentLanguage()))
    val label = pluginString(Strings.playback_speed)
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = label }) {
            Text(format.format(speed) + "×", style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SubstackPodcast.SPEEDS.forEach { value ->
                DropdownMenuItem(
                    text = { Text(format.format(value) + "×") },
                    leadingIcon = { if (value == speed) Icon(Icons.Default.Check, null) else Box(Modifier.size(24.dp)) },
                    onClick = { open = false; onSelect(value) },
                )
            }
        }
    }
}
