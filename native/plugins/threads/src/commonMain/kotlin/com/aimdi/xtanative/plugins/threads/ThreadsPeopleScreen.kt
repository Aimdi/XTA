package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsMore
import com.aimdi.xtanative.networks.threads.ThreadsPaging
import com.aimdi.xtanative.networks.threads.ThreadsPeopleList
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.EmptyPane
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginAvatar
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

/**
 * One people list as far as it has been read: its first page for the words
 * Meta was asked to search, if any, and the pages after it as the reader
 * scrolls. [target] gives the account's id or the post's media id, found
 * once and remembered by the client.
 */
internal class ThreadsPeopleModel(
    private val app: ThreadsApp,
    private val list: ThreadsPeopleList,
    private val query: String?,
    private val target: suspend () -> String,
    scope: CoroutineScope,
) {
    val first = Fetch<Page<ThreadsProfile>>(scope) { app.ready(); app.nextPage { app.direct.people(list, target(), null, query) } }
    val more = ThreadsPaging(scope, { it.username.lowercase() }) { cursor ->
        app.nextPage { app.direct.people(list, target(), cursor, query).let { ThreadsMore(it.items, it.next) } }
    }
}

/**
 * An account's followers, whom it follows and, for a signed-in reader, the
 * followers they both have, as Threads' own sheet shows them: a tab each,
 * a search box over them, and one row per person. A read only: each row's
 * Follow is the follow kept on this device.
 */
@Composable
internal fun ThreadsPeopleScreen(app: ThreadsApp, handle: String, opened: ThreadsPeopleList) {
    val key = remember(handle) { handle.trim().removePrefix("@").lowercase() }
    val settings by app.prefs.collectAsState()
    val me by app.signedIn.collectAsState()
    val signedIn = ThreadsPeopleView.signedIn(ThreadsSessions.of(settings))
    val tabs = ThreadsPeopleView.tabsFor(signedIn, me, key)
    var chosen by rememberSaveable { mutableStateOf(opened) }
    val tab = chosen.takeIf { it in tabs } ?: tabs.first()
    var profile by remember { mutableStateOf<ThreadsProfile?>(null) }
    LaunchedEffect(key) { profile = app.keptProfile(key)?.page?.profile }
    PluginScaffold(title = "@$key", onBack = app.host::back) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tabs.indexOf(tab), containerColor = MaterialTheme.colorScheme.background, divider = { Hairline() }) {
                tabs.forEach { list ->
                    Tab(selected = list == tab, onClick = { chosen = list }, text = { TabLabel(list, ThreadsPeopleView.count(profile, list)) })
                }
            }
            Box(Modifier.weight(1f)) {
                PeopleList(app, tab, "people-$key", signedIn) { app.direct.userIdFor(key) }
                NoticeHost(app.notices)
            }
        }
    }
}

/** Who liked the post Meta keeps under [mediaId], in the same rows. */
@Composable
internal fun ThreadsLikersScreen(app: ThreadsApp, mediaId: String) {
    val settings by app.prefs.collectAsState()
    val signedIn = ThreadsPeopleView.signedIn(ThreadsSessions.of(settings))
    PluginScaffold(title = pluginString(Strings.liked_by), onBack = app.host::back) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PeopleList(app, ThreadsPeopleList.LIKERS, "likers-$mediaId", signedIn) { mediaId }
            NoticeHost(app.notices)
        }
    }
}

@Composable
private fun TabLabel(list: ThreadsPeopleList, count: Long?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(pluginString(tabLabel(list)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count != null) Text(countText(count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun tabLabel(list: ThreadsPeopleList): PluginText = when (list) {
    ThreadsPeopleList.FOLLOWERS -> Strings.followers
    ThreadsPeopleList.FOLLOWING -> Strings.following
    ThreadsPeopleList.MUTUAL -> Strings.people_both
    ThreadsPeopleList.LIKERS -> Strings.liked_by
}

/**
 * One list: the search box, then its people as read so far, filtered by
 * the words typed at once; for a list Meta searches, the words are sent once
 * typing settles and the list read again for them.
 */
@Composable
private fun PeopleList(app: ThreadsApp, list: ThreadsPeopleList, key: String, signedIn: Boolean, target: suspend () -> String) {
    var typed by rememberSaveable(list) { mutableStateOf("") }
    var searched by rememberSaveable(list) { mutableStateOf<String?>(null) }
    LaunchedEffect(list, typed) {
        delay(SEARCH_AFTER_MILLIS)
        searched = ThreadsPeopleView.serverQuery(list, typed)
    }
    Column(Modifier.fillMaxSize()) {
        if (signedIn) SearchBox(typed) { typed = it }
        if (!signedIn) {
            SignedOut(app)
            return@Column
        }
        val model = rememberKept("threads-$key-${list.name}-${searched.orEmpty()}") { scope -> ThreadsPeopleModel(app, list, searched, target, scope) }
        LaunchedEffect(model) { model.first.ensure() }
        val first by model.first.state.collectAsState()
        val more by model.more.state.collectAsState()
        LaunchedEffect(first.value) { first.value?.let { model.more.start(it.items, it.next) } }
        val people = remember(first.value, more.items, typed) { ThreadsPeopleView.shown(first.value?.items.orEmpty(), more.items, typed) }
        val body = ThreadsPeopleView.body(signedIn, read = first.value != null, problem = first.problem != null, people = people.size, typed = typed)
        val state = rememberLazyListState()
        ReadsMoreNear(state, model.more, more)
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            when (body) {
                ThreadsPeopleView.Body.PROBLEM -> item(key = "problem") { RetryMessage(ThreadsFailures.profile(first.problem, currentLanguage()), onRetry = { model.first.load(force = true) }) }
                ThreadsPeopleView.Body.LOADING -> item(key = "loading") { LoadingRow() }
                ThreadsPeopleView.Body.EMPTY -> item(key = "empty") { MessageText(pluginString(if (list == ThreadsPeopleList.LIKERS) Strings.likers_empty else Strings.people_empty)) }
                ThreadsPeopleView.Body.NO_MATCH -> item(key = "none") { MessageText(pluginString(Strings.no_matching_accounts)) }
                else -> personRows(app, people) { app.openProfile(it) }
            }
            if (body == ThreadsPeopleView.Body.PEOPLE || body == ThreadsPeopleView.Body.NO_MATCH) moreFoot("people", more) { model.more.retry() }
        }
    }
}

@Composable
private fun SearchBox(typed: String, onType: (String) -> Unit) {
    OutlinedTextField(
        value = typed,
        onValueChange = onType,
        singleLine = true,
        placeholder = { Text(pluginString(Strings.search)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (typed.isEmpty()) null else {
            { IconButton(onClick = { onType("") }) { Icon(Icons.Filled.Close, contentDescription = pluginString(Strings.delete)) } }
        },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Threads shows these lists only to signed-in readers: where to sign in. */
@Composable
private fun SignedOut(app: ThreadsApp) {
    EmptyPane(ThreadsIcons.Mark, pluginString(Strings.people_signed_out), detail = pluginString(Strings.people_signed_out_detail)) {
        if (webSignIn) {
            Button(onClick = { app.host.open(ThreadsRoutes.SIGN_IN) }) {
                Icon(ThreadsIcons.Mark, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(pluginString(Strings.sign_in_web), modifier = Modifier.padding(start = 8.dp))
            }
        }
        TextButton(onClick = { app.host.openSettings() }) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(pluginString(Strings.settings), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** One row per person, as Threads draws them: face, handle with its badge, name; a tap opens the profile, Follow is kept on this device. */
internal fun LazyListScope.personRows(app: ThreadsApp, users: List<ThreadsProfile>, onOpen: (String) -> Unit) {
    items(users.distinctBy { it.username.lowercase() }, key = { "user:" + it.username.lowercase() }) { user -> ThreadsPersonRow(app, user, onOpen) }
}

@Composable
internal fun ThreadsPersonRow(app: ThreadsApp, user: ThreadsProfile, onOpen: (String) -> Unit) {
    val rows by app.accounts.state.collectAsState()
    val followed = ThreadsAccounts.follows(rows, user.username)
    val handle = ThreadsLinks.normaliseHandle(user.username) ?: user.username.lowercase()
    ListItem(
        leadingContent = { PluginAvatar(user.profilePicUrl, user.displayName, user.username, 40.dp) },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.username, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (user.isVerified) Icon(XIcons.Verified, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.padding(start = 4.dp).size(16.dp))
            }
        },
        supportingContent = user.fullName.trim().takeIf { it.isNotEmpty() }?.let { name -> { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = {
            TextButton(onClick = { if (followed) app.unfollow(handle) else app.follow(ThreadsAccount.of(user)) }) {
                Text(pluginString(if (followed) Strings.unfollow else Strings.follow))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onOpen(handle) },
    )
    HorizontalDivider()
}

/** How long typing rests before Meta is asked to search: a session read per pause, not per letter. */
private const val SEARCH_AFTER_MILLIS = 600L
