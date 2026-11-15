package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsPerson
import com.aimdi.xtanative.networks.threads.ThreadsSuggestedAccount
import com.aimdi.xtanative.ui.plugins.PluginAvatar
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/**
 * For you's people row: Threads' suggestions for the signed-in reader
 * ("Suggested for you", cards scrolling sideways) once they were read with
 * the account, marked as using it; else the strip of the feed's authors a
 * guest sees, as before.
 */
@Composable
internal fun ForYouPeople(app: ThreadsApp, guest: List<ThreadsPerson>) {
    val held by app.suggestions.state.collectAsState()
    val dismissed by app.suggestions.dismissed.collectAsState()
    val hidden = remember(dismissed) { dismissed.orEmpty().toSet() }
    val people = remember(held, hidden) {
        if (ThreadsSuggestedRules.fresh(held, System.currentTimeMillis())) ThreadsSuggestedRules.shown(held.people, hidden) else emptyList()
    }
    when {
        people.isNotEmpty() -> SuggestedForYou(app, people)
        guest.isNotEmpty() -> PeopleStrip(app, guest.filterNot { it.handle.lowercase() in hidden }, startOpen = true)
        else -> Spacer(Modifier.height(1.dp))
    }
}

/** Reads the suggestions once the reader has scrolled to the people row: never before, and once a visit ([ThreadsSuggestedPeople.reached]). */
@Composable
internal fun AsksSuggestionsWhenReached(app: ThreadsApp, list: LazyListState, hasRow: Boolean) {
    LaunchedEffect(app, list, hasRow) {
        if (!hasRow) return@LaunchedEffect
        snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.key == PEOPLE_KEY } }
            .distinctUntilChanged()
            .first { it }
        // Beyond the list's own scope, so a read already sent is not given up when the rows change.
        app.launch { app.suggestions.reached() }
    }
}

private const val PEOPLE_KEY = "people"

/** The heading, the account's line, and the cards. */
@Composable
private fun SuggestedForYou(app: ThreadsApp, people: List<ThreadsSuggestedAccount>) {
    val followed by app.accounts.state.collectAsState()
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
        Text(
            pluginString(Strings.suggested_for_you),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
        )
        AccountInUse(app)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(people, key = { it.handle }) { person ->
                SuggestedCard(app, person, follows = ThreadsAccounts.follows(followed, person.handle))
            }
        }
    }
}

/**
 * One suggested account, as threads.com draws it: a large round picture,
 * the name and handle, Follow (on this device only) turning to Following,
 * and an × that hides it from suggestions here. The card opens the profile.
 */
@Composable
private fun SuggestedCard(app: ThreadsApp, person: ThreadsSuggestedAccount, follows: Boolean) {
    val handle = "@" + person.handle
    Card(
        onClick = { app.openProfile(person.handle) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.width(CARD_WIDTH.dp),
    ) {
        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
            ) {
                PluginAvatar(person.avatarUrl, person.name, person.handle, AVATAR.dp)
                Text(
                    person.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    handle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                FollowHere(app, person, follows, handle)
            }
            IconButton(
                onClick = { app.dismissSuggested(person.handle) },
                modifier = Modifier.align(Alignment.TopEnd).size(48.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = pluginString(Strings.suggestion_hide, handle), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Follow on this device, filled; once followed, Following, which unfollows here again. Nothing is sent to Threads. */
@Composable
private fun FollowHere(app: ThreadsApp, person: ThreadsSuggestedAccount, follows: Boolean, handle: String) {
    val wide = Modifier.fillMaxWidth()
    if (follows) {
        val label = pluginString(Strings.suggestion_unfollow, handle)
        OutlinedButton(onClick = { app.unfollow(person.handle) }, modifier = wide.semantics { contentDescription = label }) {
            Text(pluginString(Strings.following_state), maxLines = 1)
        }
    } else {
        val label = pluginString(Strings.suggestion_follow, handle)
        Button(onClick = { app.followSuggested(person) }, modifier = wide.semantics { contentDescription = label }) {
            Text(pluginString(Strings.follow), maxLines = 1)
        }
    }
}

private const val CARD_WIDTH = 168
private const val AVATAR = 88
