package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivSeriesContext
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One work's detail as its screen shows it: the copy its list had at once, Pixiv's full record once it arrives. */
internal data class PixivWorkState(
    val illust: PixivIllust?,
    /** The full record arrived. */
    val loaded: Boolean = false,
    /** Why it did not; the list's copy stays shown. */
    val error: Throwable? = null,
    /** The page on screen. */
    val page: Int = 0,
    val series: PixivSeriesContext? = null,
    /** The creator's other works, at most [PixivWorkModel.AUTHOR_WORKS]. */
    val authorWorks: List<PixivIllust> = emptyList(),
)

/**
 * A work opened: its full record (a failed fetch keeps the list's copy),
 * the works Pixiv relates to it, the creator's other works and its place in
 * its series, each read on its own. Once the full record arrives the work
 * joins the viewing history, unless the reader paused it.
 */
internal class PixivWorkModel(private val runtime: PixivRuntime, private val scope: CoroutineScope, val id: Long, seed: PixivIllust?) {
    private val mutable = MutableStateFlow(PixivWorkState(seed))
    val state: StateFlow<PixivWorkState> = mutable.asStateFlow()

    val related = PixivList(scope, { it.id }, ::relatedPage, visible = { runtime.mutes.now.filter(it) })

    private var job: Job? = null
    private var asides: Job? = null

    init {
        load()
        seed?.let(::readAsides)
    }

    /** Reads the work again with the works below it, as a pull does. */
    fun load() {
        job?.cancel()
        mutable.update { it.copy(error = null) }
        job = scope.launch {
            try {
                val illust = runtime.client.illustDetail(id)
                val first = !mutable.value.loaded
                mutable.update { it.copy(illust = illust, loaded = true) }
                if (first) {
                    remember(illust)
                    if (asides == null || mutable.value.authorWorks.isEmpty()) readAsides(illust)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = e) }
            }
        }
        related.refresh()
    }

    fun showPage(page: Int) = mutable.update { it.copy(page = page) }

    private suspend fun relatedPage(next: String?): Page<PixivIllust> =
        mutable.value.illust?.let { runtime.client.related(it, next) } ?: runtime.client.related(id, next)

    /** The creator's other works and the work's place in its series, which need only whose it is. */
    private fun readAsides(illust: PixivIllust) {
        asides?.cancel()
        asides = scope.launch {
            launch { readAuthorWorks(illust) }
            if (illust.series != null) launch { readSeries() }
        }
    }

    private suspend fun readAuthorWorks(illust: PixivIllust) {
        val works = runCatching { runtime.client.userIllusts(illust.userId).items }.getOrDefault(emptyList())
        val shown = runtime.mutes.now.filter(works).filter { it.id != illust.id }.take(AUTHOR_WORKS)
        mutable.update { it.copy(authorWorks = shown) }
    }

    private suspend fun readSeries() {
        val place = runCatching { runtime.discovery.seriesContext(id) }.getOrNull()
        mutable.update { it.copy(series = place) }
    }

    private suspend fun remember(illust: PixivIllust) {
        if (runtime.historyPaused) return
        runCatching { runtime.history.record(PixivVisit(PixivKept.of(illust), Instant.now())) }
    }

    companion object {
        const val AUTHOR_WORKS = 12
    }
}
