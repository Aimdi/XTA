package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivUserListKind
import com.aimdi.xtanative.networks.pixiv.PixivUserProfile
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.saved.SavedIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

/** Whose profile is shown, and how the reader stands with them. */
internal class PixivProfileScope(val profile: PixivUserProfile, val own: Boolean, val muted: Boolean)

/**
 * A creator's profile, as XTA's Kanten-style header draws it over their tabs:
 * Works, Novels (when they wrote any), Bookmarks, Following and Info. A muted
 * creator shows a placeholder first, with Show anyway and Unmute.
 */
@Composable
internal fun PixivUserScreen(id: Long, tab: String?) {
    val runtime = LocalPixiv.current
    val fetch = rememberKept("pixiv-user-$id") { scope -> PixivFetch(scope) { runtime.social.userProfile(id) }.also { it.ensure() } }
    val state by fetch.state.collectAsState()
    val profile = state.value
    var revealed by rememberSaveable { mutableStateOf(false) }
    if (profile == null) {
        PluginScaffold(title = "", onBack = runtime.host::back) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                if (state.error != null && !state.loading) PixivProblem(state.error, fetch::load) else LoadingRow()
            }
        }
        return
    }
    val scope = PixivProfileScope(profile, own = profile.id == runtime.ownId, muted = profile.id in pixivMutes().authorIds)
    PluginScaffold(title = profile.user.name, onBack = runtime.host::back, actions = { ProfileActions(scope) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (scope.muted && !revealed) MutedProfile(scope) { revealed = true } else ProfileView(scope, tab)
        }
    }
}

@Composable
private fun MutedProfile(scope: PixivProfileScope, onReveal: () -> Unit) {
    val runtime = LocalPixiv.current
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(pluginString(Strings.profile_muted), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            Button(onClick = onReveal) { Text(pluginString(Strings.profile_show_anyway)) }
            OutlinedButton(onClick = { runtime.mutes.unmuteAuthor(scope.profile.id) }) { Text(pluginString(Strings.unmute)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileView(scope: PixivProfileScope, initialTab: String?) {
    val tabs = profileTabs.filter { it.offeredFor(scope) }
    var shown by rememberSaveable { mutableStateOf(tabs.indexOfFirst { it.id == initialTab }.coerceAtLeast(0)) }
    CollapsingHeader(
        header = { UserHeader(scope.profile, onWorks = { shown = tabs.indexOfFirst { it.id == "works" }.coerceAtLeast(0) }) },
        pinned = {
            PrimaryScrollableTabRow(selectedTabIndex = shown.coerceIn(tabs.indices), edgePadding = 8.dp) {
                tabs.forEachIndexed { index, tab -> Tab(selected = index == shown, onClick = { shown = index }, text = { Text(pluginString(tab.label)) }) }
            }
        },
        body = { tabs[shown.coerceIn(tabs.indices)].content(scope) },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Share, and the menu: follow them privately, copy their info, mute or unmute them, open them on Pixiv. */
@Composable
private fun ProfileActions(scope: PixivProfileScope) {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    val outside = rememberOutside()
    val copy = rememberCopy()
    val language = currentLanguage()
    var open by remember { mutableStateOf(false) }
    val profile = scope.profile
    IconButton(onClick = { outside.share(profile.url) }) { Icon(Icons.Filled.Share, pluginString(Strings.share_link)) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, null) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val item: @Composable (ImageVector, PluginText, () -> Unit) -> Unit = { icon, label, run ->
                DropdownMenuItem(text = { Text(pluginString(label)) }, leadingIcon = { Icon(icon, null) }, onClick = {
                    open = false
                    run()
                })
            }
            if (!scope.own) item(Icons.Outlined.Lock, Strings.follow_privately) { sheets.show(PixivSheet.Follow(profile.user)) }
            item(PixivIcons.ContentCopy, Strings.profile_copy_info) { copy("${profile.user.name}\n@${profile.user.account}\n${profile.url}", Strings.profile_info_copied) }
            if (!scope.own && !scope.muted) {
                item(GroupIcons.PersonRemove, Strings.mute_author) {
                    sheets.ask.ask(Strings.mute_author.get(language), Strings.mute_author.get(language)) {
                        runtime.mutes.muteAuthor(profile.id, profile.user.name)
                    }
                }
            }
            if (!scope.own && scope.muted) item(Icons.Outlined.Person, Strings.unmute) { runtime.mutes.unmuteAuthor(profile.id) }
            item(SavedIcons.OpenInNew, Strings.open_on_pixiv) { outside.open(profile.url) }
        }
    }
}

/** The banner (a long press saves it), the avatar (a tap saves it), names, Premium, Follow and Add to group, the counts and the bio. */
@Composable
private fun UserHeader(profile: PixivUserProfile, onWorks: () -> Unit) {
    val user = profile.user
    Column(Modifier.fillMaxWidth()) {
        profile.backgroundUrl?.let { Banner(it) }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Identity(profile)
            Spacer(Modifier.height(14.dp))
            ProfileButtons(user)
            Counts(profile, onWorks)
            if (user.comment.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(user.comment.trim(), style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Banner(url: String) {
    val sheets = LocalPixivSheets.current
    val haptics = rememberHaptics()
    PixivPicture(
        url,
        Modifier.fillMaxWidth().height(140.dp).background(MaterialTheme.colorScheme.surfaceVariant).combinedClickable(onClick = {}, onLongClick = {
            haptics.medium()
            sheets.saveImage(url)
        }, onLongClickLabel = pluginString(Strings.profile_save_background)),
        contentDescription = null,
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun Identity(profile: PixivUserProfile) {
    val sheets = LocalPixivSheets.current
    val user = profile.user
    Row(verticalAlignment = Alignment.CenterVertically) {
        val avatar = Modifier.clip(CircleShape)
        PixivAvatar(user.avatarUrl, user.name, size = 72.dp, modifier = user.avatarUrl?.let { url -> avatar.clickable(onClickLabel = pluginString(Strings.profile_save_avatar)) { sheets.saveImage(url) } } ?: avatar)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(user.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("@" + user.account, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (profile.isPremium) {
                Spacer(Modifier.height(4.dp))
                val accent = MaterialTheme.colorScheme.primary
                Text(
                    pluginString(Strings.profile_premium),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.border(1.dp, accent, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

/** Works, Following and My pixiv, each number bold in its sentence; works shows the Works tab, following the list. */
@Composable
private fun Counts(profile: PixivUserProfile, onWorks: () -> Unit) {
    val runtime = LocalPixiv.current
    val user = profile.user
    FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.Center) {
        val works = countText(user.worksCount)
        Count(pluginPlural(Strings.works_count, user.worksCount.toInt(), user.worksCount.toInt(), works), works, onWorks)
        val following = countText(user.followingCount)
        Count(pluginPlural(Strings.following_count, user.followingCount.toInt(), user.followingCount.toInt(), following), following) {
            runtime.open(PixivRoute.Users(PixivUserListKind.FOLLOWING, profile.id))
        }
        val friends = countText(user.mypixivCount)
        Count(pluginPlural(Strings.mypixiv_count, user.mypixivCount.toInt(), user.mypixivCount.toInt(), friends), friends, null)
    }
}

@Composable
private fun Count(label: String, number: String, onClick: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val at = label.indexOf(number)
    val text = buildAnnotatedString {
        if (at < 0) {
            append(label)
        } else {
            append(label.substring(0, at))
            withStyle(SpanStyle(color = scheme.onSurface, fontWeight = FontWeight.Bold)) { append(number) }
            append(label.substring(at + number.length))
        }
    }
    val base = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
    Box(if (onClick != null) base.clickable(onClick = onClick) else base, contentAlignment = Alignment.CenterStart) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    }
}
