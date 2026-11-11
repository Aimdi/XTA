package com.aimdi.xtanative.plugins.rss

import com.aimdi.xtanative.networks.RssItem
import com.aimdi.xtanative.networks.rss.RssClient
import com.aimdi.xtanative.networks.rss.RssFeed
import com.aimdi.xtanative.networks.rss.RssTimelineCopies
import com.aimdi.xtanative.networks.rss.RssTimelineCopy
import com.aimdi.xtanative.networks.rss.RssTimelineRules
import com.aimdi.xtanative.store.StoreFile
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
 * is counted, not thrown. The last read is [kept] on the device, so a start
 * shows it at once while the feeds are read again.
 */
internal class RssTimelineModel(
    private val client: RssClient,
    private val scope: CoroutineScope,
    private val kept: StoreFile? = null,
    private val feeds: () -> List<RssFeed>,
) {
    private val mutable = MutableStateFlow(RssTimelineState())
    val state: StateFlow<RssTimelineState> = mutable.asStateFlow()
    private var job: Job? = null
    private var asked: Set<String>? = null

    fun refresh(force: Boolean = false) {
        val followed = feeds()
        if (followed.isEmpty()) {
            job?.cancel()
            mutable.value = RssTimelineState(loaded = true, fetchedAt = state.value.fetchedAt ?: Instant.now())
            return
        }
        val ids = followed.map { it.id }.toSet()
        if (!force && settled(ids)) return
        if (!force && asked == ids && job?.isActive == true) return
        job?.cancel()
        asked = ids
        job = scope.launch {
            restore(ids)
            if (force || !settled(ids)) read(followed)
        }
    }

    private fun settled(ids: Set<String>): Boolean =
        state.value.let { it.loaded && it.readFrom == ids && RssTimelineRules.fresh(it.fetchedAt, Instant.now()) }

    /** The copy kept on the device, before anything is shown: the items of the feeds still followed. */
    private suspend fun restore(ids: Set<String>) {
        if (state.value.loaded) return
        val copy = kept?.let { file -> attempt { RssTimelineCopies.decode(file.read()) } } ?: return
        val items = copy.items.filter { it.feedId in ids }
        if (items.isEmpty() || state.value.loaded) return
        mutable.value = RssTimelineState(items = items, loaded = true, fetchedAt = copy.fetchedAt, readFrom = copy.readFrom)
    }

    private suspend fun read(followed: List<RssFeed>) {
        mutable.update { it.copy(loading = true, error = null) }
        try {
            val timeline = client.timeline(followed)
            val read = RssTimelineState(
                items = timeline.items,
                failed = timeline.failed,
                loaded = true,
                fetchedAt = Instant.now(),
                readFrom = followed.map { it.id }.toSet(),
            )
            mutable.value = read
            kept?.let { file -> attempt { file.write(RssTimelineCopies.encode(RssTimelineCopy(read.items, read.fetchedAt!!, read.readFrom))) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutable.update { it.copy(loading = false, loaded = true, error = e.takeIf { _ -> it.items.isEmpty() }) }
        }
    }

    private suspend fun <T> attempt(work: suspend () -> T): T? = try {
        work()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
