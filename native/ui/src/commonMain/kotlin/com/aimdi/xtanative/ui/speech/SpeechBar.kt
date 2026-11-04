package com.aimdi.xtanative.ui.speech

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.services.speech.SpeechPlayback
import com.aimdi.xtanative.services.speech.Tts
import com.aimdi.xtanative.services.speech.TtsFailureAction
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_dismiss
import com.aimdi.xtanative.ui.generated.resources.reader_speech_get_sherpa
import com.aimdi.xtanative.ui.generated.resources.reader_speech_no_engine
import com.aimdi.xtanative.ui.generated.resources.reader_speech_no_text
import com.aimdi.xtanative.ui.generated.resources.reader_speech_playing
import com.aimdi.xtanative.ui.generated.resources.reader_speech_sherpa_missing
import com.aimdi.xtanative.ui.generated.resources.reader_speech_stop
import com.aimdi.xtanative.ui.generated.resources.reader_speech_unavailable
import com.aimdi.xtanative.ui.generated.resources.reader_speech_use_sherpa
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.media.ReaderIcons
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** The app's reading aloud, for any screen: read a post, a thread or an article, stop, ask what is being read. */
val LocalSpeech = staticCompositionLocalOf<AppSpeech?> { null }

/** What the platform gives reading aloud; made once per process. */
@Composable
internal expect fun rememberSpeechPlatform(): () -> SpeechPlatform

/** Reading aloud's parts for this app, made the first time a screen asks for them. */
@Composable
internal fun rememberSpeechParts(): SpeechParts {
    val graph = LocalGraph.current
    val platform = rememberSpeechPlatform()
    return remember(graph) { SpeechParts.of(graph, platform) }
}

/**
 * The app with a "reading aloud" bar under it while something is read
 * (speech_bar.dart): under the app rather than in the reader, so the reading
 * can be stopped from wherever the reader went. The bar takes its own room
 * and the system's bottom inset with it, so it covers nothing. When a reading
 * could not start, the bar says why, with XTA's way out.
 */
@Composable
fun SpeechBarScaffold(content: @Composable () -> Unit) {
    val speech = rememberSpeechParts().speech
    val playback by speech.playback.collectAsStateWithLifecycle()
    val notice by speech.notice.collectAsStateWithLifecycle()
    val shown = playback.speaking || notice != null
    CompositionLocalProvider(LocalSpeech provides speech) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().then(if (shown) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)) {
                content()
            }
            AnimatedVisibility(visible = shown, enter = expandVertically(), exit = shrinkVertically()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Box(Modifier.navigationBarsPadding()) {
                        val current = notice
                        if (current != null) NoticeRow(current, speech) else ReadingRow(playback, speech)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingRow(playback: SpeechPlayback, speech: AppSpeech) {
    Row(
        Modifier.fillMaxWidth().clickable { speech.stop() }.padding(start = 16.dp, top = 6.dp, end = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ReaderIcons.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.reader_speech_playing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            playback.title?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        IconButton(onClick = { speech.stop() }) { Icon(ReaderIcons.StopCircle, stringResource(Res.string.reader_speech_stop)) }
    }
}

/** Why nothing is read, for a few seconds, with what can be done about it. */
@Composable
private fun NoticeRow(notice: SpeechNotice, speech: AppSpeech) {
    val links = LocalLinks.current
    LaunchedEffect(notice) {
        delay(NOTICE_MS)
        speech.dismiss(notice)
    }
    val message = when (notice) {
        is SpeechNotice.Failed -> if (notice.action == TtsFailureAction.INSTALL_SHERPA) Res.string.reader_speech_sherpa_missing else Res.string.reader_speech_unavailable
        SpeechNotice.NoEngine -> Res.string.reader_speech_no_engine
        SpeechNotice.NothingToRead -> Res.string.reader_speech_no_text
    }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, end = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(ReaderIcons.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (notice is SpeechNotice.Failed) {
            when (notice.action) {
                TtsFailureAction.USE_SHERPA -> TextButton(onClick = { speech.useSherpa() }) { Text(stringResource(Res.string.reader_speech_use_sherpa)) }
                TtsFailureAction.INSTALL_SHERPA -> TextButton(onClick = {
                    speech.dismiss(notice)
                    links?.openOutside(Tts.SHERPA_INSTALL_URL)
                }) { Text(stringResource(Res.string.reader_speech_get_sherpa)) }
            }
        }
        IconButton(onClick = { speech.dismiss(notice) }) { Icon(Icons.Filled.Close, stringResource(Res.string.reader_dismiss)) }
    }
}

/** How long a notice stays: long enough to reach its action. */
private const val NOTICE_MS = 6_000L
