package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsBioLink
import com.aimdi.xtanative.networks.threads.ThreadsLink
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsProfilePage
import com.aimdi.xtanative.networks.threads.ThreadsProfileTab
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.PostIcons
import java.text.NumberFormat
import java.util.Locale

/** The rules of a profile's header beyond XTA's: its links, its exact counts. */
internal object ThreadsProfileHeader {
    /**
     * Every link the profile names, its own website first, then the ones under
     * its bio, each once whatever its scheme, "www." or trailing slash; never
     * its own Threads page, which the bar already opens.
     */
    fun links(profile: ThreadsProfile): List<ThreadsBioLink> {
        val site = profile.externalUrl?.trim()?.ifEmpty { null }?.let { ThreadsBioLink(it) }
        return (listOfNotNull(site) + profile.facts.links)
            .filter { (ThreadsLinks.parse(it.url) as? ThreadsLink.Profile)?.handle != profile.username.lowercase() }
            .distinctBy { ThreadsProfiles.linkLabel(it.url).lowercase() }
    }

    /** What a link's row reads: the title its owner gave it, else the address without its scheme. */
    fun label(link: ThreadsBioLink): String = link.title ?: ThreadsProfiles.linkLabel(link.url)

    /** Whether [kind]'s count is the figure Meta keeps, not the meta tags' rounded "5.7M", so a tap can show it whole. */
    fun isExact(profile: ThreadsProfile, kind: ThreadsCount): Boolean = when (kind) {
        ThreadsCount.FOLLOWERS -> !profile.facts.roundedFollowers
        ThreadsCount.POSTS -> !profile.facts.roundedPosts
        ThreadsCount.FOLLOWING -> true
    }

    /** The Threads tab: the account's own threads, its reposts having a tab of their own as on Threads. */
    fun threads(page: ThreadsProfilePage): List<ThreadsPost> = page.tab(ThreadsProfileTab.POSTS).filterNot { it.isRepost }

    /**
     * The Reposts tab: what the account shared of others', from its reposts
     * page ([read], pages after the first included) and any its own page
     * carried, newest first, each post once.
     */
    fun reposts(page: ThreadsProfilePage, read: List<ThreadsPost> = emptyList()): List<ThreadsPost> =
        (read + page.posts.filter { it.isRepost }).distinctBy { it.id }.sortedByDescending { it.publishedAt ?: java.time.Instant.MIN }

    /** [n] whole, grouped as [locale] groups digits: 5,812,345. */
    fun exact(n: Long, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(n)
}

/** The profile's links, one row each, opened outside the app (more than XTA's one website). */
@Composable
internal fun ProfileLinks(profile: ThreadsProfile) {
    val outside = rememberOpenOutside()
    val links = remember(profile) { ThreadsProfileHeader.links(profile) }
    Column(Modifier.padding(top = 4.dp)) {
        links.forEach { link ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(role = Role.Button) { outside(link.url) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(XIcons.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                Text(ThreadsProfileHeader.label(link), color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** "5.7M Followers"; a tap shows the whole figure when Meta gave it, and a second tap rounds it again. */
@Composable
internal fun ProfileCount(n: Long, label: PluginText, exact: Boolean) {
    var whole by rememberSaveable(n) { mutableStateOf(false) }
    val strong = MaterialTheme.colorScheme.onSurface
    val rounded = countText(n)
    val language = currentLanguage()
    val full = remember(n, language) { ThreadsProfileHeader.exact(n, Locale.forLanguageTag(language)) }
    val number = if (whole) full else rounded
    val word = pluginString(label)
    val tappable = exact && full != rounded
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = strong)) { append(number) }
            append(" ")
            append(word)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (tappable) Modifier.heightIn(min = 32.dp).clickable(role = Role.Button) { whole = !whole } else Modifier,
    )
}

/** The reader's private note on the account (XTA's ProfileNoteCard): one row, its editor in a dialog. */
@Composable
internal fun ProfileNote(app: ThreadsApp, handle: String) {
    val notes by app.accountNotes.state.collectAsState()
    val note = notes[ThreadsAccountNotes.key(handle)]
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.accountNotes.load() }
    if (editing) NoteDialog(note.orEmpty(), onSave = { text -> app.launch { app.accountNotes.keep(handle, text) } }) { editing = false }
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp).clickable(role = Role.Button) { editing = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PostIcons.TextSnippet, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(
            note ?: pluginString(Strings.profile_note_add),
            style = MaterialTheme.typography.bodyMedium,
            color = if (note == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        if (note != null) Icon(Icons.Outlined.Edit, contentDescription = pluginString(Strings.profile_note_title), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun NoteDialog(note: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluginString(Strings.profile_note_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(pluginString(Strings.profile_note_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onSave(text) }) { Text(pluginString(Strings.profile_note_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) } },
    )
}

/**
 * The foot of a profile's list: Threads gives a guest one page of an account,
 * so the list says where the rest is and opens it (XTA's list just stopped).
 */
@Composable
internal fun ProfileEnd(handle: String) {
    val outside = rememberOpenOutside()
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(pluginString(Strings.profile_guest_end), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { outside("${ThreadsLinks.WEB}/@$handle") }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(PostIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(pluginString(Strings.open_in_browser), modifier = Modifier.padding(start = 8.dp))
        }
    }
}
