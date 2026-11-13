package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Where the reader stopped in the Home section, kept in the plugin's own
 * folder (no post, only its id), so the section opens there again after
 * For you, Liked or a restart. Written a moment after the last change, not
 * at every scrolled row.
 */
internal class ThreadsHomePlace(private val file: StoreFile, scope: CoroutineScope) {
    @Volatile
    var place = ThreadsPlace()
        private set

    private val restoring = Mutex()
    private var restored = false
    private val changed = MutableStateFlow(0)

    init {
        scope.launch {
            changed.collectLatest { change ->
                if (change == 0) return@collectLatest
                delay(KEEP_AFTER_MILLIS)
                file.write(encode(place))
            }
        }
    }

    /** Reads the place kept last time, once. */
    suspend fun restore() = restoring.withLock {
        if (restored) return@withLock
        place = decode(file.read())
        restored = true
    }

    fun keep(now: ThreadsPlace) {
        if (!restored || now == place) return
        place = now
        changed.update { it + 1 }
    }

    companion object {
        private const val VERSION = 1
        private const val KEEP_AFTER_MILLIS = 1_500L

        fun encode(place: ThreadsPlace): String = JsonObject(mapOf("v" to JsonPrimitive(VERSION)) + place.fields()).toString()

        /** The place [text] keeps; the top when it keeps none it can read. */
        fun decode(text: String?): ThreadsPlace {
            val root = J.parse(text.orEmpty())
            return if (root["v"].long == VERSION.toLong()) ThreadsPlace.of(root) else ThreadsPlace()
        }
    }
}

/**
 * The posts a read brought in above where the reader is, for the "N new
 * posts" pill over Home: a list keeps its first post in view when posts
 * arrive above it, so without the pill they would wait there unseen.
 */
internal object ThreadsFresh {
    /** The ids [now] holds that [before] did not; none when nothing was shown before, since a first read is no news. */
    fun arrived(before: List<String>?, now: List<String>): Set<String> =
        if (before.isNullOrEmpty()) emptySet() else now.toSet() - before.toSet()

    /** How many of [fresh] stand above the [firstShown]th of [shown]. */
    fun above(shown: List<String>, fresh: Set<String>, firstShown: Int): Int =
        if (fresh.isEmpty()) 0 else shown.take(firstShown.coerceAtLeast(0)).count { it in fresh }

    /**
     * Where [place]'s post is among Home's rows: its first [peopleAfter]
     * posts, the people strip when [people], then the rest. Null at the top,
     * or when that post is gone.
     */
    fun indexOf(shown: List<String>, place: ThreadsPlace, people: Boolean, peopleAfter: Int): Int? {
        val at = place.postId?.let(shown::indexOf)?.takeIf { it >= 0 } ?: return null
        return if (people && at >= peopleAfter) at + 1 else at
    }
}
