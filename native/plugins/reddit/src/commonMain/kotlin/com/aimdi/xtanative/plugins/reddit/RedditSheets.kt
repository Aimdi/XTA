package com.aimdi.xtanative.plugins.reddit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.reddit.RedditPost
import com.aimdi.xtanative.networks.reddit.RedditSubredditAbout
import com.aimdi.xtanative.networks.reddit.normaliseSubreddit
import com.aimdi.xtanative.store.settings.RedditPrefs
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.saved.SavedIcons
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.store.settings.RedditSort as StoredSort
import com.aimdi.xtanative.store.settings.RedditTimeFilter as StoredWindow

/**
 * Everywhere a post can lead, without opening it (XTA's post sheet): its
 * author, its community, following that community, saving the post on this
 * device and its folder, the note on it, its community's groups, and leaving
 * for Reddit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostSheet(app: RedditApp, post: RedditPost, onDismiss: () -> Unit) {
    val followed by app.followed.collectAsState()
    val archived by app.host.saved.collectAsState()
    val outside = rememberOpenOutside()
    val share = rememberShareLink()
    val url = RedditThreadRules.postUrl(post)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                post.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )
            post.author?.let { author -> SheetAction(Icons.Outlined.Person, "u/$author") { onDismiss(); app.openUser(author) } }
            SheetAction(RedditGlyphs.TravelExplore, "r/" + post.subreddit) { onDismiss(); app.openSubreddit(post.subreddit) }
            val following = followed.follows(post.subreddit)
            SheetAction(if (following) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, pluginString(if (following) Strings.unsubscribe else Strings.subscribe)) {
                app.follow(post.subreddit, !following)
                onDismiss()
            }
            val isSaved = RedditArchive.id(post) in archived
            SheetAction(if (isSaved) RedditGlyphs.Bookmark else RedditGlyphs.BookmarkBorder, pluginString(if (isSaved) Strings.unsave_post else Strings.save_post)) {
                app.toggleSaved(post)
                onDismiss()
            }
            SheetAction(SavedIcons.Folder, pluginString(Strings.save_to_folder)) {
                onDismiss()
                app.chooseFolder(post)
            }
            SheetAction(SavedIcons.EditNote, pluginString(Strings.add_note)) {
                onDismiss()
                app.editNote(post)
            }
            SheetAction(PostIcons.People, pluginString(Strings.add_to_group)) {
                onDismiss()
                app.addToGroup(post.subreddit)
            }
            SheetAction(RedditGlyphs.OpenInNew, pluginString(Strings.open_in_browser)) {
                outside(url)
                onDismiss()
            }
            SheetAction(RedditGlyphs.Link, pluginString(Strings.share_link)) {
                onDismiss()
                share(url)
            }
        }
    }
}

/** One of the sheet's pills: outlined, full width, its icon before its words. */
@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 5.dp).heightIn(min = 48.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The order every listing reads in, one choice for all of them (XTA's sort
 * sheet), and for top and controversial their time window next. What was
 * chosen is kept once the sheet closes, and the listings read again.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SortSheet(app: RedditApp, onDismiss: () -> Unit) {
    val choices by app.choices.collectAsState()
    var picked by remember { mutableStateOf<StoredSort?>(null) }
    val done = { sort: StoredSort, window: StoredWindow? ->
        app.change { settings -> settings.with(RedditPrefs.sort, sort).let { if (window == null) it else it.with(RedditPrefs.timeFilter, window) } }
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = { picked?.let { done(it, null) } ?: onDismiss() }) {
        Text(
            pluginString(if (picked == null) Strings.sort else Strings.time_filter),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp),
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val sort = picked
            if (sort == null) {
                StoredSort.entries.forEach { choice ->
                    ChoiceChip(sortIcon(choice), sortLabel(choice), choice == choices.sort) { if (choice.usesTimeFilter) picked = choice else done(choice, null) }
                }
            } else {
                StoredWindow.entries.forEach { window -> ChoiceChip(null, windowLabel(window), window == choices.window) { done(sort, window) } }
            }
        }
    }
}

@Composable
private fun ChoiceChip(icon: ImageVector?, label: String, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) } },
        shape = CircleShape,
        border = null,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            labelColor = tint,
            leadingIconContentColor = tint,
        ),
    )
}

/** The glyph Reddit's own apps give each order. */
internal fun sortIcon(sort: StoredSort): ImageVector = when (sort) {
    StoredSort.HOT -> RedditGlyphs.Hot
    StoredSort.NEWEST -> RedditGlyphs.New
    StoredSort.TOP -> RedditGlyphs.Top
    StoredSort.RISING -> RedditGlyphs.Rising
    StoredSort.CONTROVERSIAL -> RedditGlyphs.Controversial
}

@Composable
internal fun sortLabel(sort: StoredSort): String = pluginString(
    when (sort) {
        StoredSort.HOT -> Strings.sort_hot
        StoredSort.NEWEST -> Strings.sort_new
        StoredSort.TOP -> Strings.sort_top
        StoredSort.RISING -> Strings.sort_rising
        StoredSort.CONTROVERSIAL -> Strings.sort_controversial
    },
)

@Composable
internal fun windowLabel(window: StoredWindow): String = pluginString(
    when (window) {
        StoredWindow.HOUR -> Strings.time_hour
        StoredWindow.DAY -> Strings.time_day
        StoredWindow.WEEK -> Strings.time_week
        StoredWindow.MONTH -> Strings.time_month
        StoredWindow.YEAR -> Strings.time_year
        StoredWindow.ALL -> Strings.time_all
    },
)

/** "Top · This week", or "Hot": the order a listing reads in, as settings name it. */
@Composable
internal fun sortTitle(choices: RedditChoices): String =
    if (choices.sort.usesTimeFilter) sortLabel(choices.sort) + " · " + windowLabel(choices.window) else sortLabel(choices.sort)

/** The communities followed here: each to open or to drop, and one more to add. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunitiesSheet(app: RedditApp, onDismiss: () -> Unit) {
    val names by app.followed.collectAsState()
    var adding by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(RedditGlyphs.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(pluginString(Strings.communities), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (names.isNotEmpty()) {
                Text("${names.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
        LazyColumn(Modifier.weight(1f, fill = false)) {
            if (names.isEmpty()) item(key = "none") { RedditEmpty(RedditGlyphs.Forum, pluginString(Strings.empty)) }
            items(names, key = { it.lowercase() }) { name ->
                CommunityTile(app, name, onOpen = {
                    onDismiss()
                    app.openSubreddit(name)
                })
            }
        }
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(pluginString(Strings.add), color = MaterialTheme.colorScheme.primary) },
            leadingContent = { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable { adding = true }.padding(bottom = 8.dp),
        )
    }
    if (adding) AddSubredditDialog(app) { adding = false }
}

@Composable
private fun CommunityTile(app: RedditApp, name: String, onOpen: () -> Unit) {
    val about by produceState<RedditSubredditAbout?>(null, name) { value = attempt { app.about(name) }.getOrNull() }
    ListItem(
        headlineContent = { Text("r/$name", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = about?.subscribers?.let { count -> { FollowersLine(count) } },
        leadingContent = { SubredditAvatar(app, name, 44.dp, known = about?.let { it.iconUrl.orEmpty() }) },
        trailingContent = {
            IconButton(onClick = { app.follow(name, follow = false) }) { Icon(Icons.Outlined.Delete, contentDescription = pluginString(Strings.delete)) }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

/** "12K followers", beside the people glyph. */
@Composable
internal fun FollowersLine(count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(RedditGlyphs.People, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            pluginPlural(Strings.followers_count, count, countText(count.toLong())),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Asks for a community and follows it on this device; a name Reddit could not have says so, and the dialog stays. */
@Composable
internal fun AddSubredditDialog(app: RedditApp, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val submit: () -> Unit = {
        val entered = text.trim()
        when {
            entered.isEmpty() -> onDismiss()
            normaliseSubreddit(entered) == null -> {
                invalid = true
            }
            else -> {
                app.follow(entered, follow = true)
                onDismiss()
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluginString(Strings.add)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    invalid = false
                },
                singleLine = true,
                placeholder = { Text("r/dartlang") },
                isError = invalid,
                supportingText = if (invalid) { { Text(pluginString(Strings.error_not_found)) } } else null,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        },
        confirmButton = { TextButton(onClick = submit) { Text(pluginString(Strings.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) } },
    )
}

/** The sidebar as a sheet: what the community is, how many read it, and its own words. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutSheet(app: RedditApp, subreddit: String, onDismiss: () -> Unit) {
    var tries by remember { mutableIntStateOf(0) }
    val about by produceState<Result<RedditSubredditAbout>?>(null, subreddit, tries) { value = attempt { app.about(subreddit) } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val read = about
        val failure = read?.exceptionOrNull()
        when {
            read == null -> LoadingRow()
            failure != null -> RedditProblemView(failure, onRetry = { tries++ })
            else -> AboutContent(app, read.getOrThrow())
        }
    }
}

@Composable
private fun AboutContent(app: RedditApp, about: RedditSubredditAbout) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SubredditAvatar(app, about.name, 48.dp, known = about.iconUrl.orEmpty())
            Spacer(Modifier.width(12.dp))
            Text("r/" + about.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        about.title?.takeIf { it != about.name }?.let { Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp)) }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            about.subscribers?.let { FollowersLine(it) }
            about.activeUsers?.let { active ->
                Spacer(Modifier.width(12.dp))
                Spacer(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(
                    countText(active.toLong()) + " " + pluginString(Strings.online_now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        about.description?.let { RedditText(it, Modifier.padding(top = 16.dp)) }
    }
}
