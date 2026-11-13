package com.aimdi.xtanative.plugins.tiktok

import com.aimdi.xtanative.networks.tiktok.TikTokSearchPage
import com.aimdi.xtanative.networks.tiktok.TikTokSearchUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the search shows before a search: discover's people and the trending searches. */
internal data class TikTokLanding(val people: List<TikTokSearchUser> = emptyList(), val trending: List<String> = emptyList(), val loading: Boolean = true)

/** A search run: what it found, while it reads, or why it failed. */
internal data class TikTokFound(val query: String, val page: TikTokSearchPage? = null, val error: Throwable? = null) {
    val loading: Boolean get() = page == null && error == null
}

/**
 * The search (tiktok_search_sheet.dart): discover's people and the trending
 * searches before anything is typed, TikTok's suggestions as words are
 * typed, and a guest search's people and videos. Every search goes first
 * among the recent ones.
 */
internal class TikTokSearchModel(private val app: TikTokApp, private val scope: CoroutineScope) {
    private val landingState = MutableStateFlow(TikTokLanding())
    private val suggested = MutableStateFlow<List<String>>(emptyList())
    private val foundState = MutableStateFlow<TikTokFound?>(null)

    val landing: StateFlow<TikTokLanding> = landingState.asStateFlow()
    val suggestions: StateFlow<List<String>> = suggested.asStateFlow()

    /** Null before the first search, and again once the field is emptied. */
    val found: StateFlow<TikTokFound?> = foundState.asStateFlow()

    @Volatile
    private var typed = ""
    private var suggesting: Job? = null

    /** Discover's people and the trending searches; a failure leaves the prompt to search. */
    fun loadLanding() {
        scope.launch {
            landingState.value = try {
                val client = app.client()
                TikTokLanding(client.discoverUsers(), client.trendingQueries(), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TikTokLanding(loading = false)
            }
        }
    }

    /** The field as typed: TikTok's suggestions a moment after the last key; emptied, the search is put away. */
    fun type(text: String) {
        typed = text
        suggesting?.cancel()
        if (text.isBlank()) {
            suggested.value = emptyList()
            foundState.value = null
            return
        }
        suggesting = scope.launch {
            delay(SUGGEST_AFTER_MILLIS)
            val words = try {
                app.client().suggestQueries(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            if (typed.trim() == text.trim()) suggested.value = words
        }
    }

    fun search(raw: String) {
        val query = raw.trim()
        if (query.isEmpty()) return
        typed = raw
        suggesting?.cancel()
        foundState.value = TikTokFound(query)
        scope.launch {
            foundState.value = try {
                app.rememberSearch(query)
                val page = app.client().search(query)
                suggested.value = page.suggestions
                TikTokFound(query, page = page)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TikTokFound(query, error = e)
            }
        }
    }

    private companion object {
        const val SUGGEST_AFTER_MILLIS = 350L
    }
}
