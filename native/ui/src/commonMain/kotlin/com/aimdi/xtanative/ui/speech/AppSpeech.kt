package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.services.article.Article
import com.aimdi.xtanative.services.speech.SpeechPlayback
import com.aimdi.xtanative.services.speech.SpeechReader
import com.aimdi.xtanative.services.speech.Tts
import com.aimdi.xtanative.services.speech.TtsFailureAction
import com.aimdi.xtanative.store.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the speech bar says in place of a reading: why nothing is being read, and what can be done about it. */
sealed interface SpeechNotice {
    /** No engine would read: [action] is the way out XTA offers, picking Sherpa or getting it. */
    data class Failed(val action: TtsFailureAction) : SpeechNotice

    /** The platform has no speech engine at all: the desktop. */
    data object NoEngine : SpeechNotice

    /** The text had nothing to read. */
    data object NothingToRead : SpeechNotice
}

/**
 * Reading aloud for the whole app (speech_store.dart, and the reader's
 * Listen button around it): one reading at a time, which outlives the screen
 * that started it until it ends or is stopped. The voice, engine and speed
 * are the reader's settings at the moment a reading starts. [hasEngine] is
 * false where the platform has none to read with.
 */
class AppSpeech(
    private val reader: SpeechReader,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    val hasEngine: Boolean,
) {
    val playback: StateFlow<SpeechPlayback> = reader.state

    private val notices = MutableStateFlow<SpeechNotice?>(null)
    val notice: StateFlow<SpeechNotice?> = notices.asStateFlow()

    /** The last reading asked for, which "Use Sherpa" starts again. */
    @Volatile
    private var last: Pair<String, String>? = null

    /** Whether [title] is what is being read now, rather than something left playing from elsewhere. */
    fun isReading(title: String): Boolean = playback.value.let { it.speaking && it.title == title }

    /** The Listen button: stops [title] when that is being read, else reads [text] aloud as [title]. */
    fun toggle(title: String, text: String) {
        if (isReading(title)) stop() else speak(title, text)
    }

    /** Reads [text] aloud as [title], in place of whatever was being read; a reading no engine takes leaves a notice. */
    fun speak(title: String, text: String) {
        notices.value = null
        when {
            !hasEngine -> notices.value = SpeechNotice.NoEngine
            text.isBlank() -> notices.value = SpeechNotice.NothingToRead
            else -> {
                last = title to text
                scope.launch {
                    val choice = VoiceChoices.choiceOf(settings.load())
                    if (!reader.speak(title, text, choice)) notices.value = SpeechNotice.Failed(Tts.failureAction(choice.engine))
                }
            }
        }
    }

    fun stop() {
        scope.launch { reader.stop() }
    }

    /** Takes [notice] off the bar, unless another has replaced it since. */
    fun dismiss(notice: SpeechNotice? = null) {
        notices.update { current -> if (notice == null || current == notice) null else current }
    }

    /** "Use Sherpa": Sherpa as the engine, then the reading that failed, again. */
    fun useSherpa() {
        notices.value = null
        scope.launch {
            settings.update(VoiceChoices::preferSherpa)
            last?.let { (title, text) -> speak(title, text) }
        }
    }

    /** Reads [post] aloud under its author's name, or stops it when that is what is being read. */
    fun togglePost(post: XPost) = toggle(SpeechTexts.title(post), SpeechTexts.post(post))

    /** Reads a thread's [posts] aloud, one after the other, under the first author's name. */
    fun toggleThread(posts: List<XPost>) {
        val first = posts.firstOrNull() ?: return
        toggle(SpeechTexts.title(first), SpeechTexts.thread(posts))
    }

    fun toggleArticle(article: Article) = toggle(article.title, SpeechTexts.article(article))
}
