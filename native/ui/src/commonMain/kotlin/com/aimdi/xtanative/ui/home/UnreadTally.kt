package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.session.DeckColumn
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.ui.group.XDiscovery
import com.aimdi.xtanative.ui.group.ReadMarks
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The feeds with unread posts ([lit], by [HomeTab.key]) and, for those whose
 * posts are kept, how many ([counts]); For you keeps none, so it has a dot alone.
 */
data class UnreadNow(val lit: Set<String> = emptySet(), val counts: Map<String, Int> = emptyMap()) {
    /** Unread posts in all, what the window's title, the tray and the launcher say: a feed with no count counts one. */
    val total: Int get() = lit.sumOf { counts[it] ?: 1 }

    /** How many posts [key]'s feed has unread; null while it has none or keeps no count. */
    fun count(key: String): Int? = counts[key]?.takeIf { key in lit && it > 0 }
}

/**
 * The unread marks of the home feeds, read from their read marks
 * ([ReadMarks]) and kept posts, for the whole app: Home's dots and counts,
 * and the window's title, the tray and the launcher on the desktop. Read
 * again a moment after it is asked ([reload]) or a read mark changes, and
 * as the session or Remember reading position does.
 */
@OptIn(FlowPreview::class)
class UnreadTally(private val graph: AppGraph, private val scope: CoroutineScope, private val clock: () -> Long = System::currentTimeMillis) {
    private val state = MutableStateFlow(UnreadNow())
    val now: StateFlow<UnreadNow> = state.asStateFlow()
    private val asked = MutableStateFlow(0L)
    private val following = AtomicBoolean(false)

    /** Reads the marks again, in a moment: timelines turned in quick succession read them once (XTA's 200 ms). */
    fun reload() {
        if (following.compareAndSet(false, true)) scope.launch { follow() }
        asked.update { it + 1 }
    }

    private suspend fun follow() {
        val reading = graph.settings.settings.map { it[PostsPrefs.rememberReadingPosition] }
        merge(asked, ReadMarks.changes, graph.sessions.state, reading)
            .debounce(AFTER_MILLIS)
            .collectLatest { state.value = read() }
    }

    /** The marks as they are now. */
    suspend fun read(): UnreadNow {
        val session = graph.sessions.state.value ?: return UnreadNow()
        val reading = graph.settings.load()[PostsPrefs.rememberReadingPosition]
        val counted = HomeUnread.feeds(session).mapNotNull { tab ->
            val key = HomeUnread.feedKey(tab) ?: return@mapNotNull null
            val tracks = HomeUnread.tracks(session, tab, reading)
            val marks = marks(key)
            if (!marks.unread(tracks)) return@mapNotNull null
            tab.key to kept(tab)?.let { marks.unreadCount(tracks, it) }
        }
        return UnreadNow(counted.mapTo(HashSet()) { it.first }, counted.mapNotNull { (key, count) -> count?.let { key to it } }.toMap())
    }

    /** Everything [tab]'s feed read counts as read. */
    fun markRead(tab: HomeTab) {
        val key = HomeUnread.feedKey(tab) ?: return
        scope.launch {
            marks(key).markNewestRead(clock())
            reload()
        }
    }

    /** Every home feed's posts count as read: Following's, For you's and each group's that has unread ones. */
    fun markAllRead() {
        scope.launch {
            val session = graph.sessions.state.value ?: return@launch
            val lit = read().lit
            unreadFeeds(session, lit).forEach { key -> marks(key).markNewestRead(clock()) }
            reload()
        }
    }

    private fun marks(key: String) = ReadMarks(graph.feedCache(ReadMarks.key(key)))

    /** The posts [tab]'s feed keeps, to count; null for For you, which keeps none. */
    private suspend fun kept(tab: HomeTab): List<XThread>? = when (tab) {
        HomeTab.Following -> XDiscovery.feedThreads(graph.feedCache(null).read())
        is HomeTab.Group -> XDiscovery.feedThreads(graph.feedCache(tab.id).read())
        HomeTab.ForYou, is HomeTab.Network -> null
    }

    companion object {
        const val AFTER_MILLIS = 200L

        /** The home timeline [column] reads, whose read marks it shares; null for a network's. */
        fun tabOf(column: DeckColumn): HomeTab? = when (column) {
            DeckColumn.Following -> HomeTab.Following
            DeckColumn.ForYou -> HomeTab.ForYou
            is DeckColumn.Group -> HomeTab.Group(column.groupId, "")
            is DeckColumn.Network -> null
        }

        /** The read-mark keys of [session]'s home feeds that are [lit]. */
        fun unreadFeeds(session: Session, lit: Set<String>): List<String> =
            HomeUnread.feeds(session).filter { it.key in lit }.mapNotNull(HomeUnread::feedKey)
    }
}
