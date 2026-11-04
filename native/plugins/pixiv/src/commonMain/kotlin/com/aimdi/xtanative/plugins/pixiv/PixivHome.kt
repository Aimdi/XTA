package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivSearchKind
import com.aimdi.xtanative.networks.pixiv.PixivUserListKind
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.saved.SavedIcons

/**
 * The home tab: Home, Rankings, Favorites, Search and More over
 * illustrations or, in Novel mode, novels, as XTA's Pixiv screen. Before an
 * account is connected every section but More offers to sign in, over works
 * Pixiv shows without one.
 */
@Composable
internal fun PixivHome(runtime: PixivRuntime, frame: PluginFrame) {
    PixivRoot(runtime) {
        val model = rememberKept("pixiv-home") { scope -> PixivHomeModel(runtime, scope) }
        val view by model.view.collectAsState()
        val settings = pixivSettings()
        val signedIn = PixivAccounts.signedIn(settings)
        var reselected by remember { mutableIntStateOf(0) }
        LaunchedEffect(view.tab, view.novels, signedIn) { model.ensureShown() }
        RefreshesOn(frame.refresh) { model.refreshShown() }
        val toTop = frame.toTop + reselected
        Column(Modifier.fillMaxSize()) {
            HomeChrome(view, onTab = { tab -> if (tab == view.tab) reselected++ else model.show(tab) }, onMode = model::switchMode)
            HorizontalDivider()
            Box(Modifier.weight(1f)) {
                val padding = frame.padding
                when {
                    !signedIn && view.tab != PixivTab.MORE -> SignInBody(padding)
                    view.tab == PixivTab.HOME -> if (view.novels) NovelHomeSection(model, view, padding, toTop) else HomeSection(model, view, padding, toTop)
                    view.tab == PixivTab.RANKING -> RankingSection(model, view, padding, toTop)
                    view.tab == PixivTab.FAVORITES -> FavoritesSection(model, view, padding, toTop)
                    view.tab == PixivTab.SEARCH -> PixivSearchBody(if (view.novels) PixivSearchKind.NOVELS else PixivSearchKind.WORKS, initial = "", padding = padding, embedded = true)
                    else -> MorePane(view.novels, padding, toTop)
                }
            }
        }
    }
}

/** The section tabs, each an icon over its name, and the button switching between illustrations and novels. */
@Composable
private fun HomeChrome(view: PixivHomeView, onTab: (PixivTab) -> Unit, onMode: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        SectionTab(Icons.Filled.Home, pluginString(Strings.home), view.tab == PixivTab.HOME) { onTab(PixivTab.HOME) }
        SectionTab(XIcons.Views, pluginString(Strings.tab_ranking), view.tab == PixivTab.RANKING) { onTab(PixivTab.RANKING) }
        SectionTab(Icons.Filled.FavoriteBorder, pluginString(Strings.tab_favorites), view.tab == PixivTab.FAVORITES) { onTab(PixivTab.FAVORITES) }
        SectionTab(Icons.Filled.Search, pluginString(Strings.search), view.tab == PixivTab.SEARCH) { onTab(PixivTab.SEARCH) }
        SectionTab(Icons.Filled.Menu, pluginString(Strings.tab_more), view.tab == PixivTab.MORE) { onTab(PixivTab.MORE) }
        IconButton(onClick = onMode) {
            Icon(
                if (view.novels) PostIcons.Image else PixivIcons.MenuBook,
                pluginString(if (view.novels) Strings.mode_illusts else Strings.mode_novels),
                tint = if (view.novels) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RowScope.SectionTab(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).heightIn(min = 48.dp).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.padding(top = 2.dp).width(24.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(if (selected) color else Color.Transparent))
    }
}

/** A sideways row of choice chips over a list. */
@Composable
internal fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun Chip(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = icon?.let { { Icon(it, null, modifier = Modifier.size(18.dp)) } },
    )
}

/** Home: Following (with whose works it shows), the people followed, Recommended, Manga and Watchlist, over the chosen list. */
@Composable
private fun HomeSection(model: PixivHomeModel, view: PixivHomeView, padding: PaddingValues, toTop: Int) {
    val runtime = LocalPixiv.current
    Column(Modifier.fillMaxSize()) {
        ChipRow {
            Chip(pluginString(Strings.tab_following), view.source == PixivHomeSource.FOLLOWING, { model.showSource(PixivHomeSource.FOLLOWING) })
            if (view.source == PixivHomeSource.FOLLOWING) RestrictMenu(view.restrict, model::restrict)
            IconButton(onClick = { runtime.open(PixivRoute.Users(PixivUserListKind.FOLLOWING, 0)) }) { Icon(PostIcons.People, pluginString(Strings.following)) }
            Chip(pluginString(Strings.tab_recommended), view.source == PixivHomeSource.RECOMMENDED, { model.showSource(PixivHomeSource.RECOMMENDED) })
            Chip(pluginString(Strings.tab_manga), view.source == PixivHomeSource.MANGA, { model.showSource(PixivHomeSource.MANGA) })
            Chip(pluginString(Strings.tab_watchlist), view.source == PixivHomeSource.WATCHLIST, { model.showSource(PixivHomeSource.WATCHLIST) })
        }
        Box(Modifier.weight(1f)) {
            when (view.source) {
                PixivHomeSource.FOLLOWING -> if (view.restrict == PixivFollowRestrict.DEVICE) {
                    PixivWorksGrid(model.deviceFollowing, pluginString(Strings.device_following_empty), padding = padding, toTop = toTop)
                } else {
                    PixivWorksGrid(model.following, pluginString(Strings.empty), padding = padding, toTop = toTop)
                }
                PixivHomeSource.RECOMMENDED -> PixivWorksGrid(model.recommended, pluginString(Strings.recommended_empty), padding = padding, toTop = toTop, header = {
                    item(key = "recommended-header", span = StaggeredGridItemSpan.FullLine) { RecommendedHeader(model) }
                })
                PixivHomeSource.MANGA -> PixivWorksGrid(model.manga, pluginString(Strings.manga_empty), padding = padding, toTop = toTop)
                PixivHomeSource.WATCHLIST -> WatchlistFeed(model.watchlist, pluginString(Strings.watchlist_empty), padding, toTop, novels = false)
            }
        }
    }
}

/** Whose works Following shows (every followed creator, the public or the private follows on Pixiv, or those followed here), as one icon with a menu. */
@Composable
private fun RestrictMenu(restrict: PixivFollowRestrict, onPick: (PixivFollowRestrict) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val labels = listOf(
        PixivFollowRestrict.ALL to Strings.follow_restrict_all,
        PixivFollowRestrict.PUBLIC to Strings.follow_restrict_public,
        PixivFollowRestrict.PRIVATE to Strings.follow_restrict_private,
        PixivFollowRestrict.DEVICE to Strings.follow_restrict_device,
    )
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                restrictIcon(restrict),
                pluginString(labels.first { it.first == restrict }.second),
                tint = if (restrict == PixivFollowRestrict.ALL) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            labels.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(pluginString(label)) },
                    leadingIcon = { Icon(restrictIcon(value), null) },
                    trailingIcon = { if (value == restrict) Icon(Icons.Filled.Check, null) },
                    onClick = {
                        open = false
                        onPick(value)
                    },
                )
            }
        }
    }
}

private fun restrictIcon(restrict: PixivFollowRestrict): ImageVector = when (restrict) {
    PixivFollowRestrict.ALL -> PixivIcons.AllInclusive
    PixivFollowRestrict.PUBLIC -> SavedIcons.Public
    PixivFollowRestrict.PRIVATE -> Icons.Filled.Lock
    PixivFollowRestrict.DEVICE -> PixivIcons.Smartphone
}

/** Novel mode's Home: Recommended, Following (public or private) and Watchlist. */
@Composable
private fun NovelHomeSection(model: PixivHomeModel, view: PixivHomeView, padding: PaddingValues, toTop: Int) {
    Column(Modifier.fillMaxSize()) {
        ChipRow {
            Chip(pluginString(Strings.tab_recommended), view.novelSource == PixivNovelSource.RECOMMENDED, { model.showNovelSource(PixivNovelSource.RECOMMENDED) })
            Chip(pluginString(Strings.tab_following), view.novelSource == PixivNovelSource.FOLLOWING, { model.showNovelSource(PixivNovelSource.FOLLOWING) })
            if (view.novelSource == PixivNovelSource.FOLLOWING) {
                Chip(pluginString(Strings.follow_public), view.novelRestrict == PixivFavoritesSource.PUBLIC, { model.novelRestrict(PixivFavoritesSource.PUBLIC) })
                Chip(pluginString(Strings.follow_private), view.novelRestrict == PixivFavoritesSource.PRIVATE, { model.novelRestrict(PixivFavoritesSource.PRIVATE) })
            }
            Chip(pluginString(Strings.tab_watchlist), view.novelSource == PixivNovelSource.WATCHLIST, { model.showNovelSource(PixivNovelSource.WATCHLIST) })
        }
        Box(Modifier.weight(1f)) {
            when (view.novelSource) {
                PixivNovelSource.RECOMMENDED -> PixivNovelFeed(model.novelRecommended, pluginString(Strings.novel_recommended_empty), padding, toTop)
                PixivNovelSource.FOLLOWING -> PixivNovelFeed(model.novelFollowing, pluginString(Strings.novel_following_empty), padding, toTop)
                PixivNovelSource.WATCHLIST -> WatchlistFeed(model.novelWatchlist, pluginString(Strings.novel_watchlist_empty), padding, toTop, novels = true)
            }
        }
    }
}

/** Before an account is connected: the way to sign in, over works Pixiv shows without one. */
@Composable
private fun SignInBody(padding: PaddingValues) {
    val runtime = LocalPixiv.current
    val preview = rememberKept("pixiv-walkthrough") { scope -> PixivList(scope, { it.id }, { runtime.discovery.walkthrough(it) }, { runtime.mutes.now.filter(it) }).also { it.ensure() } }
    PixivWorksGrid(preview, empty = "", padding = padding, tile = { works, index -> WalkthroughTile(works[index]) }, header = {
        item(key = "sign-in", span = StaggeredGridItemSpan.FullLine) {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(pluginString(Strings.not_configured), textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { runtime.open(PixivRoute.SignIn) }) { Text(pluginString(Strings.sign_in)) }
            }
        }
        item(key = "walkthrough-title", span = StaggeredGridItemSpan.FullLine) {
            Text(pluginString(Strings.walkthrough_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    })
}

/** A work Pixiv shows before sign-in: only its picture, since opening it needs an account. */
@Composable
private fun WalkthroughTile(illust: PixivIllust) {
    val runtime = LocalPixiv.current
    val language = com.aimdi.xtanative.ui.plugins.currentLanguage()
    PixivPicture(
        illust.thumbnailUrl,
        Modifier.fillMaxWidth().aspectRatio(illust.aspectRatio.toFloat().coerceIn(0.45f, 1.6f)).clip(RoundedCornerShape(8.dp))
            .clickable { runtime.tell(Strings.walkthrough_sign_in.get(language)) },
        contentDescription = illust.title,
    )
}
