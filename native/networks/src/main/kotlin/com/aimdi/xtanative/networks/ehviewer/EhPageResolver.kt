package com.aimdi.xtanative.networks.ehviewer

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Where one page of the reader stands. A page never asked for has no slot. */
sealed interface EhPageSlot {
    /** Being fetched again, after a reload or a retry. */
    data object Loading : EhPageSlot

    data class Ready(val image: EhImagePage) : EhPageSlot

    data class Failed(val error: Throwable) : EhPageSlot
}

/**
 * Turns a gallery's page numbers into their images the way the site links
 * them: page number, then its page token (from a preview sheet, or from a
 * neighbouring image page's links), then its image page.
 *
 * Each page is fetched once and shared by everyone who asks; a page that
 * failed stays failed until [retry], so reading ahead never hammers the site.
 * The fetches run on [scope]'s dispatcher and end with it, or with [close].
 */
class EhPageResolver(
    private val client: EhClient,
    val gallery: EhGallery,
    val total: Int,
    previews: List<EhPreview> = emptyList(),
    scope: CoroutineScope,
) {
    private val work = EhWork(scope)
    private val lock = Any()
    private val known = previews.associateByTo(HashMap()) { it.page }
    private val pending = HashMap<Int, Deferred<EhImagePage?>>()
    private val sheets = HashMap<Int, Deferred<Unit>>()
    private var sheetSize: Int? = sheetSizeFrom(sheet = 0, previews = leadingRun(previews), total = total)
    private var closed = false

    private val mutableSlots = MutableStateFlow<Map<Int, EhPageSlot>>(emptyMap())

    /** Every page asked for so far, by page number. */
    val slots: StateFlow<Map<Int, EhPageSlot>> = mutableSlots.asStateFlow()

    /** The page's tile, once a preview sheet or a neighbouring page named its token. */
    fun previewOf(page: Int): EhPreview? = synchronized(lock) { known[page] }

    fun imageOf(page: Int): EhImagePage? = (slots.value[page] as? EhPageSlot.Ready)?.image

    /** The page's image page, fetched once: a ready page answers at once, a failed one with null until [retry]. */
    fun resolve(page: Int): Deferred<EhImagePage?> = synchronized(lock) {
        if (!asking(page)) return@synchronized answered(null)
        when (val slot = slots.value[page]) {
            is EhPageSlot.Ready -> answered(slot.image)
            is EhPageSlot.Failed -> answered(null)
            else -> pending.getOrPut(page) { start(page, reloadKey = null) }
        }
    }

    /** The page fetched again, after it failed. */
    fun retry(page: Int): Deferred<EhImagePage?> = refetch(page, reloadKey = null)

    /** The page fetched again from another image server, through its `nl` key. */
    fun reload(page: Int): Deferred<EhImagePage?> = refetch(page, imageOf(page)?.reloadKey)

    /** Stops every fetch; nothing changes after this. */
    fun close() {
        synchronized(lock) { closed = true }
        work.close()
    }

    /** A page of the gallery, while the resolver still reads: neither closed nor its scope ended. */
    private fun asking(page: Int): Boolean = !closed && work.active && page in 1..total

    private fun refetch(page: Int, reloadKey: String?): Deferred<EhImagePage?> = synchronized(lock) {
        if (!asking(page)) return@synchronized answered(null)
        pending[page] ?: run {
            set(page, EhPageSlot.Loading)
            start(page, reloadKey).also { pending[page] = it }
        }
    }

    /** An answer already there. Named, since `CompletableDeferred(null)` would be one with no parent that never completes. */
    private fun answered(image: EhImagePage?): Deferred<EhImagePage?> = CompletableDeferred(value = image)

    private fun start(page: Int, reloadKey: String?): Deferred<EhImagePage?> = work.scope.async { fetch(page, reloadKey) }

    /** The page's outcome lands in the same step as its fetch ends, so a reload asked for right after it fetches anew. */
    private suspend fun fetch(page: Int, reloadKey: String?): EhImagePage? {
        val outcome = try {
            val preview = previewFor(page) ?: throw EhException(EhErrorKind.NOT_FOUND, "No sheet lists page $page of ${gallery.gid}")
            val image = client.imagePage(gallery.gid, preview.pageToken, page, reloadKey)
            learnNeighbours(image)
            EhPageSlot.Ready(image)
        } catch (cancelled: CancellationException) {
            synchronized(lock) { pending.remove(page) }
            throw cancelled
        } catch (error: Exception) {
            EhPageSlot.Failed(error)
        }
        synchronized(lock) {
            pending.remove(page)
            set(page, outcome)
        }
        return (outcome as? EhPageSlot.Ready)?.image
    }

    /**
     * Reads the sheet the page should sit on. A wrong guess at the sheet size
     * is put right by what the sheet held, and the right sheet read next.
     */
    private suspend fun previewFor(page: Int): EhPreview? {
        val tried = HashSet<Int>()
        while (previewOf(page) == null && tried.size < 3) {
            val sheet = (page - 1) / (synchronized(lock) { sheetSize } ?: DEFAULT_SHEET_SIZE)
            if (!tried.add(sheet)) break
            loadSheet(sheet).await()
        }
        return previewOf(page)
    }

    private fun loadSheet(sheet: Int): Deferred<Unit> = synchronized(lock) {
        sheets.getOrPut(sheet) {
            work.scope.async {
                try {
                    val previews = client.previewSheet(gallery.gid, gallery.token, sheet)
                    synchronized(lock) {
                        previews.associateByTo(known) { it.page }
                        sheetSize = sheetSizeFrom(sheet, previews, total) ?: sheetSize
                    }
                } finally {
                    synchronized(lock) { sheets.remove(sheet) }
                }
            }
        }
    }

    /** An image page links its neighbours, so reading on needs no preview sheet. */
    private fun learnNeighbours(image: EhImagePage) = synchronized(lock) {
        listOfNotNull(image.prevPage, image.nextPage).filter { it.gid == gallery.gid }.forEach { link ->
            known.getOrPut(link.page) { EhPreview(link.pageToken, link.page) }
        }
    }

    private fun set(page: Int, slot: EhPageSlot) = synchronized(lock) {
        if (!closed) mutableSlots.update { it + (page to slot) }
    }

    companion object {
        /** Tiles per preview sheet with the large tiles XTA asks for, until a sheet says otherwise. */
        const val DEFAULT_SHEET_SIZE = 20

        /**
         * Pages per preview sheet, read off sheet [sheet] holding [previews]:
         * a full first sheet says it outright, a later one by the page it starts at.
         */
        fun sheetSizeFrom(sheet: Int, previews: List<EhPreview>, total: Int): Int? {
            val first = previews.minOfOrNull { it.page } ?: return null
            if (first == 1) return previews.size.takeIf { it < total }
            val before = first - 1
            return if (sheet > 0 && before % sheet == 0) before / sheet else null
        }

        /** The unbroken run of pages from page 1: the sheets a gallery's page read first. */
        fun leadingRun(previews: List<EhPreview>): List<EhPreview> {
            val byPage = previews.associateBy { it.page }
            return generateSequence(1) { it + 1 }.map { byPage[it] }.takeWhile { it != null }.filterNotNull().toList()
        }

        /** The gallery's length for the reader: its page count, else the furthest page known so far. */
        fun totalOf(gallery: EhGallery, previews: List<EhPreview>, initialPage: Int): Int =
            gallery.pageCount?.takeIf { it > 0 } ?: (previews.map { it.page } + initialPage).max()

        /** The pages worth fetching around [page]: the next three, then the one before. */
        fun preloadPages(page: Int, total: Int): List<Int> =
            listOf(page + 1, page + 2, page + 3, page - 1).filter { it in 1..total }
    }
}
