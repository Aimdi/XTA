package com.aimdi.xtanative.ui.community

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.XCommunity
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.ProblemView
import com.aimdi.xtanative.ui.common.currentLocale
import com.aimdi.xtanative.ui.common.datePattern
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.discover_community_about
import com.aimdi.xtanative.ui.generated.resources.discover_community_created
import com.aimdi.xtanative.ui.generated.resources.discover_community_members
import com.aimdi.xtanative.ui.generated.resources.discover_community_rules
import com.aimdi.xtanative.ui.generated.resources.discover_member_count
import com.aimdi.xtanative.ui.generated.resources.discover_no_results
import com.aimdi.xtanative.ui.generated.resources.discover_popular
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.ThreadRows
import com.aimdi.xtanative.ui.search.DiscoverIcons
import com.aimdi.xtanative.ui.search.LoadMoreNearEnd
import com.aimdi.xtanative.ui.search.PersonDivider
import com.aimdi.xtanative.ui.search.PersonRow
import com.aimdi.xtanative.ui.search.pageStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** An X community, read only, as XTA shows one: its header, its Top posts and its About tab. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CommunityScreen(id: String, about: Boolean, actions: PostActions, openMembers: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: CommunityViewModel = viewModel { CommunityViewModel(graph, id, about) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val posts by vm.posts.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    if (state.tab == CommunityTab.TOP) LoadMoreNearEnd(list, posts, vm::morePosts)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) }
                },
                title = { Text(state.community?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val community = state.community
        val problem = state.problem
        val below = Modifier.padding(top = padding.calculateTopPadding())
        when {
            community == null && problem != null -> ProblemView(problem, vm::load, below)
            community == null -> LoadingRow(below)
            // Padding, not content padding: the tabs stick below the bar, not under it.
            else -> LazyColumn(
                state = list,
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                modifier = Modifier.fillMaxSize().then(below),
            ) {
                item(key = "header", contentType = "header") { CommunityHeader(community) { openMembers(community.id) } }
                stickyHeader(key = "tabs", contentType = "tabs") { CommunityTabs(state.tab, vm::select) }
                when (state.tab) {
                    CommunityTab.TOP -> {
                        items(posts.items, key = { "post-" + it.id }, contentType = { "thread" }) { thread ->
                            ThreadRows(thread, actions, pinnedIds = state.pinnedIds)
                        }
                        pageStatus(posts, Res.string.discover_no_results, vm::retryPosts, vm::morePosts)
                    }
                    CommunityTab.ABOUT -> item(key = "about", contentType = "about") { CommunityAbout(community) }
                }
            }
        }
    }
}

/** A community's members, each opening their profile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityMembersScreen(id: String, actions: PostActions, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: CommunityMembersViewModel = viewModel { CommunityMembersViewModel(graph, id) }
    val members by vm.members.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    LoadMoreNearEnd(list, members, vm::more)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) }
                },
                title = { Text(stringResource(Res.string.discover_community_members)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(members.items, key = { _, user -> user.id }, contentType = { _, _ -> "person" }) { index, user ->
                PersonRow(user, onOpen = { actions.openProfile(user.screenName) })
                if (index < members.items.lastIndex) PersonDivider()
            }
            pageStatus(members, Res.string.discover_no_results, vm::retry, vm::more, icon = DiscoverIcons.Groups)
        }
    }
}

/** A community in a list, as XTA's community tile: its banner, name and member count. */
@Composable
fun CommunityRow(community: XCommunity, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            val banner = community.bannerUrl
            if (banner == null) {
                Icon(DiscoverIcons.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                AsyncImage(model = banner, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(community.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(memberCount(community), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun memberCount(community: XCommunity): String {
    val count = community.memberCount.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    return pluralStringResource(Res.plurals.discover_member_count, count, count)
}

/** Banner, name, topic and the start of the description; the member count opens the members. */
@Composable
private fun CommunityHeader(community: XCommunity, onMembers: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        community.bannerUrl?.let { banner ->
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = banner,
                    contentDescription = community.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CommunityPage.bannerHeight(maxWidth.value).dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
            Text(community.name, style = MaterialTheme.typography.titleLarge)
            community.topic?.let { topic ->
                Text(topic, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CommunityPage.description(community)?.let { description ->
                Text(description, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onMembers).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val faces = CommunityPage.faces(community)
            faces.forEach { url -> Avatar(url, 24.dp, Modifier.padding(end = 4.dp)) }
            Text(
                memberCount(community),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = if (faces.isEmpty()) 0.dp else 4.dp),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CommunityTabs(selected: CommunityTab, onSelect: (CommunityTab) -> Unit) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = MaterialTheme.colorScheme.background,
        divider = { Hairline() },
    ) {
        CommunityTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = {
                    Text(stringResource(if (tab == CommunityTab.TOP) Res.string.discover_popular else Res.string.discover_community_about))
                },
                selectedContentColor = MaterialTheme.colorScheme.onBackground,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The description in full, the rules, and when the community was made. */
@Composable
private fun CommunityAbout(community: XCommunity) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        CommunityPage.description(community)?.let { description -> SelectionContainer { Text(description) } }
        val rules = CommunityPage.numberedRules(community)
        if (rules.isNotEmpty()) {
            Text(
                stringResource(Res.string.discover_community_rules),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
            rules.forEach { (number, rule) ->
                Row(Modifier.padding(vertical = 8.dp)) {
                    Text(number.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
                    Text(rule)
                }
            }
        }
        community.createdAt?.let { created ->
            Text(
                stringResource(Res.string.discover_community_created, dayText(created)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/** "Oct 12, 2026", as the reader's locale writes a day. */
@Composable
private fun dayText(then: Instant): String {
    val locale = currentLocale()
    return DateTimeFormatter.ofPattern(datePattern(locale, "yMMMd"), locale).format(then.atZone(ZoneId.systemDefault()))
}
