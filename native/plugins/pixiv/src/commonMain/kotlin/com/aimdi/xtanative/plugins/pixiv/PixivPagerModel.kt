package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivPages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What follows a pager's last work. */
internal enum class PixivPagerTail { NONE, MORE, LOADING, FAILED, END }

/**
 * The works a pager swipes through: the list as it was tapped, growing by its
 * source's next pages, which only ever add at the end, so the work on screen
 * keeps its place. Pixiv's feeds repeat works across pages, so a page that
 * brings nothing new is read past, a few times at most.
 */
internal class PixivPagerModel(
    private val handoff: PixivWorkHandoff,
    private val scope: CoroutineScope,
    private val visible: (List<PixivIllust>) -> List<PixivIllust>,
) {
    private val mutableWorks = MutableStateFlow(handoff.works)
    val works: StateFlow<List<PixivIllust>> = mutableWorks.asStateFlow()

    private val mutableTail = MutableStateFlow(tailOf(handoff.source?.state?.value))
    val tail: StateFlow<PixivPagerTail> = mutableTail.asStateFlow()

    private var job: Job? = null

    /** The reader is on [index]: the next page loads once they come within [AHEAD] of the end. */
    fun near(index: Int) {
        if (index >= mutableWorks.value.size - AHEAD && mutableTail.value == PixivPagerTail.MORE) loadMore()
    }

    /** The source's next page, also as Retry after it failed. */
    fun loadMore() {
        val source = handoff.source ?: return
        if (job?.isActive == true || mutableTail.value == PixivPagerTail.END) return
        mutableTail.value = PixivPagerTail.LOADING
        job = scope.launch {
            repeat(PixivPages.EMPTY_PAGE_ADVANCE_LIMIT) {
                val after = nextPage(source)
                val merged = PixivPages.merge(mutableWorks.value, visible(after.items)) { it.id }
                val grew = merged.size > mutableWorks.value.size
                mutableWorks.value = merged
                if (grew || !after.hasMore || after.moreFailed) {
                    mutableTail.value = tailOf(after)
                    return@launch
                }
            }
            mutableTail.value = PixivPagerTail.FAILED
        }
    }

    /** Asks [source] for its next page and waits until it is in, or failed, or there was none. */
    private suspend fun nextPage(source: PixivList<PixivIllust>): PixivListState<PixivIllust> {
        if (source.state.value.moreFailed) source.retryMore() else source.more()
        return source.state.first { !it.loadingMore && !it.loading }
    }

    private companion object {
        const val AHEAD = 2

        fun tailOf(state: PixivListState<PixivIllust>?): PixivPagerTail = when {
            state == null -> PixivPagerTail.NONE
            state.moreFailed -> PixivPagerTail.FAILED
            state.hasMore -> PixivPagerTail.MORE
            else -> PixivPagerTail.END
        }
    }
}
