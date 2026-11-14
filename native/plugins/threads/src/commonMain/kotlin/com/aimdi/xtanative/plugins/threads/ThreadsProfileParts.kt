package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.networks.threads.ThreadsBioLink
import com.aimdi.xtanative.networks.threads.ThreadsLink
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsPeopleList
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsProfileAbout
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

    /** The people list a count opens: followers, or whom the account follows; none for its posts. */
    fun list(kind: ThreadsCount): ThreadsPeopleList? = when (kind) {
        ThreadsCount.FOLLOWERS -> ThreadsPeopleList.FOLLOWERS
        ThreadsCount.FOLLOWING -> ThreadsPeopleList.FOLLOWING
        ThreadsCount.POSTS -> null
    }

    /** [n] whole, grouped as [locale] groups digits: 5,812,345. */
    fun exact(n: Long, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(n)

    /**
     * When the account joined, as "Joined %s" reads it: the month and year of
     * the moment Meta gave, in [locale]'s words and [zone], else Meta's own
     * words for it; null when it gave neither.
     */
    fun joined(about: ThreadsProfileAbout, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String? =
        about.joinedAt?.let { DateTimeFormatter.ofPattern("LLLL yyyy", locale).withZone(zone).format(it) } ?: about.joined?.trim()?.ifEmpty { null }
}

/** The profile's links, one row each, opened outside the app (more than XTA's one website). */
@Composable
internal fun ProfileLinks(profile: ThreadsProfile) {
    val outside = rememberOpenOutside()
    val links = remember(profile) { ThreadsProfileHeader.links(profile) }
    Column(Modifier.padding(top = 6.dp)) {
        links.forEach { link ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(role = Role.Button) { outside(link.url) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(XIcons.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                Text(
                    ThreadsProfileHeader.label(link),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * "5.7M Followers". With [onOpen], a tap opens the list it counts and a long
 * press shows the whole figure; without, a tap shows the whole figure when
 * Meta gave it, and a second tap rounds it again.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProfileCount(n: Long, label: PluginText, exact: Boolean, onOpen: (() -> Unit)? = null) {
    var whole by rememberSaveable(n) { mutableStateOf(false) }
    val strong = MaterialTheme.colorScheme.onSurface
    val rounded = countText(n)
    val language = currentLanguage()
    val full = remember(n, language) { ThreadsProfileHeader.exact(n, Locale.forLanguageTag(language)) }
    val number = if (whole) full else rounded
    val word = pluginString(label)
    val tappable = exact && full != rounded
    val showList = pluginString(Strings.show_list)
    val tap = when {
        onOpen != null -> Modifier.heightIn(min = 48.dp).combinedClickable(
            role = Role.Button,
            onClickLabel = showList,
            onClick = onOpen,
            onLongClick = if (tappable) { { whole = !whole } } else null,
        )
        tappable -> Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { whole = !whole }
        else -> Modifier.heightIn(min = 48.dp)
    }
    // The words sit in the middle of what can be touched: at its top they left a blank band under the counts.
    Box(tap, contentAlignment = Alignment.CenterStart) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = strong)) { append(number) }
                append(" ")
                append(word)
            },
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The private note's editor (XTA's ProfileNoteCard's): only on this device, never synced; an empty note removes it. */
@Composable
internal fun NoteDialog(note: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
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
