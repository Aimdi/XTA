package com.aimdi.xtanative.networks.mastodon

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a search shows, in XTA's order: accounts, posts, hashtags. */
enum class MastodonSearchTab { ACCOUNTS, POSTS, HASHTAGS }

/**
 * One search, as XTA's MastodonSearchStore reads it ([MastodonClient.find]
 * routes it: a post's address reads that post, `user@instance` looks the
 * account up where it lives, anything else is a guest search), opened on the
 * first tab that found something.
 */
class MastodonSearchReader(
    private val client: MastodonClient,
    private val instances: () -> List<String>,
    val query: String,
    private val scope: CoroutineScope,
) {
    data class State(
        val loading: Boolean = true,
        val error: Throwable? = null,
        val tab: MastodonSearchTab = MastodonSearchTab.ACCOUNTS,
        val results: MastodonSearchPage = MastodonSearchPage(),
    )

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    init {
        search()
    }

    fun select(tab: MastodonSearchTab) = mutable.update { it.copy(tab = tab) }

    fun search() {
        mutable.value = State(loading = true)
        scope.launch {
            try {
                val results = client.find(query, instances())
                mutable.value = State(loading = false, tab = firstTab(results), results = results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = State(loading = false, error = e)
            }
        }
    }

    companion object {
        /** The first of accounts, posts and hashtags that found something; accounts when none did. */
        fun firstTab(results: MastodonSearchPage): MastodonSearchTab = when {
            results.accounts.isNotEmpty() -> MastodonSearchTab.ACCOUNTS
            results.posts.isNotEmpty() -> MastodonSearchTab.POSTS
            results.tags.isNotEmpty() -> MastodonSearchTab.HASHTAGS
            else -> MastodonSearchTab.ACCOUNTS
        }
    }
}
