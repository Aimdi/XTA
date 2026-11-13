package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.plugins.AddToGroupIcon
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.PluginAvatar
import com.aimdi.xtanative.ui.plugins.PluginFilterChip
import com.aimdi.xtanative.ui.plugins.PluginMenuRow
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.addToGroupText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

/** The banner's height, and how far the face rises into it. */
private val BannerHeight = 128.dp
private val FaceRise = 44.dp
private val FaceSize = 80.dp
private val FaceRing = 4.dp

/**
 * The profile's header: the banner, the face over its lower edge with the
 * filters-and-groups button and following here beside it, then the name,
 * the handle with the private note, the bio, the links, the counts (each
 * opening its list, a long press showing the whole figure) and a word when
 * the profile is private. Following is the device's own, never Threads'.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThreadsProfileCard(app: ThreadsApp, profile: ThreadsProfile, onFilters: () -> Unit) {
    val picture = profile.pictureUrl.ifEmpty { null }
    val openPicture = picture?.let { { app.host.openMedia(listOf(MediaItem(imageUrl = it, aspectRatio = 1f, shareUrl = "${ThreadsLinks.WEB}/@${profile.username}")), 0) } }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            ProfileBanner(ThreadsProfileView.bannerUrl(profile))
            Row(
                Modifier.fillMaxWidth().padding(top = BannerHeight - FaceRise - FaceRing, start = 16.dp - FaceRing, end = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(FaceRing)) {
                    PluginAvatar(picture, profile.displayName, profile.username, FaceSize, onClick = openPicture)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onFilters) { Icon(PostIcons.Tune, contentDescription = pluginString(Strings.filters_and_groups)) }
                ProfileFollowButton(app, profile)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.displayName,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 23.sp, lineHeight = 28.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (profile.isVerified) Icon(XIcons.Verified, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.padding(start = 6.dp).size(20.dp))
                if (profile.isPrivate) Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp).size(18.dp))
            }
            ProfileHandleLine(app, profile.username)
            profile.biography.trim().takeIf { it.isNotEmpty() }?.let { bio ->
                ThreadsRichText(app, bio, profile.facts.bioFragments, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp))
            }
            ProfileLinks(profile)
            val counts = ThreadsProfiles.counts(profile)
            if (counts.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    counts.forEach { (n, kind) ->
                        val list = ThreadsProfileHeader.list(kind)
                        ProfileCount(n, countLabel(kind), ThreadsProfileHeader.isExact(profile, kind), list?.let { { app.host.open(ThreadsRoutes.people(profile.username, it)) } })
                    }
                }
            }
            if (profile.isPrivate) {
                Text(pluginString(Strings.profile_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Threads gives a profile no banner, so its own picture stands in: enlarged,
 * cropped, blurred and darkened; without a picture, a flat surface.
 */
@Composable
private fun ProfileBanner(url: String?) {
    Box(Modifier.fillMaxWidth().height(BannerHeight).clipToBounds().background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }.blur(28.dp),
            )
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)))
        }
    }
}

/**
 * Following here, in the accent: "+ Follow ▾" or "✓ Following ▾", which opens
 * the menu of what the device keeps of the account: Follow, Add to group and
 * Unfollow on this device (after the same question as Home asks). Nothing is
 * sent to Threads. Nothing shows while the accounts followed here are read.
 */
@Composable
private fun ProfileFollowButton(app: ThreadsApp, profile: ThreadsProfile) {
    val rows by app.accounts.state.collectAsState()
    val face = ThreadsProfileView.followFace(rows, profile.username) ?: return
    var menu by remember { mutableStateOf(false) }
    var unfollowing by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val following = face == ThreadsFollowFace.FOLLOWING
    Box {
        Row(
            Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { menu = true }
                .padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (following) Icons.Filled.Check else Icons.Filled.Add, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Text(
                pluginString(if (following) Strings.following_state else Strings.follow),
                color = accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
            Icon(GroupIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp).size(18.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (!following) {
                MenuItem(ThreadsIcons.PersonAdd, pluginString(Strings.follow)) { menu = false; app.followProfile(profile) }
            }
            MenuItem(AddToGroupIcon, addToGroupText()) { menu = false; app.addToGroup(ThreadsAccount.of(profile)) }
            if (following) {
                MenuItem(ThreadsIcons.PersonRemove, pluginString(Strings.unfollow_on_device)) { menu = false; unfollowing = true }
            }
        }
    }
    if (unfollowing) UnfollowDialog(profile.username, onConfirm = { app.unfollow(profile.username) }) { unfollowing = false }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = onClick, leadingIcon = { Icon(icon, contentDescription = null) })
}

/**
 * "@handle · + Note", as the X profile draws it: the reader's private note
 * beside the handle, in the accent's italic, cut to the line, or a muted
 * "+ Note" without one; either opens the editor.
 */
@Composable
internal fun ProfileHandleLine(app: ThreadsApp, handle: String) {
    val notes by app.accountNotes.state.collectAsState()
    val note = notes[ThreadsAccountNotes.key(handle)]
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.accountNotes.load() }
    if (editing) NoteDialog(note.orEmpty(), onSave = { text -> app.launch { app.accountNotes.keep(handle, text) } }) { editing = false }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
    val empty = note.isNullOrEmpty()
    val color = if (empty) muted else MaterialTheme.colorScheme.primary
    val label = if (empty) pluginString(Strings.profile_note_add) else pluginString(Strings.profile_note_title) + ": " + note
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("@$handle", style = style, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Text(" · ", style = style, color = muted)
        Row(
            Modifier
                .weight(1f, fill = false)
                .height(32.dp)
                .clickable(onClickLabel = label, role = Role.Button) { editing = true }
                .semantics(mergeDescendants = true) { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (empty) Icons.Filled.Add else Icons.Filled.Edit, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Text(
                if (empty) pluginString(Strings.profile_note_short) else note.orEmpty(),
                style = style,
                color = color,
                fontStyle = if (empty) FontStyle.Normal else FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** The bar's field while the profile is searched: what is typed narrows the loaded posts at once. */
@Composable
internal fun ProfileSearchField(query: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    val hint = pluginString(Strings.search_profile)
    Box(contentAlignment = Alignment.CenterStart) {
        if (query.isEmpty()) Text(hint, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = hint },
        )
    }
}

/**
 * The tabs across the width, between hairlines: the chosen one in the
 * foreground colour over a 3dp line in the accent, the others muted. Equal
 * while every label fits its share; wider labels take the room they need,
 * and the strip scrolls sideways once they are wider than the screen.
 */
@Composable
internal fun <T> ProfileTabStrip(tabs: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Hairline()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = with(LocalDensity.current) { maxWidth.roundToPx() }
            Layout(
                content = { tabs.forEach { tab -> ProfileTabItem(label(tab), tab == selected) { onSelect(tab) } } },
                modifier = Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
            ) { measurables, constraints ->
                val naturals = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
                val widths = ThreadsProfileView.tabWidths(naturals, width)
                val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixedWidth(widths[i])) }
                val height = placeables.maxOfOrNull { it.height } ?: 0
                layout(widths.sum(), height) {
                    var x = 0
                    placeables.forEach { it.placeRelative(x, 0); x += it.width }
                }
            }
        }
        Hairline()
    }
}

@Composable
private fun ProfileTabItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(48.dp).selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
            fontWeight = FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (selected) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary))
    }
}

/** The round toggles under the tabs: every post, those with media, those with a link; the chosen one on a quiet filled circle. */
@Composable
internal fun ContentToggles(options: List<ThreadsFeedContent>, selected: ThreadsFeedContent, onSelect: (ThreadsFeedContent) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val chosen = option == selected
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (chosen) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                    .selectable(selected = chosen, role = Role.RadioButton) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    contentIcon(option),
                    contentDescription = pluginString(contentLabel(option)),
                    tint = if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Filters and groups: the same toggles named, the groups the account is in, and the profile on threads.com. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ProfileFiltersSheet(
    app: ThreadsApp,
    profile: ThreadsProfile,
    options: List<ThreadsFeedContent>,
    selected: ThreadsFeedContent,
    onChoose: (ThreadsFeedContent) -> Unit,
    onDismiss: () -> Unit,
) {
    val outside = rememberOpenOutside()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(pluginString(Strings.filters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    PluginFilterChip(pluginString(contentLabel(option)), option == selected, onClick = { onChoose(option) })
                }
            }
            PluginMenuRow(AddToGroupIcon, addToGroupText(), onClick = { onDismiss(); app.addToGroup(ThreadsAccount.of(profile)) })
            PluginMenuRow(PostIcons.OpenInNew, pluginString(Strings.open_in_browser), onClick = { onDismiss(); outside("${ThreadsLinks.WEB}/@${profile.username}") })
        }
    }
}

private fun contentIcon(content: ThreadsFeedContent): ImageVector = when (content) {
    ThreadsFeedContent.ALL -> PostIcons.Layers
    ThreadsFeedContent.MEDIA -> PostIcons.Image
    ThreadsFeedContent.LINKS -> XIcons.Link
}

private fun contentLabel(content: ThreadsFeedContent): PluginText = when (content) {
    ThreadsFeedContent.ALL -> Strings.all
    ThreadsFeedContent.MEDIA -> Strings.media
    ThreadsFeedContent.LINKS -> Strings.links
}

private fun countLabel(kind: ThreadsCount): PluginText = when (kind) {
    ThreadsCount.FOLLOWERS -> Strings.followers
    ThreadsCount.FOLLOWING -> Strings.following
    ThreadsCount.POSTS -> Strings.posts
}
