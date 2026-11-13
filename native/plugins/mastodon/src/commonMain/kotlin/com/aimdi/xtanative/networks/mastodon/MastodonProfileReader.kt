package com.aimdi.xtanative.networks.mastodon

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One profile and its Posts, Posts & replies and Media, as XTA's
 * MastodonProfileStore reads them: the profile and its first page of posts
 * (pinned ones first) from the first of [instances] that knows [acct], and
 * each tab's pages from that same instance, a tab read the first time it is
 * shown.
 */
class MastodonProfileReader(
    private val client: MastodonClient,
    private val instances: () -> List<String>,
    val acct: String,
    private val scope: CoroutineScope,
) {
    /** One tab's posts so far, and whether more can be read. */
    data class Timeline(
        val posts: List<MastodonPost> = emptyList(),
        val cursor: String? = null,
        val loaded: Boolean = false,
        val loading: Boolean = false,
        val more: Boolean = true,
        val error: Throwable? = null,
    )

    data class State(
        val profile: MastodonProfile? = null,
        val instance: String? = null,
        val timelines: Map<MastodonProfileTab, Timeline> = emptyMap(),
        val pinnedIds: Set<String> = emptySet(),
        val selected: MastodonProfileTab = MastodonProfileTab.POSTS,
        val loading: Boolean = false,
        val profileError: Throwable? = null,
    ) {
        fun timeline(tab: MastodonProfileTab): Timeline = timelines[tab] ?: Timeline()

        val current: Timeline get() = timeline(selected)

        val error: Throwable? get() = profileError ?: current.error
    }

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()
    private val generation = AtomicInteger()

    /** The profile and its first page of posts read again; the tab shown reads its first page after them. */
    fun refresh() {
        val started = generation.incrementAndGet()
        mutable.update { now -> now.copy(loading = true, profileError = null, timelines = now.timelines.mapValues { it.value.copy(loading = false) }) }
        scope.launch {
            try {
                val page = client.profileAnywhere(instances(), acct)
                if (generation.get() != started) return@launch
                val posts = Timeline(page.posts, page.next, loaded = true, more = page.next != null)
                mutable.update {
                    it.copy(profile = page.profile, instance = page.instance, pinnedIds = page.pinnedIds, loading = false, timelines = mapOf(MastodonProfileTab.POSTS to posts))
                }
                if (state.value.selected != MastodonProfileTab.POSTS) loadMore()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation.get() == started) mutable.update { it.copy(loading = false, profileError = e) }
            }
        }
    }

    /** [tab] shown; read the first time. */
    fun select(tab: MastodonProfileTab) {
        mutable.update { it.copy(selected = tab) }
        val now = state.value
        if (!now.current.loaded && !now.loading) loadMore()
    }

    /** What failed read again: the profile, or the page of the tab shown. */
    fun retry() = if (state.value.profileError == null) loadMore() else refresh()

    /** The next page of the tab shown. */
    fun loadMore() {
        val now = state.value
        val profile = now.profile ?: return
        val instance = now.instance ?: return
        val tab = now.selected
        val current = now.current
        if (now.loading || current.loading || !current.more) return
        val started = generation.get()
        put(tab, current.copy(loading = true, error = null))
        scope.launch {
            try {
                val page = client.profileTab(instance, profile.id, tab, current.cursor)
                if (generation.get() != started) return@launch
                val more = page.next != null && page.next != current.cursor
                put(tab, Timeline(MastodonFeeds.unique(current.posts, page.items), page.next, loaded = true, more = more))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation.get() == started) put(tab, current.copy(loading = false, error = e))
            }
        }
    }

    private fun put(tab: MastodonProfileTab, timeline: Timeline) = mutable.update { it.copy(timelines = it.timelines + (tab to timeline)) }
}
