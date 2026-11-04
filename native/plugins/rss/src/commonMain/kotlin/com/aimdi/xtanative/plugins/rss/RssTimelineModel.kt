package com.aimdi.xtanative.plugins.rss

import com.aimdi.xtanative.networks.RssItem
import com.aimdi.xtanative.networks.rss.RssClient
import com.aimdi.xtanative.networks.rss.RssFeed
import com.aimdi.xtanative.networks.rss.RssTimelineRules
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Every followed feed's items, newest first, and how many feeds could not be read on the last refresh. */
internal data class RssTimelineState(
    val items: List<RssItem> = emptyList(),
    val failed: Int = 0,
    val loading: Boolean = false,
    /** A first read has ended, whatever it brought. */
    val loaded: Boolean = false,
    val error: Throwable? = null,
    val fetchedAt: Instant? = null,
    /** The feeds the items were read from, by id: other follows make them stale. */
    val readFrom: Set<String> = emptySet(),
)

/**
 * The RSS tab's timeline, as XTA's RssTimelineStore keeps it while the
 * plugin runs: read again on a refresh asked for, when the feeds followed
 * changed, or when it is older than [RssTimelineRules.FRESH_FOR]; a refresh
 * over shown items keeps them when it fails. One feed that cannot be read
 * is counted, not thrown.
 */
internal class RssTimelineModel(private val client: RssClient, private val scope: CoroutineScope, private val feeds: () -> List<RssFeed>) {
    private val mutable = MutableStateFlow(RssTimelineState())
    val state: StateFlow<RssTimelineState> = mutable.asStateFlow()
    private var job: Job? = null

    fun refresh(force: Boolean = false) {
        val followed = feeds()
        val now = state.value
        if (followed.isEmpty()) {
            job?.cancel()
            mutable.value = RssTimelineState(loaded = true, fetchedAt = now.fetchedAt ?: Instant.now())
            return
        }
        val same = now.readFrom == followed.map { it.id }.toSet()
        if (!force && same && now.loaded && RssTimelineRules.fresh(now.fetchedAt, Instant.now())) return
        if (!force && same && job?.isActive == true) return
        job?.cancel()
        job = scope.launch { read(followed) }
    }

    private suspend fun read(followed: List<RssFeed>) {
        mutable.update { it.copy(loading = true, error = null) }
        try {
            val timeline = client.timeline(followed)
            mutable.value = RssTimelineState(
                items = timeline.items,
                failed = timeline.failed,
                loaded = true,
                fetchedAt = Instant.now(),
                readFrom = followed.map { it.id }.toSet(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutable.update { it.copy(loading = false, loaded = true, error = e.takeIf { _ -> it.items.isEmpty() }) }
        }
    }
}
