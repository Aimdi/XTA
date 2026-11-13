package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivLinks
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivSearchFilter
import com.aimdi.xtanative.networks.pixiv.PixivSearchKind
import com.aimdi.xtanative.networks.pixiv.PixivSearchSort
import com.aimdi.xtanative.networks.pixiv.PixivTag
import com.aimdi.xtanative.networks.pixiv.PixivTrendTag
import com.aimdi.xtanative.networks.pixiv.PixivUser
import com.aimdi.xtanative.networks.pixiv.PixivUserPreview
import com.aimdi.xtanative.store.settings.PixivPrefs
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the search shows: the words searched and those being typed, the filter, the tags suggested and the popular strip. */
internal data class PixivSearchView(
    val filter: PixivSearchFilter,
    val word: String = "",
    val typed: String = "",
    val remembered: Boolean = false,
    val isPremium: Boolean = false,
    val suggestions: List<PixivTrendTag> = emptyList(),
    val popular: List<PixivIllust> = emptyList(),
    val historyExpanded: Boolean = false,
) {
    val searched: Boolean get() = word.isNotEmpty()

    /** Popular order without Premium reads Pixiv's free preview, which takes no dates. */
    val previewMode: Boolean get() = filter.sort == PixivSearchSort.POPULAR && !isPremium

    val showsPopularStrip: Boolean get() = !filter.sort.byPopularity && popular.isNotEmpty()

    /** What the strip was read for; null while results are ordered by popularity, which needs none. */
    val popularStripKey: Any? get() = if (filter.sort.byPopularity) null else listOf(word, filter.target, filter.hideAi, filter.ugoira)

    val numericId: Long? get() = PixivTyping.numericId(typed)

    /** Typing something new that has suggestions or opens by id: the picker shows instead of the results. */
    val picking: Boolean get() = typed.trim().let { it.isNotEmpty() && it != word && (suggestions.isNotEmpty() || numericId != null) }
}

/**
 * One search of works or novels, as XTA's search store: tag suggestions as
 * the reader types, the results (works, novels) and the creators found,
 * Pixiv's free popular preview beside newest-first results, the landing's
 * trending tags and suggested creators, and the recent searches.
 */
internal class PixivSearchModel(private val runtime: PixivRuntime, private val scope: CoroutineScope, val kind: PixivSearchKind) {
    private val premium get() = runtime.settings[PixivPrefs.isPremium]
    private val mutable = MutableStateFlow(
        PixivSearchView(
            filter = PixivSearchMemory.starting(runtime.settings.now, kind, premium),
            remembered = PixivSearchMemory.kept(runtime.settings.now, kind) != null,
            isPremium = premium,
        ),
    )
    val view: StateFlow<PixivSearchView> = mutable.asStateFlow()

    val works = PixivList<PixivIllust>(scope, { it.id }, { nothing() }, ::visibleWorks)
    val novels = PixivList<PixivNovel>(scope, { it.id }, { nothing() }) { runtime.mutes.now.filterNovels(it) }
    val users = PixivList<PixivUserPreview>(scope, { it.user.id }, { nothing() }) { previews -> runtime.mutes.now.withoutMutedAuthors(previews) { it.user.id } }
    val trending = PixivFetch(scope) { if (kind.isWorks) runtime.searchApi.trendingTags() else runtime.novels.trendingTags() }
    val creators = PixivFetch<List<PixivUser>>(scope) { runtime.searchApi.recommendedUsers() }

    private var suggesting: Job? = null

    private fun <T> nothing(): Page<T> = Page(emptyList(), null)

    private fun visibleWorks(illusts: List<PixivIllust>): List<PixivIllust> = mutable.value.filter.ugoira.applyTo(runtime.mutes.now.filter(illusts))

    /** The reader typed [text]: tags for its last word are asked for after a pause, none for a link. */
    fun type(text: String) {
        suggesting?.cancel()
        val word = PixivTyping.typedWord(text)
        val quiet = word.isEmpty() || (PixivTyping.numericId(text) == null && PixivLinks.parse(text) != null)
        mutable.update { it.copy(typed = text, suggestions = if (quiet) emptyList() else it.suggestions) }
        if (quiet) return
        suggesting = scope.launch {
            delay(DEBOUNCE_MS)
            val tags = runCatching { runtime.searchApi.autocomplete(word) }.getOrNull() ?: return@launch
            if (mutable.value.typed == text) mutable.update { it.copy(suggestions = tags) }
        }
    }

    /** A suggested tag: it replaces the word being typed after earlier ones, else it is searched. What the field shows is answered. */
    fun pick(tag: PixivTrendTag): String {
        suggesting?.cancel()
        val typed = mutable.value.typed
        if (PixivTyping.hasEarlierWords(typed)) {
            val text = PixivTyping.replacedTypedWord(typed, tag.name)
            mutable.update { it.copy(typed = text, suggestions = emptyList()) }
            return text
        }
        search(tag.name)
        return tag.name
    }

    fun search(text: String) {
        val word = text.trim()
        if (word.isEmpty()) return
        suggesting?.cancel()
        mutable.update { it.copy(word = word, typed = word, suggestions = emptyList(), popular = emptyList()) }
        runtime.settings.update { it.with(history, PixivRecentSearches.remembered(it[history], word)) }
        users.use { next -> runtime.searchApi.users(word, next) }
        users.refresh()
        readResults(strip = true)
    }

    /** Another filter, kept for the next searches when [remember]; results already shown are read again. */
    fun applyFilter(filter: PixivSearchFilter, remember: Boolean = mutable.value.remembered) {
        val before = mutable.value
        val next = before.copy(filter = filter, remembered = remember)
        val strip = next.popularStripKey != before.popularStripKey
        mutable.value = if (strip) next.copy(popular = emptyList()) else next
        runtime.settings.update { PixivSearchMemory.keeping(it, kind, if (remember) filter else null) }
        if (next.searched) readResults(strip)
    }

    fun loadLanding(force: Boolean = false) {
        if (force) trending.load() else trending.ensure()
        if (kind.isWorks) if (force) creators.load() else creators.ensure()
    }

    fun toggleHistory() = mutable.update { it.copy(historyExpanded = !it.historyExpanded) }

    private val history get() = PixivSearchMemory.historyOf(kind)

    fun forget(query: String) = runtime.settings.update { it.with(history, PixivRecentSearches.forgotten(it[history], query)) }

    fun clearHistory() = runtime.settings.update { it.with(history, emptyList()) }

    private fun readResults(strip: Boolean) {
        val search = mutable.value
        val includeAi = !search.filter.hideAi
        if (!kind.isWorks) {
            val query = search.filter.query(search.word, LocalDate.now(), kind)
            novels.use { next -> runtime.novels.search(query, next, includeAi) }
            novels.refresh()
            return
        }
        if (strip) readPopularStrip(search)
        works.use(worksSource(search, includeAi))
        works.refresh()
    }

    private fun worksSource(search: PixivSearchView, includeAi: Boolean): suspend (String?) -> Page<PixivIllust> {
        val filter = search.filter
        if (search.previewMode) {
            val word = PixivSearchFilter.searchWord(search.word, filter.usersIri)
            return { next -> runtime.searchApi.popularPreview(word, filter.target, next, includeAi) }
        }
        val query = filter.query(search.word, LocalDate.now())
        return { next -> runtime.searchApi.illusts(query, next, includeAi) }
    }

    private fun readPopularStrip(search: PixivSearchView) {
        val key = search.popularStripKey ?: return
        scope.launch {
            val page = try {
                runtime.searchApi.popularPreview(search.word, search.filter.target, null, !search.filter.hideAi)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            if (mutable.value.popularStripKey == key) mutable.update { it.copy(popular = visibleWorks(page.items)) }
        }
    }

    companion object {
        const val DEBOUNCE_MS = 300L

        /** Trending tags without those the reader muted; a muted work's picture leaves its tag bare. */
        fun visibleTrendTags(tags: List<PixivTrendTag>, mutes: PixivMutes): List<PixivTrendTag> = tags
            .filter { mutes.tagMatcher.match(listOf(PixivTag(it.name))) == null }
            .map { tag -> if (tag.illust?.let(mutes::isMuted) == true) tag.copy(illust = null) else tag }
    }
}
