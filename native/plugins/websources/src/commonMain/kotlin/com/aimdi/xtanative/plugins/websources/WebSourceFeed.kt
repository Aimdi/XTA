package com.aimdi.xtanative.plugins.websources

import com.aimdi.xtanative.networks.websources.WebSourceClient
import com.aimdi.xtanative.networks.websources.WebSourceDefinition
import com.aimdi.xtanative.networks.websources.WebSourceItem
import com.aimdi.xtanative.networks.websources.WebSourceList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where one list of a source stands: its posts, its next page, and what failed. */
internal data class WebSourceFeedState(
    val items: List<WebSourceItem> = emptyList(),
    val next: String? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    /** Why the first page did not come; what is shown stays when a refresh fails. */
    val error: Throwable? = null,
    val loadingMore: Boolean = false,
)

/**
 * One list of a web source, page by page, as XTA's WebSourceFeedStore: a
 * page that brings nothing new ends the list (the client sees to that), and
 * a page that failed waits [RETRY_AFTER_MS] before scrolling asks for it again.
 * [query] makes it a search's results.
 */
internal class WebSourceFeed(
    private val client: WebSourceClient,
    val source: WebSourceDefinition,
    private val list: WebSourceList,
    private val scope: CoroutineScope,
    private val query: String? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutable = MutableStateFlow(WebSourceFeedState())
    val state: StateFlow<WebSourceFeedState> = mutable.asStateFlow()
    private var job: Job? = null
    private var failedAt: Long? = null

    fun refresh() {
        job?.cancel()
        job = scope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            try {
                val page = client.list(source, list, query)
                failedAt = null
                mutable.value = WebSourceFeedState(page.items, page.next, loaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, loaded = true, error = e) }
            }
        }
    }

    /** The next page, after what is shown. */
    fun more() {
        val now = mutable.value
        val cursor = now.next ?: return
        if (now.loading || now.loadingMore || now.items.isEmpty()) return
        failedAt?.let { if (clock() - it < RETRY_AFTER_MS) return }
        job = scope.launch {
            mutable.update { it.copy(loadingMore = true) }
            try {
                val page = client.list(source, list, query, cursor, shown = now.items.map { it.key }.toSet())
                failedAt = null
                mutable.update { it.copy(items = it.items + page.items, next = page.next, loadingMore = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The list keeps what it has; the next scroll after a pause tries again.
                failedAt = clock()
                mutable.update { it.copy(loadingMore = false) }
            }
        }
    }

    companion object {
        const val RETRY_AFTER_MS = 5_000L
    }
}
