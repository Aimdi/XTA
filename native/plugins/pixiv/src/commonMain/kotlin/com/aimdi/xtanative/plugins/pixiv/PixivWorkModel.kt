package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.core.x.http.withRequestBudget
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.pixiv.PixivPages
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivSeriesContext
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    /** Why the work's place in its series did not arrive; the series link stays. */
    val seriesError: Throwable? = null,
    /** The creator's other works, at most [PixivWorkModel.AUTHOR_WORKS]. */
    val authorWorks: List<PixivIllust> = emptyList(),
    /** Why the creator's other works did not arrive: a rate limit is not "no other works". */
    val authorWorksError: Throwable? = null,
)

/**
 * A work opened: its full record (a failed fetch keeps the list's copy),
 * the works Pixiv relates to it, the creator's other works and its place in
 * its series, each read on its own. Once the full record arrives the work
 * joins the viewing history, unless the reader paused it. Nothing is read
 * before [start]; a model made with [autoStart] starts at once, a pager's
 * once the reader stops on its work.
 */
internal class PixivWorkModel(
    private val runtime: PixivRuntime,
    private val scope: CoroutineScope,
    val id: Long,
    private val seed: PixivIllust?,
    autoStart: Boolean = true,
) {
    private val mutable = MutableStateFlow(PixivWorkState(seed))
    val state: StateFlow<PixivWorkState> = mutable.asStateFlow()

    val related = PixivList(scope, { it.id }, ::relatedPage, visible = { runtime.mutes.now.filter(it) })

    private var job: Job? = null
    private var asides: Job? = null

    private var started = false

    init {
        if (autoStart) start()
    }

    /** Reads the work and what goes with it, the first time only. */
    fun start() {
        if (started) return
        started = true
        load()
        seed?.let(::readAsides)
    }

    /** Reads the work again with the works below it, as a pull does. */
    fun load() {
        job?.cancel()
        mutable.update { it.copy(error = null) }
        job = scope.launch {
            try {
                val illust = withinBudget { runtime.client.illustDetail(id) }
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

    /** The creator's other works again, after they failed. */
    fun retryAuthorWorks() {
        val illust = mutable.value.illust ?: return
        mutable.update { it.copy(authorWorksError = null) }
        scope.launch { readAuthorWorks(illust) }
    }

    /** The work's place in its series again, after it failed. */
    fun retrySeries() {
        mutable.update { it.copy(seriesError = null) }
        scope.launch { readSeries() }
    }

    private suspend fun readAuthorWorks(illust: PixivIllust) {
        catchingFailures { withinBudget { runtime.client.userIllusts(illust.userId).items } }
            .onSuccess { works ->
                val shown = runtime.mutes.now.filter(works).filter { it.id != illust.id }.take(AUTHOR_WORKS)
                mutable.update { it.copy(authorWorks = shown, authorWorksError = null) }
            }
            .onFailure { error -> mutable.update { it.copy(authorWorksError = error) } }
    }

    private suspend fun readSeries() {
        catchingFailures { withinBudget { runtime.discovery.seriesContext(id) } }
            .onSuccess { place -> mutable.update { it.copy(series = place, seriesError = null) } }
            .onFailure { error -> mutable.update { it.copy(seriesError = error) } }
    }

    private suspend fun <T> withinBudget(read: suspend () -> T): T = withRequestBudget(PixivPages.READ_BUDGET_MILLIS, "pixiv work") { read() }

    private suspend fun remember(illust: PixivIllust) {
        if (runtime.historyPaused) return
        catchingFailures { runtime.history.record(PixivVisit(PixivKept.of(illust), Instant.now())) }
    }

    companion object {
        const val AUTHOR_WORKS = 12
    }
}

/**
 * The work models of a pager: one per work the reader passes, but read only
 * for the work they stopped on ([settle]) and kept only around it, so a
 * fling through twenty works neither sends their requests nor holds their
 * related lists until the pager closes. A work's model dropped here has its
 * requests cancelled with it.
 */
internal class PixivPagerWorks(
    private val parent: CoroutineScope,
    private val make: (scope: CoroutineScope, work: PixivIllust) -> PixivWorkModel,
) {
    private class Held(val model: PixivWorkModel, val scope: CoroutineScope)

    private val held = LinkedHashMap<Long, Held>()

    /** Whether the pager settled once: the work it opened on is read at once, later ones after a pause. */
    var settledOnce = false
        private set

    /** [work]'s model, made (and not yet reading) the first time it is asked for. */
    fun model(work: PixivIllust): PixivWorkModel = held.getOrPut(work.id) {
        val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
        Held(make(scope, work), scope)
    }.model

    /** The reader stopped on [index] of [ids]: its work starts reading, and every model further than [KEEP] works from it goes. */
    fun settle(ids: List<Long>, index: Int) {
        settledOnce = true
        val kept = ids.subList((index - KEEP).coerceIn(0, ids.size), (index + KEEP + 1).coerceIn(0, ids.size)).toSet()
        val gone = held.keys.filter { it !in kept }
        gone.forEach { id -> held.remove(id)?.scope?.cancel() }
        ids.getOrNull(index)?.let { held[it]?.model?.start() }
    }

    /** The works whose models are held, for tests. */
    val heldIds: Set<Long> get() = held.keys.toSet()

    companion object {
        /** How many works on each side of the one on screen keep their models: swiping back a little reads nothing again. */
        const val KEEP = 2

        /** How long the pager must rest on a work before it is read: a fling's works in passing are not. */
        const val SETTLE_MILLIS = 300L
    }
}
