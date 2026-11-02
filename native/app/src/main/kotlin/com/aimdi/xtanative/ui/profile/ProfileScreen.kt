package com.aimdi.xtanative.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.aimdi.xtanative.R
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.clickableNoRipple
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.ThreadRows
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(screenName: String, actions: PostActions, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: ProfileViewModel = viewModel { ProfileViewModel(graph, screenName) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val nearEnd by remember(list) {
        derivedStateOf {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, state.posts.size) { if (nearEnd) vm.loadMore() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(state.user?.name ?: "@$screenName", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 48.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header", contentType = "header") { Header(state.user, screenName, state.complete) }
            val problem = state.problem
            when {
                state.loading && state.posts.isEmpty() -> item(key = "loading") { LoadingRow() }
                problem != null -> item(key = "problem") { ProblemRow(problem, vm::load) }
            }
            items(state.posts, key = { it.id }, contentType = { "thread" }) { thread ->
                ThreadRows(thread, actions, state.pinnedIds)
            }
            item(key = "footer") {
                val more = state.moreProblem
                when {
                    state.loadingMore -> LoadingRow()
                    more != null -> ProblemRow(more, vm::loadMore)
                }
            }
        }
    }
}

@Composable
private fun Header(user: XUser?, screenName: String, complete: Boolean) {
    val background = MaterialTheme.colorScheme.background
    Column(Modifier.fillMaxWidth()) {
        Box {
            AsyncImage(
                model = user?.bannerUrl?.let(::bannerUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Avatar(
                user?.largeAvatarUrl,
                80.dp,
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 16.dp, y = 40.dp)
                    .border(4.dp, background, CircleShape),
            )
        }
        Spacer(Modifier.height(48.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user?.name ?: screenName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (user?.verified == true) {
                        Icon(XIcons.Verified, null, tint = Color.Unspecified, modifier = Modifier.padding(start = 4.dp).size(20.dp))
                    }
                }
                Text("@" + (user?.screenName ?: screenName), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            user?.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (complete && user != null) {
                Meta(user)
                Counts(user)
            }
        }
        Spacer(Modifier.height(12.dp))
        Hairline()
    }
}

/** Where, which site, and since when. */
@Composable
private fun Meta(user: XUser) {
    val uri = LocalUriHandler.current
    val joined = user.createdAt?.let {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "yMMMM")
        stringResource(R.string.joined, DateTimeFormatter.ofPattern(pattern).format(it.atZone(ZoneId.systemDefault())))
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        user.location?.let { MetaLine(Icons.Filled.LocationOn, it) }
        user.website?.let { site ->
            MetaLine(XIcons.Link, site.removePrefix("https://").removePrefix("http://").removeSuffix("/"), link = true) {
                runCatching { uri.openUri(site) }
            }
        }
        joined?.let { MetaLine(Icons.Filled.DateRange, it) }
    }
}

@Composable
private fun MetaLine(icon: ImageVector, text: String, link: Boolean = false, onClick: (() -> Unit)? = null) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick != null) Modifier.clickableNoRipple(onClick) else Modifier,
    ) {
        Icon(icon, null, tint = grey, modifier = Modifier.size(16.dp))
        Text(
            text,
            color = if (link) MaterialTheme.colorScheme.primary else grey,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun Counts(user: XUser) {
    val strong = MaterialTheme.colorScheme.onSurface
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOfNotNull(user.following?.let { it to R.string.following_count }, user.followers?.let { it to R.string.followers_count }).forEach { (n, label) ->
            val number = countText(n)
            val word = stringResource(label)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = strong)) { append(number) }
                    append(" ")
                    append(word)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** X's banners come in set sizes; a phone needs no more than this one. */
private fun bannerUrl(url: String): String = if (Regex("/\\d+x\\d+$").containsMatchIn(url)) url else "$url/1080x360"
