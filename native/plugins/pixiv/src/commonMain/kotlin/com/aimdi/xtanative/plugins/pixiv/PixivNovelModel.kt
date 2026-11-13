package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivNovelBlock
import com.aimdi.xtanative.networks.pixiv.PixivNovelContent
import com.aimdi.xtanative.networks.pixiv.PixivNovelMarkup
import com.aimdi.xtanative.store.reading.ArticleReadPoint
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A novel being read: its detail, its text in blocks, where each page starts, and where the reader stopped last time. */
internal data class PixivNovelReading(
    val novel: PixivNovel? = null,
    val content: PixivNovelContent? = null,
    val blocks: List<PixivNovelBlock> = emptyList(),
    val pageStarts: Map<Int, Int> = emptyMap(),
    /** The block to come back to, read once from the journal. */
    val resumeAt: Int? = null,
    val loading: Boolean = true,
    val error: Throwable? = null,
)

/**
 * Reads a novel as XTA's reader does: the detail the list had first (else
 * Pixiv's), then its text; it joins the viewing history unless that is
 * paused, and where the reader stops is kept for the next time.
 */
internal class PixivNovelModel(private val runtime: PixivRuntime, private val scope: CoroutineScope, private val id: Long, seed: PixivNovel?) {
    private val mutable = MutableStateFlow(PixivNovelReading(novel = seed))
    val reading: StateFlow<PixivNovelReading> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutable.update { it.copy(loading = true, error = null) }
        scope.launch {
            try {
                val novel = mutable.value.novel ?: runtime.novels.detail(id)
                mutable.update { it.copy(novel = novel) }
                remember(novel)
                val content = runtime.novels.content(id)
                val blocks = PixivNovelMarkup.parse(content.text)
                val resume = runtime.novelPlaces.point(id)?.takeIf { !it.completed }?.paragraph
                mutable.value = PixivNovelReading(novel, content, blocks, PixivNovelMarkup.pageStarts(blocks), resume, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, error = e) }
            }
        }
    }

    private suspend fun remember(novel: PixivNovel) {
        if (runtime.historyPaused) return
        runCatching { runtime.novelHistory.record(PixivVisit(PixivKept.of(novel), Instant.now())) }
    }

    /** Keeps where the reader is: [block] of [total], finished once the last is reached. */
    fun keepPlace(block: Int, total: Int) {
        if (total <= 0) return
        val point = ArticleReadPoint(
            fraction = (block + 1).toDouble() / total,
            paragraph = block,
            completed = block >= total - 1,
            updatedAt = System.currentTimeMillis(),
        )
        runtime.novelPlaces.put(id, point)
    }
}
