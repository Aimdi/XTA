package com.aimdi.xtanative.store.reading

import com.aimdi.xtanative.core.json.J
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** How long a reader has had the article in front of them: it runs only while the reader is open and in view. */
interface ActiveClock {
    val isRunning: Boolean
    val elapsedMs: Long

    fun start()

    fun stop()

    companion object {
        /** A clock on the system's monotonic time. */
        operator fun invoke(): ActiveClock = object : ActiveClock {
            private var startedAt: Long? = null
            private var before = 0L
            override val isRunning: Boolean get() = startedAt != null
            override val elapsedMs: Long get() = before + (startedAt?.let { (System.nanoTime() - it) / 1_000_000 } ?: 0)

            override fun start() {
                if (startedAt == null) startedAt = System.nanoTime()
            }

            override fun stop() {
                before = elapsedMs
                startedAt = null
            }
        }
    }
}

/** An article reader's type and place, and whether it opened where the reader had left it. */
data class ReadingState(
    val fontSize: Double = 18.0,
    val lineHeight: Double = 1.7,
    val point: ArticleReadPoint = ArticleReadPoint(),
    val resumed: Boolean = false,
)

/**
 * One article open in a reader: it opens where the reader left it, notes
 * the place as they read (a moment after they stop), and marks the article
 * read when they finish it: by asking, or by scrolling to the end themselves
 * after [DWELL_MS] of reading. Arriving at the end while the place is put
 * back is not reading. Told so ([open]'s `journalOnOpen`), opening leaves
 * no trace until the reader moves. The reader's screen uses it from its own
 * thread; its timers run in the scope it gives.
 */
class ArticleReading private constructor(
    private val journal: ReadingJournal,
    private val appearanceStore: ReadingAppearanceStore,
    val articleId: String,
    private val onCompleted: suspend () -> Unit,
    private val scope: CoroutineScope,
    private val remembers: () -> Boolean,
    /** False for previews: those are finished only by asking. */
    @Volatile var allowAutomaticCompletion: Boolean,
    private val clock: ActiveClock,
    private val now: () -> Long,
    initial: ReadingState,
    @Volatile private var journaled: Boolean,
) {
    private val current = MutableStateFlow(initial)
    val state: StateFlow<ReadingState> = current.asStateFlow()

    @Volatile private var closed = false
    @Volatile private var completing = false
    private var debounce: Job? = null
    private var endTimer: Job? = null
    private var appearanceWrite: Job? = null

    /** Whether places are kept at all, as the app-wide setting says now. */
    val remembersPosition: Boolean get() = remembers()

    /** The reader came into view, or left it: only time in view counts toward finishing, and leaving notes the place. */
    fun setActive(active: Boolean) {
        if (closed) return
        if (active) {
            clock.start()
        } else {
            clock.stop()
            endTimer?.cancel()
            scope.launch { flush() }
        }
    }

    /** A new text size or line height, within bounds, kept for every reader. */
    fun appearance(fontSize: Double? = null, lineHeight: Double? = null) {
        if (closed) return
        current.update { state ->
            val bounded = ReadingAppearance(fontSize ?: state.fontSize, lineHeight ?: state.lineHeight).bounded()
            state.copy(fontSize = bounded.fontSize, lineHeight = bounded.lineHeight)
        }
        appearanceWrite = scope.launch { current.value.let { appearanceStore.set(ReadingAppearance(it.fontSize, it.lineHeight)) } }
    }

    /**
     * A place as a web reader reports it, as JSON with `interacted`,
     * `userScrolled` and `atEnd`. Anything malformed or overlong is ignored:
     * web content cannot touch the kept place.
     */
    fun receiveProgress(message: String) {
        if (closed || message.length > MAX_MESSAGE) return
        val raw = J.parse(message)
        if (raw.raw !is JsonObject) return
        receivePoint(ArticleReadPoint.parse(raw), raw["interacted"].boolean == true, raw["userScrolled"].boolean == true, raw["atEnd"].boolean == true)
    }

    /**
     * Where a reader now stands. [interacted] ends the "resumed" note and
     * joins the article to the journal; a scroll by the reader to the end may
     * finish the article.
     */
    fun receivePoint(point: ArticleReadPoint, interacted: Boolean = false, userScrolled: Boolean = false, atEnd: Boolean = false) {
        if (closed) return
        if (interacted) journaled = true
        current.update { state ->
            state.copy(point = point.copy(completed = state.point.completed, updatedAt = now()), resumed = state.resumed && !interacted)
        }
        schedule()
        checkCompletion(userScrolled, atEnd)
    }

    /** Marks the article read through [onCompleted]; when that fails it stays unread, to be tried again. */
    suspend fun complete() {
        if (closed || current.value.point.completed || completing) return
        completing = true
        try {
            onCompleted()
            if (closed) return
            journaled = true
            current.update { it.copy(point = it.point.copy(completed = true, updatedAt = now()), resumed = false) }
            flush()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Leave the finish action there when the read could not be noted.
        } finally {
            completing = false
        }
    }

    /** Back to the top; a finished article stays finished. */
    fun startOver() {
        if (closed) return
        current.update { ReadingState(it.fontSize, it.lineHeight, ArticleReadPoint(completed = it.point.completed)) }
        schedule()
    }

    /** Writes the place now, unless places are not kept or the article has not joined the journal. */
    suspend fun flush() {
        debounce?.cancel()
        write()
    }

    private suspend fun write() {
        if (!journaled || !remembers()) return
        journal.put(articleId, current.value.point)
    }

    /** The reader is gone: the place is written one last time, and late reports are ignored. */
    suspend fun close() {
        closed = true
        endTimer?.cancel()
        clock.stop()
        flush()
        appearanceWrite?.join()
    }

    private fun schedule() {
        debounce?.cancel()
        debounce = scope.launch {
            delay(DEBOUNCE_MS)
            write()
        }
    }

    private fun checkCompletion(userScrolled: Boolean, atEnd: Boolean) {
        endTimer?.cancel()
        if (!allowAutomaticCompletion || !userScrolled || !atEnd || !clock.isRunning) return
        val remaining = DWELL_MS - clock.elapsedMs
        endTimer = scope.launch {
            if (remaining > 0) delay(remaining)
            // Its own job: a later scroll cancels a waiting timer, never a finish under way.
            if (!closed && clock.isRunning && allowAutomaticCompletion) scope.launch { complete() }
        }
    }

    companion object {
        /** How long a reader reads before scrolling to the end finishes an article. */
        const val DWELL_MS = 12_000L
        const val DEBOUNCE_MS = 400L
        private const val MAX_MESSAGE = 1500

        /**
         * Opens [articleId]: with the type every reader shares and, while
         * [remembers] says places are kept, the place it was left at. With
         * [journalOnOpen] false the article joins the journal only once the
         * reader moves through it or finishes it, so opening leaves no trace.
         */
        suspend fun open(
            journal: ReadingJournal,
            appearance: ReadingAppearanceStore,
            articleId: String,
            scope: CoroutineScope,
            onCompleted: suspend () -> Unit,
            remembers: () -> Boolean = { true },
            journalOnOpen: Boolean = true,
            alreadyCompleted: Boolean = false,
            allowAutomaticCompletion: Boolean = true,
            clock: ActiveClock = ActiveClock(),
            now: () -> Long = System::currentTimeMillis,
        ): ArticleReading {
            val type = appearance.get()
            val saved = if (remembers()) journal.point(articleId) else null
            val point = saved ?: ArticleReadPoint()
            val initial = ReadingState(
                fontSize = type.fontSize,
                lineHeight = type.lineHeight,
                point = point.copy(completed = point.completed || alreadyCompleted, updatedAt = now()),
                resumed = point.fraction > 0.01 && !point.completed,
            )
            val reading = ArticleReading(
                journal, appearance, articleId, onCompleted, scope, remembers, allowAutomaticCompletion, clock, now, initial,
                journaled = journalOnOpen || saved != null,
            )
            return reading.also { it.schedule() }
        }
    }
}

/** One laid-out block of a text: its place in the text, and its top and height on screen. */
data class BlockExtent(val index: Int, val top: Double, val height: Double)

/** The first block still showing below the top of the screen, and how far above (negative) or below the top its own top sits. */
data class VisibleBlock(val index: Int, val leading: Double)

/** The block a native reader is at: the first, in text order, whose bottom is still below the top. Null when none shows. */
fun firstVisibleBlock(blocks: List<BlockExtent>): VisibleBlock? =
    blocks.filter { it.top + it.height > 0 }.minByOrNull { it.index }?.let { VisibleBlock(it.index, it.top) }
