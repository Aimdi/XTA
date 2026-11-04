package com.aimdi.xtanative.plugins.ehviewer

import com.aimdi.xtanative.store.settings.EhReadingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One page at a time, swiped sideways. */
internal val EhReadingMode.paged: Boolean get() = this != EhReadingMode.VERTICAL

/** Pages advance towards the left, as in manga. */
internal val EhReadingMode.reversed: Boolean get() = this == EhReadingMode.RIGHT_TO_LEFT

/** What a tap on the reader does. */
internal enum class EhTap { PREVIOUS, NEXT, CHROME }

/**
 * Where the reader is (eh_reader_store.dart): the [page], the one under the
 * slider while it is dragged ([scrubPage]), the [mode], whether the bars
 * show, and [jump], which grows each time the reader asks for a page (a tap
 * zone, the slider, the jump dialog), so the pages move there, turning
 * ([smooth]) for a tap zone and at once otherwise; a page the view swiped or
 * scrolled to itself is [EhReaderModel.observe]d.
 */
internal data class EhReaderState(
    val page: Int,
    val mode: EhReadingMode,
    val scrubPage: Int? = null,
    val chromeVisible: Boolean = true,
    val jump: Int = 0,
    val smooth: Boolean = false,
) {
    val shownPage: Int get() = scrubPage ?: page
}

/**
 * The reader's page, mode and bars. [settled] hears each page the reader
 * settles on (history, the pages read ahead), never one under the slider
 * while it is dragged.
 */
internal class EhReaderModel(val total: Int, initialPage: Int, mode: EhReadingMode, private val settled: (Int) -> Unit) {
    private val mutable = MutableStateFlow(EhReaderState(bounded(initialPage), mode))
    val state: StateFlow<EhReaderState> = mutable.asStateFlow()

    /** A page the reader asked for; [smooth] turns to it rather than jumping. */
    fun goTo(page: Int, smooth: Boolean = false) {
        val target = bounded(page)
        val before = mutable.value.page
        mutable.update { it.copy(page = target, scrubPage = null, jump = it.jump + 1, smooth = smooth) }
        if (target != before) settled(target)
    }

    fun next() = goTo(mutable.value.page + 1, smooth = true)

    fun previous() = goTo(mutable.value.page - 1, smooth = true)

    /** A page the view swiped or scrolled to itself. */
    fun observe(page: Int) {
        val target = bounded(page)
        if (target == mutable.value.page) return
        mutable.update { it.copy(page = target) }
        settled(target)
    }

    /** Follows the slider while it is dragged; null gives the counter back to the page. */
    fun scrub(page: Int?) = mutable.update { it.copy(scrubPage = page?.let(::bounded)) }

    fun toggleChrome() = mutable.update { it.copy(chromeVisible = !it.chromeVisible) }

    /** Another mode, staying on the page. */
    fun setMode(mode: EhReadingMode) = mutable.update { if (it.mode == mode) it else it.copy(mode = mode, jump = it.jump + 1, smooth = false) }

    /** What a tap on a zone does ([EhReaderRules.tap]). */
    fun tap(action: EhTap) = when (action) {
        EhTap.CHROME -> toggleChrome()
        EhTap.NEXT -> next()
        EhTap.PREVIOUS -> previous()
    }

    private fun bounded(page: Int): Int = if (total < 1) 1 else page.coerceIn(1, total)
}

internal object EhReaderRules {
    /**
     * What a tap at [x] across a [width]-wide reader does: the outer thirds
     * turn pages, mirrored when reading right to left, and the middle shows
     * or hides the bars.
     */
    fun tap(x: Float, width: Float, mode: EhReadingMode): EhTap {
        val third = width / 3
        if (x >= third && x <= width - third) return EhTap.CHROME
        return if ((x < third) == mode.reversed) EhTap.NEXT else EhTap.PREVIOUS
    }

    /**
     * The page a vertical reader is on: the topmost one in view, or the last
     * page once all of it shows, since a short last page never reaches the top.
     */
    fun visiblePage(firstVisible: Int, lastShownWhole: Boolean, total: Int): Int = if (lastShownWhole) total else firstVisible
}
