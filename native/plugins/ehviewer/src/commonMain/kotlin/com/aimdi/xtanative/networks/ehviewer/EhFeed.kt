package com.aimdi.xtanative.networks.ehviewer

import com.aimdi.xtanative.networks.Page
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A gallery list as read so far. [error] is the last refresh's failure, kept until one succeeds. */
data class EhFeedState(
    val galleries: List<EhGallery> = emptyList(),
    val loading: Boolean = false,
    val error: Throwable? = null,
    val hasMore: Boolean = true,
    val loadingMore: Boolean = false,
)

/**
 * A gallery list read page by page (the front page, popular, a toplist,
 * watched or a search), following each page's link to the next.
 */
class EhFeed(private val read: suspend (next: String?) -> Page<EhGallery>) {
    private val mutableState = MutableStateFlow(EhFeedState())
    val state: StateFlow<EhFeedState> = mutableState.asStateFlow()

    @Volatile
    private var next: String? = null
    private val readingMore = AtomicBoolean(false)

    /** The first page again, in place of everything read so far. */
    suspend fun refresh() {
        next = null
        mutableState.update { it.copy(loading = true, hasMore = true) }
        try {
            val page = read(null)
            next = page.next
            mutableState.value = EhFeedState(galleries = page.items, hasMore = page.next != null)
        } catch (cancelled: CancellationException) {
            mutableState.update { it.copy(loading = false) }
            throw cancelled
        } catch (error: Exception) {
            mutableState.update { it.copy(loading = false, error = error) }
        }
    }

    /** The next page, added below; one at a time. A failure keeps what is shown and can be tried again. */
    suspend fun loadMore() {
        val from = next
        if (!state.value.hasMore || from == null || !readingMore.compareAndSet(false, true)) return
        mutableState.update { it.copy(loadingMore = true) }
        try {
            val page = read(from)
            next = page.next
            mutableState.update { it.copy(galleries = it.galleries + page.items, hasMore = page.next != null) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // What is shown stays; the next scroll tries again.
        } finally {
            readingMore.set(false)
            mutableState.update { it.copy(loadingMore = false) }
        }
    }
}
