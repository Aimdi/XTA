package com.aimdi.xtanative.ui.media.mpris

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.aimdi.xtanative.store.settings.DesktopPrefs
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_speech_playing
import com.aimdi.xtanative.ui.generated.resources.reader_video
import org.jetbrains.compose.resources.stringResource

/** While [DesktopPrefs.mediaDetails] is off, the desktop's media controls are told only that a video or a reading plays, by [appName]. */
@Composable
fun MediaControlsPlainWords(settings: SettingsStore, appName: String) {
    val details by remember(settings) { settings.observe(DesktopPrefs.mediaDetails) }
        .collectAsState(initial = settings.state.value?.get(DesktopPrefs.mediaDetails) ?: true)
    val plain = MprisPlain(stringResource(Res.string.reader_video), stringResource(Res.string.reader_speech_playing), appName)
    LaunchedEffect(details, plain) { MprisSessions.service.plain = plain.takeUnless { details } }
}
