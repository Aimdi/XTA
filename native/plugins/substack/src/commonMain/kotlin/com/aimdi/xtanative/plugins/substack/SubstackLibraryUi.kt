package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.substack.SubstackBodySearch
import com.aimdi.xtanative.networks.substack.SubstackHomeRules
import com.aimdi.xtanative.networks.substack.SubstackLibrarySection
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.networks.substack.SubstackPublication
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.addToGroupText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.PluginText

/**
 * Library, as XTA's: the publications followed here (pinned first, each to
 * pin, put in groups or unfollow), and the posts saved and liked on this device, found by
 * title or, for those whose body the device keeps, by what they say.
 */
@Composable
internal fun LibraryPane(app: SubstackApp, controls: HomeControls, frame: PluginFrame) {
    val bodies = rememberKept("substack-library-bodies") { SubstackBodySearch { post -> app.keptBody(post) } }
    val saved by app.saves.state.collectAsState()
    val liked by app.likes.state.collectAsState()
    val query = controls.libraryQuery
    var passages by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(query, saved, liked) { passages = bodies.search(query, saved + liked) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { controls.libraryQuery = it },
            placeholder = { Text(pluginString(Strings.library_search)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubstackLibrarySection.entries.forEach { section ->
                FilterChip(selected = controls.librarySection == section, onClick = { controls.librarySection = section }, label = { Text(pluginString(sectionLabel(section))) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (controls.librarySection) {
                SubstackLibrarySection.FOLLOWING -> FollowingList(app, query, frame)
                SubstackLibrarySection.SAVED -> LibraryPosts(app, saved, query, passages, Strings.saved_empty, frame)
                SubstackLibrarySection.LIKED -> LibraryPosts(app, liked, query, passages, Strings.liked_empty, frame)
            }
        }
    }
}

private fun sectionLabel(section: SubstackLibrarySection) = when (section) {
    SubstackLibrarySection.FOLLOWING -> Strings.library_following
    SubstackLibrarySection.SAVED -> Strings.library_saved
    SubstackLibrarySection.LIKED -> Strings.library_liked
}

@Composable
private fun FollowingList(app: SubstackApp, query: String, frame: PluginFrame) {
    val publications by app.publications.collectAsState()
    val pins by app.pins.state.collectAsState()
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    val all = publications ?: return LoadingRow()
    val shown = all.filter { SubstackHomeRules.matches(query, it) }
    LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
        if (shown.isEmpty()) {
            item(key = "empty") {
                EmptyState(pluginString(if (query.isBlank()) Strings.empty else Strings.library_search_empty)) {
                    if (query.isBlank()) {
                        FilledTonalButton(onClick = app::openAdd, modifier = Modifier.padding(top = 16.dp)) {
                            Icon(Icons.Default.Add, null, Modifier.padding(end = 8.dp))
                            Text(pluginString(Strings.add))
                        }
                    }
                }
            }
        }
        items(shown, key = { "follow-" + it.id + "\n" + it.baseUrl }) { publication ->
            FollowRow(app, publication, publication.id in pins)
            Hairline()
        }
    }
}

@Composable
private fun FollowRow(app: SubstackApp, publication: SubstackPublication, pinned: Boolean) {
    var menu by remember { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { app.openPublication(publication) }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        PublicationLogo(publication.logoUrl, 56.dp, shape = CircleShape)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(publication.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(SubstackHomeRules.hostOf(publication), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        IconButton(onClick = { app.launch { app.pins.toggle(publication.id) } }) {
            Icon(if (pinned) SubstackIcons.PushPin else SubstackIcons.PushPinOutlined, pluginString(if (pinned) Strings.unpin else Strings.pin), tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, pluginString(Strings.more_options)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(addToGroupText()) }, onClick = { menu = false; app.addToGroup(publication) })
                DropdownMenuItem(text = { Text(pluginString(Strings.unfollow)) }, onClick = { menu = false; confirm = true })
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(pluginString(Strings.unfollow)) },
            text = { Text(publication.name) },
            confirmButton = { Button(onClick = { confirm = false; app.unfollow(publication) }) { Text(pluginString(Strings.unfollow)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(pluginString(Strings.cancel)) } },
        )
    }
}

@Composable
private fun LibraryPosts(
    app: SubstackApp,
    posts: List<SubstackPost>,
    query: String,
    passages: Map<String, String>,
    empty: PluginText,
    frame: PluginFrame,
) {
    val marks = rememberMarks(app)
    val shown = SubstackHomeRules.library(posts, query, passages)
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
        if (shown.isEmpty()) item(key = "empty") { EmptyState(pluginString(if (query.isBlank()) empty else Strings.library_search_empty)) }
        items(shown, key = { "post-" + it.first.key }) { (post, passage) ->
            passage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }
            SubstackPostCard(app, post, marks)
        }
    }
}
