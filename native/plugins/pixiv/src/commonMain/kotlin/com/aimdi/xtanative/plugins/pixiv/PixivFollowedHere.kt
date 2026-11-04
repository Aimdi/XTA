package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The newest works of the creators followed on this device, merged newest
 * first: each creator's own first page, a few read at a time. One creator
 * who cannot be read leaves the others; only when none can is it a failure.
 */
internal object PixivFollowedHere {
    /** How many creators one read asks for, the most recently followed first. */
    const val PER_READ = 30
    private const val AT_ONCE = 4

    suspend fun read(runtime: PixivRuntime): Page<PixivIllust> {
        val follows = PixivFollows.of(runtime.settings.now).asReversed().take(PER_READ)
        if (follows.isEmpty()) return Page(emptyList(), null)
        val reads = readEach(follows.map { it.id }) { id -> runtime.client.userIllusts(id).items }
        val works = reads.mapNotNull { it.getOrNull() }
        if (works.isEmpty()) reads.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
        return Page(merged(works), null)
    }

    /** Every creator's works in one list, newest first, each work once. */
    fun merged(works: List<List<PixivIllust>>): List<PixivIllust> =
        works.flatten().distinctBy { it.id }.sortedByDescending { it.createdAt?.toEpochMilli() ?: 0 }

    private suspend fun <T> readEach(ids: List<Long>, read: suspend (Long) -> T): List<Result<T>> = coroutineScope {
        val gate = Semaphore(AT_ONCE)
        ids.map { id ->
            async {
                gate.withPermit {
                    try {
                        Result.success(read(id))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }
        }.awaitAll()
    }
}
