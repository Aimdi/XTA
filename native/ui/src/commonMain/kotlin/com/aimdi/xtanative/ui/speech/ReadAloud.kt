package com.aimdi.xtanative.ui.speech

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_speech_listen
import com.aimdi.xtanative.ui.generated.resources.reader_speech_stop_listening
import com.aimdi.xtanative.ui.media.ReaderIcons
import org.jetbrains.compose.resources.stringResource

/**
 * A reader's Listen button (substack_reader_screen.dart): reads [text] aloud
 * as [title], or stops when that is what is being read. [text] is asked only
 * when the button is pressed, so a long article is not gathered for nothing.
 */
@Composable
fun ReadAloudButton(title: String, text: () -> String, modifier: Modifier = Modifier) {
    val speech = LocalSpeech.current ?: return
    val playback by speech.playback.collectAsStateWithLifecycle()
    val reading = playback.speaking && playback.title == title
    IconButton(onClick = { speech.toggle(title, text()) }, modifier = modifier) {
        Icon(
            if (reading) ReaderIcons.StopCircle else ReaderIcons.RecordVoiceOverOutlined,
            contentDescription = stringResource(if (reading) Res.string.reader_speech_stop_listening else Res.string.reader_speech_listen),
        )
    }
}
