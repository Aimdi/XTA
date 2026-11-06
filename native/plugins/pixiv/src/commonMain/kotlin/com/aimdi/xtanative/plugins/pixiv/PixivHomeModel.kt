package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivUserPreview
import com.aimdi.xtanative.networks.pixiv.PixivWatchlistSeries
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Settings
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which list Home shows under its chips, for works and for novels. */
internal enum class PixivHomeSource { FOLLOWING, RECOMMENDED, MANGA, WATCHLIST }

internal enum class PixivNovelSource { RECOMMENDED, FOLLOWING, WATCHLIST }

/** Whose works Following shows: Pixiv's follows of the account (all, public or private), or the creators followed on this device. */
internal enum class PixivFollowRestrict(val api: String) { ALL("all"), PUBLIC("public"), PRIVATE("private"), DEVICE("device") }

/** Whose bookmarks Favorites shows: the account's public or private ones on Pixiv, or those kept on this device. */
internal enum class PixivFavoritesSource(val api: String) { PUBLIC("public"), PRIVATE("private"), DEVICE("device") }

/** The home tab's choices for one session: they are not kept, as in XTA. */
internal data class PixivHomeView(
    val tab: PixivTab = PixivTab.HOME,
    val novels: Boolean = false,
    val source: PixivHomeSource = PixivHomeSource.FOLLOWING,
    val restrict: PixivFollowRestrict = PixivFollowRestrict.ALL,
    val rankingMode: String = "day",
    val rankingDate: LocalDate? = null,
    val favorites: PixivFavoritesSource = PixivFavoritesSource.PUBLIC,
    /** The bookmark tag Favorites is narrowed to; null shows every bookmark. */
    val tag: String? = null,
    val novelSource: PixivNovelSource = PixivNovelSource.RECOMMENDED,
    val novelRestrict: PixivFavoritesSource = PixivFavoritesSource.PUBLIC,
    val novelRankingMode: String = "day",
    val novelRankingDate: LocalDate? = null,
    val novelFavorites: PixivFavoritesSource = PixivFavoritesSource.PUBLIC,
)

/** What the lists were read for: the account, and the Show R-18 and Hide AI choices, which drop works as each page is read. */
internal data class PixivSessionKey(val userId: Long, val token: String, val showR18: Boolean, val hideAi: Boolean) {
    companion object {
        fun of(settings: Settings) = PixivSessionKey(
            settings[PixivPrefs.userId],
            settings[PixivPrefs.refreshToken].trim(),
            settings[PixivPrefs.showR18],
            settings[PixivPrefs.hideAi],
        )
    }
}

/**
 * The home tab while it is on the back stack: the section and choices the
 * reader made this session, and every list it shows, each read when first
 * shown. Signing out, switching account or changing Show R-18 or Hide AI
 * empties them all, so one account's private lists never show under another.
 */
internal class PixivHomeModel(private val runtime: PixivRuntime, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(PixivHomeView(tab = PixivTab.of(runtime.settings[PixivPrefs.startSection])))
    val view: StateFlow<PixivHomeView> = mutable.asStateFlow()

    private val works: (List<PixivIllust>) -> List<PixivIllust> = { runtime.mutes.now.filter(it) }
    private val novelsShown: (List<PixivNovel>) -> List<PixivNovel> = { runtime.mutes.now.filterNovels(it) }

    val following = PixivList(scope, { it.id }, ::followingPage, works)
    val deviceFollowing = PixivList(scope, { it.id }, { next -> if (next != null) Page(emptyList(), null) else PixivFollowedHere.read(runtime) }, works)
    val recommended = PixivList(scope, { it.id }, { runtime.client.recommended(it) }, works)
    val spotlight = PixivList(scope, { it.id }, { runtime.discovery.spotlightArticles(it) })
    val users = PixivList<PixivUserPreview>(scope, { it.user.id }, { runtime.discovery.recommendedUsers(it) })
    val manga = PixivList(scope, { it.id }, { runtime.discovery.mangaRecommended(it) }, works)
    val watchlist = PixivList<PixivWatchlistSeries>(scope, { it.id }, { runtime.discovery.mangaWatchlist(it) })
    val ranking = PixivList(scope, { it.id }, ::rankingPage, works)
    val bookmarks = PixivList(scope, { it.id }, ::bookmarksPage)
    val novelRecommended = PixivList(scope, { it.id }, { runtime.novels.recommended(it) }, novelsShown)
    val novelFollowing = PixivList(scope, { it.id }, { runtime.novels.following(view.value.novelRestrict.api, it) }, novelsShown)
    val novelWatchlist = PixivList<PixivWatchlistSeries>(scope, { it.id }, { runtime.novels.watchlist(it) })
    val novelRanking = PixivList(scope, { it.id }, ::novelRankingPage, novelsShown)
    val novelBookmarks = PixivList(scope, { it.id }, { runtime.novels.ownBookmarks(view.value.novelFavorites.api, it) })

    private val all: List<PixivList<*>> = listOf(
        following, deviceFollowing, recommended, spotlight, users, manga, watchlist, ranking, bookmarks,
        novelRecommended, novelFollowing, novelWatchlist, novelRanking, novelBookmarks,
    )

    init {
        scope.launch {
            runtime.ready()
            runtime.settings.state.map(PixivSessionKey::of).distinctUntilChanged().drop(1).collect { forget() }
        }
        scope.launch {
            runtime.watchlistChanges.collect { novels -> (if (novels) novelWatchlist else watchlist).takeIf { it.state.value.loaded }?.refresh() }
        }
    }

    /** The lists the section on show reads, each read when it has nothing yet. */
    fun ensureShown() {
        if (!runtime.signedIn) return
        shownLists().forEach { it.ensure() }
    }

    private fun shownLists(): List<PixivList<*>> {
        val view = mutable.value
        return when (view.tab) {
            PixivTab.HOME -> if (view.novels) listOf(novelList(view.novelSource)) else homeLists(view)
            PixivTab.RANKING -> listOf(if (view.novels) novelRanking else ranking)
            PixivTab.FAVORITES -> listOfNotNull(if (view.novels) novelBookmarks.takeIf { view.novelFavorites != PixivFavoritesSource.DEVICE } else bookmarks.takeIf { view.favorites != PixivFavoritesSource.DEVICE })
            PixivTab.SEARCH, PixivTab.MORE -> emptyList()
        }
    }

    private fun homeLists(view: PixivHomeView): List<PixivList<*>> = when (view.source) {
        PixivHomeSource.FOLLOWING -> listOf(if (view.restrict == PixivFollowRestrict.DEVICE) deviceFollowing else following)
        PixivHomeSource.RECOMMENDED -> listOf(recommended, spotlight, users)
        PixivHomeSource.MANGA -> listOf(manga)
        PixivHomeSource.WATCHLIST -> listOf(watchlist)
    }

    private fun novelList(source: PixivNovelSource): PixivList<*> = when (source) {
        PixivNovelSource.RECOMMENDED -> novelRecommended
        PixivNovelSource.FOLLOWING -> novelFollowing
        PixivNovelSource.WATCHLIST -> novelWatchlist
    }

    /** Reads the section on show again, as the desktop's refresh asks; Recommended also reads what is above it. */
    fun refreshShown() = shownLists().forEach { it.refresh() }

    fun show(tab: PixivTab) = change { it.copy(tab = tab) }

    fun switchMode() = change { it.copy(novels = !it.novels) }

    fun showSource(source: PixivHomeSource) = change { it.copy(source = source) }

    fun showNovelSource(source: PixivNovelSource) = change { it.copy(novelSource = source) }

    fun restrict(restrict: PixivFollowRestrict) {
        if (restrict == mutable.value.restrict) return
        change { it.copy(restrict = restrict) }
        if (restrict != PixivFollowRestrict.DEVICE) following.refresh() else deviceFollowing.refresh()
    }

    fun novelRestrict(restrict: PixivFavoritesSource) {
        if (restrict == mutable.value.novelRestrict) return
        change { it.copy(novelRestrict = restrict) }
        novelFollowing.refresh()
    }

    fun rank(mode: String) {
        change { it.copy(rankingMode = mode) }
        ranking.clear()
        ensureShown()
    }

    fun rankOn(date: LocalDate?) {
        change { it.copy(rankingDate = date) }
        ranking.clear()
        ensureShown()
    }

    fun rankNovels(mode: String) {
        change { it.copy(novelRankingMode = mode) }
        novelRanking.clear()
        ensureShown()
    }

    fun rankNovelsOn(date: LocalDate?) {
        change { it.copy(novelRankingDate = date) }
        novelRanking.clear()
        ensureShown()
    }

    /** Picks whose bookmarks Favorites shows and under which tag; another source starts on every tag. */
    fun favorites(source: PixivFavoritesSource, tag: String?) {
        val now = mutable.value
        change { it.copy(favorites = source, tag = if (source == now.favorites) tag else null) }
        if (source != PixivFavoritesSource.DEVICE) {
            bookmarks.clear()
            ensureShown()
        }
    }

    fun novelFavorites(source: PixivFavoritesSource) {
        change { it.copy(novelFavorites = source) }
        if (source != PixivFavoritesSource.DEVICE) {
            novelBookmarks.clear()
            ensureShown()
        }
    }

    /** The board shown: the chosen one while its chip is there, else the first chip, for works or for novels. */
    fun effectiveMode(novels: Boolean): String {
        val settings = runtime.settings.now
        val table = PixivRankingPins.tableOf(novels)
        val visible = PixivRankingPins.visible(PixivRankingPins.of(settings, novels), table, settings[PixivPrefs.showR18])
        val view = mutable.value
        return PixivRankingPins.effective(if (novels) view.novelRankingMode else view.rankingMode, visible)
    }

    private fun change(edit: (PixivHomeView) -> PixivHomeView) {
        mutable.update(edit)
        ensureShown()
    }

    /** Empties every list: another account, or other content choices. */
    private fun forget() {
        all.forEach { it.clear() }
        ensureShown()
    }

    private suspend fun followingPage(next: String?): Page<PixivIllust> = runtime.client.following(mutable.value.restrict.api, next)

    private suspend fun rankingPage(next: String?): Page<PixivIllust> =
        runtime.discovery.ranking(effectiveMode(novels = false), mutable.value.rankingDate, next)

    private suspend fun novelRankingPage(next: String?): Page<PixivNovel> =
        runtime.novels.ranking(effectiveMode(novels = true), mutable.value.novelRankingDate, next)

    private suspend fun bookmarksPage(next: String?): Page<PixivIllust> {
        val view = mutable.value
        return runtime.client.bookmarks(runtime.client.ensureUserId(), view.favorites.api, view.tag, next)
    }
}
