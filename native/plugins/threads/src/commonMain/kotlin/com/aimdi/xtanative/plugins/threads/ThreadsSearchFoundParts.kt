package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsSearchFound
import com.aimdi.xtanative.networks.threads.ThreadsSearchReading
import com.aimdi.xtanative.networks.threads.ThreadsSearchTerms
import com.aimdi.xtanative.networks.threads.ThreadsSuggestion
import com.aimdi.xtanative.networks.threads.ThreadsSuggestions
import com.aimdi.xtanative.networks.threads.ThreadsTopic
import com.aimdi.xtanative.store.settings.ThreadsPrefs
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.PluginAvatar
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.search.DiscoverIcons

/**
 * The People tab: with a session, the accounts its people search finds;
 * then, for a guest too, the accounts that wrote the top posts, those the
 * words name first.
 */
internal fun LazyListScope.foundPeople(app: ThreadsApp, terms: ThreadsSearchTerms, found: ThreadsSearchFound, session: List<ThreadsProfile>) {
    val listed = session.map { it.username }.toSet()
    val authors = ThreadsSearchReading.people(found.posts, terms.query).filter { it.username !in listed }
    if (session.isNotEmpty()) people(app, session, app::openProfile)
    if (authors.isNotEmpty()) {
        item(key = "people-title") { SectionTitle(pluginString(Strings.people_in_results)) }
        people(app, authors, app::openProfile)
    }
    if (session.isEmpty() && authors.isEmpty()) item(key = "none") { MessageText(pluginString(Strings.no_matching_accounts)) }
}

/** The Topics tab: the words as a topic, and the topics the top posts are filed under, those the words name first. */
internal fun LazyListScope.foundTopics(app: ThreadsApp, terms: ThreadsSearchTerms, found: ThreadsSearchFound) {
    val topics = found.related(terms.query, terms.tag).sortedByDescending { it.community }
    if (topics.isEmpty()) {
        item(key = "none") { MessageText(pluginString(Strings.no_results)) }
        return
    }
    item(key = "topics-title") { SectionTitle(pluginString(Strings.topics_in_results)) }
    items(topics, key = { "topic:" + it.key }) { topic ->
        ListItem(
            leadingContent = { TopicMark(topic, 40.dp) },
            headlineContent = { Text(if (topic.community) topic.name else "#" + topic.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = if (topic.community) { { Text(pluginString(Strings.community)) } } else null,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { app.openTopic(topic) },
        )
        HorizontalDivider()
    }
}

/**
 * A topic's or community's page opens under its name and mark, saying what
 * the posts below are, with Follow on this device: its posts then come to
 * Home. Tapped again, Following unfollows it here.
 */
@Composable
internal fun ThreadsTopicHeader(app: ThreadsApp, topic: ThreadsTopic) {
    val followed by app.topics.state.collectAsState()
    LaunchedEffect(Unit) { app.topics.load() }
    val follows = ThreadsTopics.follows(followed, topic.name)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        TopicMark(topic, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (topic.community) topic.name else "#" + topic.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                pluginString(if (topic.community) Strings.community_posts else Strings.topic_posts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (followed != null) {
            if (follows) {
                OutlinedButton(onClick = { app.unfollowTopic(topic.name) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(pluginString(Strings.following)) }
            } else {
                FilledTonalButton(onClick = { app.followTopic(topic) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(pluginString(Strings.follow)) }
            }
        }
    }
    HorizontalDivider()
}

/** The community's emoji or picture when Threads gave one, else a `#`. */
@Composable
internal fun TopicMark(topic: ThreadsTopic, size: Dp) {
    val icon = topic.iconUrl
    if (topic.emoji == null && icon != null) {
        PluginAvatar(icon, topic.name, topic.key, size)
        return
    }
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val small = size < 32.dp
        val style = when {
            small -> MaterialTheme.typography.labelMedium
            topic.emoji != null -> MaterialTheme.typography.titleLarge
            else -> MaterialTheme.typography.titleMedium
        }
        Text(topic.emoji ?: "#", style = style)
    }
}

/** Topics and communities to explore, each opening its page: those followed here first, then those of the posts shown. */
@Composable
internal fun TopicStrip(app: ThreadsApp, topics: List<ThreadsTopic>) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            pluginString(Strings.search_topics),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 16.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            topics.forEach { topic ->
                AssistChip(
                    onClick = { app.openTopic(topic) },
                    label = { Text(if (topic.community) topic.name else "#" + topic.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { TopicMark(topic, 20.dp) },
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** A session's people search for the words; nothing for a guest, a tag, or a search that failed (the authors still show). */
@Composable
internal fun rememberSessionPeople(app: ThreadsApp, terms: ThreadsSearchTerms): List<ThreadsProfile> {
    if (terms.tag || !searchesPeople(app)) return emptyList()
    val fetch = rememberKept("threads-people-${terms.query}") { scope -> Fetch(scope) { app.ready(); app.direct.searchUsers(terms.query).items } }
    LaunchedEffect(fetch) { fetch.ensure() }
    val state by fetch.state.collectAsState()
    return state.value.orEmpty()
}

/**
 * What [typed] may be finishing, found on this device: the recent searches,
 * the accounts followed here and the topics of the posts read this session.
 */
@Composable
internal fun rememberSuggestions(app: ThreadsApp, typed: String): List<ThreadsSuggestion> {
    val settings by app.prefs.collectAsState()
    val rows by app.accounts.state.collectAsState()
    val forYou by app.forYou.state.collectAsState()
    val feed by app.feed.state.collectAsState()
    LaunchedEffect(Unit) { app.accounts.load() }
    val accounts = remember(rows) { rows.orEmpty().map { ThreadsSuggestion.Account(it.id, it.name, it.avatarUrl) } }
    val topics = remember(forYou.posts, feed.items) { ThreadsSearchReading.topics(feed.items + forYou.posts).map { it.name } }
    return remember(typed, settings, accounts, topics) { ThreadsSuggestions.of(typed, settings[ThreadsPrefs.searchHistory], accounts, topics) }
}

/** The suggestions for [typed] over what is below them; nothing at all when there are none. */
@Composable
internal fun ThreadsSuggestionList(app: ThreadsApp, typed: String, modifier: Modifier = Modifier, onPick: (ThreadsSuggestion) -> Unit) {
    val suggestions = rememberSuggestions(app, typed)
    if (suggestions.isEmpty()) return
    Surface(modifier, tonalElevation = 3.dp, shadowElevation = 3.dp) {
        LazyColumn {
            items(suggestions, key = { "suggestion:" + it.text }) { suggestion -> SuggestionRow(suggestion) { onPick(suggestion) } }
        }
    }
}

/** The suggestions for [typed] as rows in a column already scrolling, such as the sheet's. */
@Composable
internal fun ThreadsSuggestionRows(app: ThreadsApp, typed: String, onPick: (ThreadsSuggestion) -> Unit) {
    rememberSuggestions(app, typed).forEach { suggestion -> SuggestionRow(suggestion) { onPick(suggestion) } }
}

@Composable
private fun SuggestionRow(suggestion: ThreadsSuggestion, onClick: () -> Unit) {
    ListItem(
        leadingContent = {
            when (suggestion) {
                is ThreadsSuggestion.Account -> PluginAvatar(suggestion.avatarUrl, suggestion.name, suggestion.handle, 32.dp)
                is ThreadsSuggestion.Topic -> TopicMark(ThreadsTopic(suggestion.name), 32.dp)
                is ThreadsSuggestion.Recent -> Icon(DiscoverIcons.History, contentDescription = null)
            }
        },
        headlineContent = { Text(if (suggestion is ThreadsSuggestion.Account) suggestion.name else suggestion.text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = (suggestion as? ThreadsSuggestion.Account)?.let { { Text(it.text, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
