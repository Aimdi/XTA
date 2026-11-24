package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.services.article.Article
import com.aimdi.xtanative.services.speech.SpeechPlayback
import com.aimdi.xtanative.services.speech.SpeechReader
import com.aimdi.xtanative.services.speech.Tts
import com.aimdi.xtanative.services.speech.TtsChoice
import com.aimdi.xtanative.services.speech.TtsFailureAction
import com.aimdi.xtanative.store.settings.SettingsStore
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the speech bar says in place of a reading: why nothing is being read, and what can be done about it. */
sealed interface SpeechNotice {
    /** No engine would read: [action] is the way out XTA offers, picking Sherpa or getting it. */
    data class Failed(val action: TtsFailureAction) : SpeechNotice

    /** The platform has no speech engine at all, and no downloaded voice can read in this build. */
    data object NoEngine : SpeechNotice

    /** Only downloaded voices read here (the desktop), and none would read this: the voice settings are the way out. */
    data object NeedsVoice : SpeechNotice

    /** The text had nothing to read. */
    data object NothingToRead : SpeechNotice
}

/**
 * Reading aloud for the whole app (speech_store.dart, and the reader's
 * Listen button around it): one reading at a time, which outlives the screen
 * that started it until it ends or is stopped. The voice, engine and speed
 * are the reader's settings at the moment a reading starts. [hasEngine] is
 * false where the platform has none to read with; [voicesOnly] where only
 * downloaded voices read (the desktop), so no system engine is offered when
 * none of them would.
 *
 * A reading can be held and gone on with, and a sleep timer ([sleepIn])
 * holds it by itself: the timer is this object's, so it runs on when the
 * screen is left and the reading goes on in the background, and it is kept
 * nowhere else, so it ends with the reading and with the app. [clock] gives
 * milliseconds that only go forward and [wait] lets them pass.
 */
class AppSpeech(
    private val reader: SpeechReader,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    val hasEngine: Boolean,
    private val voicesOnly: Boolean = false,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    val playback: StateFlow<SpeechPlayback> = reader.state

    private val notices = MutableStateFlow<SpeechNotice?>(null)
    val notice: StateFlow<SpeechNotice?> = notices.asStateFlow()

    private val settingsAsks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Ticks when a notice's way out asks for the voice settings, which the app's navigation opens. */
    val voiceSettingsAsked: SharedFlow<Unit> = settingsAsks.asSharedFlow()

    /** Counts the readings asked for, so one that ends knows whether it is still the one at hand. */
    private val readings = AtomicInteger()

    private val sleepLeft = MutableStateFlow<Int?>(null)

    /** The minutes left on the sleep timer, as the bar's button shows them; null while none runs. */
    val sleepMinutes: StateFlow<Int?> = sleepLeft.asStateFlow()

    /** The sleep timer's wait; changed only under this object's lock, with [sleepLeft]. */
    private var sleeping: Job? = null

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
        sleepIn(null)
        notices.value = null
        when {
            !hasEngine -> notices.value = SpeechNotice.NoEngine
            text.isBlank() -> notices.value = SpeechNotice.NothingToRead
            else -> {
                last = title to text
                val mine = readings.incrementAndGet()
                scope.launch {
                    val choice = VoiceChoices.choiceOf(settings.load())
                    if (!reader.speak(title, text, choice)) notices.value = failure(choice)
                    // Over by itself, or stopped from the lock screen: nothing is left for a timer to hold.
                    if (mine == readings.get() && !playback.value.speaking) sleepIn(null)
                }
            }
        }
    }

    private fun failure(choice: TtsChoice): SpeechNotice = if (voicesOnly) SpeechNotice.NeedsVoice else SpeechNotice.Failed(Tts.failureAction(choice.engine))

    /** A notice's "Voice settings": [notice] goes, and the settings open. */
    fun openVoiceSettings(notice: SpeechNotice? = null) {
        dismiss(notice)
        settingsAsks.tryEmit(Unit)
    }

    fun stop() {
        sleepIn(null)
        scope.launch { reader.stop() }
    }

    /** Holds the reading where it is; [resume] goes on with it. */
    fun pause() {
        scope.launch { reader.pause() }
    }

    fun resume() = reader.resume()

    /**
     * The sleep timer: in [minutes] the reading is held, as [pause] holds it,
     * and the timer is gone. Null turns it off, and so do stopping the
     * reading, its end and another reading. Nothing while nothing is read.
     */
    @Synchronized
    fun sleepIn(minutes: Int?) {
        val timer = minutes?.takeIf { it > 0 && playback.value.speaking }?.let { SleepTimer.of(it, clock()) }
        sleeping?.cancel()
        sleepLeft.value = timer?.minutesLeft(clock())
        sleeping = timer?.let { scope.launch { keep(it) } }
    }

    /** Shows [timer]'s minutes as they pass, then holds the reading. */
    private suspend fun keep(timer: SleepTimer) {
        val mine = currentCoroutineContext()[Job]
        while (true) {
            val now = clock()
            if (timer.isDue(now)) break
            if (!shows(mine, timer.minutesLeft(now))) return
            wait(timer.nextChange(now))
        }
        if (shows(mine, null)) reader.pause()
    }

    /** Puts [left] on the button for the timer waited by [job], unless another has taken its place; no minutes left ends it. */
    @Synchronized
    private fun shows(job: Job?, left: Int?): Boolean {
        if (sleeping !== job) return false
        sleepLeft.value = left
        if (left == null) sleeping = null
        return true
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
