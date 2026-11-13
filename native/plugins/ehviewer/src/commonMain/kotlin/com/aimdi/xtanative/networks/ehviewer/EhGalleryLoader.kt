package com.aimdi.xtanative.networks.ehviewer

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A gallery's page as read so far: its detail once loaded, and the preview
 * tiles of every sheet read since. [nextSheet] is the sheet (`?p=`) to read
 * next; [moreFailed] says reading it failed, so scrolling leaves it to the
 * reader's button. [error] is the last load's failure, kept until one succeeds.
 */
data class EhGalleryState(
    val detail: EhGalleryDetail? = null,
    val previews: List<EhPreview> = emptyList(),
    val nextSheet: Int = 1,
    val loading: Boolean = false,
    val error: Throwable? = null,
    val loadingMore: Boolean = false,
    val moreFailed: Boolean = false,
) {
    val hasMorePreviews: Boolean get() = nextSheet < (detail?.previewSheetCount ?: 0)

    companion object {
        fun loaded(detail: EhGalleryDetail): EhGalleryState =
            EhGalleryState(detail = detail, previews = detail.previews, nextSheet = detail.previewSheetIndex + 1)

        /** [known] tiles and a newly read [sheet], each page once, in page order. */
        fun merge(known: List<EhPreview>, sheet: List<EhPreview>): List<EhPreview> {
            val pages = known.mapTo(HashSet()) { it.page }
            return (known + sheet.filterNot { it.page in pages }).sortedBy { it.page }
        }
    }
}

/**
 * Reads a gallery's page: its detail with the first preview sheet, then the
 * further sheets one at a time as the reader scrolls or asks. A sheet that
 * arrives after the gallery was loaded again belongs to the old load and is dropped.
 */
class EhGalleryLoader(private val client: EhClient, val gallery: EhGallery) {
    private val mutableState = MutableStateFlow(EhGalleryState())
    val state: StateFlow<EhGalleryState> = mutableState.asStateFlow()

    /** Bumped by every load and by [close], so a sheet read for an older load is dropped. */
    private val generation = AtomicInteger()

    @Volatile
    private var closed = false

    /** The loaded detail, else what the gallery list already knew. */
    val shown: EhGallery get() = state.value.detail?.gallery ?: gallery

    /** Where reading goes on from, after [lastPage] in history; see [EhGallery.continuePage]. */
    fun continuePage(lastPage: Int?): Int? = shown.continuePage(lastPage)

    /** The detail and its first preview sheet, in place of what was read before. */
    suspend fun load() {
        val load = generation.incrementAndGet()
        change { it.copy(loading = true, loadingMore = false) }
        try {
            val detail = client.galleryDetail(gallery.gid, gallery.token)
            if (load == generation.get()) change { EhGalleryState.loaded(detail) }
        } catch (cancelled: CancellationException) {
            change { it.copy(loading = false) }
            throw cancelled
        } catch (error: Exception) {
            if (load == generation.get()) change { it.copy(loading = false, error = error) }
        }
    }

    /** The next preview sheet; the button's way in, so it also tries a failed sheet again. */
    suspend fun loadMorePreviews() {
        val start = state.value
        if (closed || !start.hasMorePreviews || start.loadingMore) return
        if (!mutableState.compareAndSet(start, start.copy(loadingMore = true, moreFailed = false))) return
        val load = generation.get()
        try {
            val sheet = client.previewSheet(gallery.gid, gallery.token, start.nextSheet)
            if (load == generation.get()) {
                change { it.copy(previews = EhGalleryState.merge(it.previews, sheet), nextSheet = start.nextSheet + 1, loadingMore = false) }
            }
        } catch (cancelled: CancellationException) {
            if (load == generation.get()) change { it.copy(loadingMore = false) }
            throw cancelled
        } catch (_: Exception) {
            if (load == generation.get()) change { it.copy(loadingMore = false, moreFailed = true) }
        }
    }

    /** The reader is close to the last tile: read on, unless the last try failed. */
    suspend fun nearEnd() {
        if (!state.value.moreFailed) loadMorePreviews()
    }

    /** Nothing read after this lands. */
    fun close() {
        closed = true
        generation.incrementAndGet()
    }

    private fun change(transform: (EhGalleryState) -> EhGalleryState) {
        if (!closed) mutableState.update(transform)
    }
}
