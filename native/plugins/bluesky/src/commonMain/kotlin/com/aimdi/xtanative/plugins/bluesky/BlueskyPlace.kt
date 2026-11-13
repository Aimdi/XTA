package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskyReadPoint
import com.aimdi.xtanative.networks.bluesky.BlueskyReadingPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Where the reader left Following, as XTA's BlueskyReaderStore keeps it: in
 * `plugin.bluesky.reader.v1`, with up to 144 posts around it, for the AppView
 * read, while Remember reading position is on. Its posts paint Following
 * the first time it reads this session, and the list opens there the first
 * time it is drawn; changes are written a moment after they settle.
 */
internal class BlueskyPlace(private val prefs: BlueskyPreferences, private val scope: CoroutineScope) {
    private var loadedFor: String? = null
    private var kept: BlueskyReadPoint? = null
    private var restored = false
    private var positioned = false
    private var writing: Job? = null

    /** The place kept for the AppView read now; nothing until the settings have been read, or while places are not kept. */
    @Synchronized
    private fun current(): BlueskyReadPoint? {
        if (!prefs.loaded) return null
        val source = prefs.appView.value
        if (loadedFor != source) {
            loadedFor = source
            kept = prefs.place(source)
            restored = false
            positioned = false
        }
        return kept?.takeIf { prefs.keepPlace.value }
    }

    /** The place's posts by accounts still followed ([follows]), the first time Following reads this session. */
    @Synchronized
    fun takePosts(follows: (String) -> Boolean): List<BlueskyPost> {
        val point = current()?.takeIf { !restored } ?: return emptyList()
        restored = true
        return BlueskyReadingPrefs.kept(point.posts, follows)
    }

    /** Where Following's list opens, the first time it is drawn this session. */
    @Synchronized
    fun takePosition(): BlueskyReadPoint? {
        val point = current()?.takeIf { !positioned } ?: return null
        positioned = true
        return point
    }

    fun remember(point: BlueskyReadPoint) {
        if (!prefs.keepPlace.value || point.posts.isEmpty()) return
        val source = prefs.appView.value
        writing?.cancel()
        writing = scope.launch {
            delay(WRITE_AFTER_MS)
            prefs.place(source, point)
        }
    }

    private companion object {
        const val WRITE_AFTER_MS = 350L
    }
}

/** Following's list against a [BlueskyReadPoint]: where it opens, and the place it shows. */
internal object FollowingPlaces {
    /** A post row's key: its URI. */
    private const val POST_KEY = "at://"

    /** How many posts Home draws before the people its feed brings (bluesky_reader_view.dart's headingAfter). */
    const val STRIP_AFTER = 3

    /**
     * The row and offset the list opens at for [point], once its anchor is
     * among [shown] and the first of them is laid out ([visible], each row's
     * key and index); null until then. [stripAfter] posts are followed by a
     * row that is no post (the people strip), which the posts under it sit
     * one row further for.
     */
    fun start(shown: List<BlueskyPost>, point: BlueskyReadPoint, visible: List<Pair<Any, Int>>, stripAfter: Int? = null): Pair<Int, Int>? {
        val at = shown.indexOfFirst { it.uri == point.anchor }
        if (at < 0) return null
        val first = visible.firstOrNull { (key, _) -> key == shown.first().uri }?.second ?: return null
        val strip = if (stripAfter != null && at >= stripAfter) 1 else 0
        return first + at + strip to (-point.leading).toInt().coerceAtLeast(0)
    }

    /** The place the list shows: the first post row on screen ([visible], each row's key and where it starts) and the posts kept around it of all Following holds. */
    fun place(visible: List<Pair<Any, Int>>, posts: List<BlueskyPost>): BlueskyReadPoint? {
        val (key, offset) = visible.firstOrNull { (key, _) -> key is String && key.startsWith(POST_KEY) } ?: return null
        val anchor = key as String
        val kept = BlueskyReadingPrefs.window(posts, anchor)
        return if (kept.isEmpty()) null else BlueskyReadPoint(kept, anchor, offset.toDouble())
    }
}
