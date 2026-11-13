package com.aimdi.xtanative.networks.hackernews

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Something one screen reads as a whole, as XTA's Hacker News stores read
 * it: [refresh] reads it again, keeping what was there until it answers; an
 * older read that answers late changes nothing.
 */
abstract class HnReader<T>(private val scope: CoroutineScope, initial: T?) {
    data class State<T>(val value: T? = null, val loading: Boolean = false, val loaded: Boolean = false, val error: Throwable? = null)

    private val mutable = MutableStateFlow(State(initial))
    val state: StateFlow<State<T>> = mutable.asStateFlow()
    private val request = AtomicInteger()

    fun refresh() {
        val started = request.incrementAndGet()
        mutable.update { it.copy(loading = true, error = null) }
        scope.launch {
            try {
                val value = read()
                if (request.get() == started) mutable.value = State(value, loaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request.get() == started) mutable.update { it.copy(loading = false, loaded = true, error = e) }
            }
        }
    }

    protected abstract suspend fun read(): T
}

/** A story and its comments; [seed] is the story as its row showed it, drawn until the thread arrives. */
class HnThreadReader(private val client: HackerNewsClient, private val id: Long, seed: HnStory?, scope: CoroutineScope) :
    HnReader<HnThread>(scope, seed?.let { HnThread(it, emptyList()) }) {
    override suspend fun read(): HnThread = client.thread(id)
}

/** An account and its latest stories. */
class HnUserReader(private val client: HackerNewsClient, private val id: String, scope: CoroutineScope) : HnReader<HnUserPage>(scope, null) {
    override suspend fun read(): HnUserPage = coroutineScope {
        val stories = async { client.submissions(id).items }
        HnUserPage(client.user(id), stories.await())
    }
}

/** What a user's page shows: the account, and its latest stories. */
data class HnUserPage(val user: HnUser, val stories: List<HnStory>)

/**
 * Following: the latest stories of each account followed here, merged
 * ([HnDevice.following]). An account that fails leaves the others' stories;
 * the failure shows only when none could be read.
 */
class HnFollowingReader(private val client: HackerNewsClient, private val authors: () -> List<String>, scope: CoroutineScope) :
    HnReader<List<HnStory>>(scope, null) {
    override suspend fun read(): List<HnStory> = coroutineScope {
        val names = authors()
        val pages = names.map { name -> async { submissions(name) } }.awaitAll()
        pages.firstNotNullOfOrNull { it.exceptionOrNull() }?.takeIf { pages.none { page -> page.isSuccess } }?.let { throw it }
        HnDevice.following(pages.mapNotNull { it.getOrNull() })
    }

    private suspend fun submissions(name: String): Result<List<HnStory>> = try {
        Result.success(client.submissions(name).items)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
