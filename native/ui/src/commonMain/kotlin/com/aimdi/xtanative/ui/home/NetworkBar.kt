package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_switch_account
import com.aimdi.xtanative.ui.generated.resources.home_switcher_title
import com.aimdi.xtanative.ui.generated.resources.home_unread
import com.aimdi.xtanative.ui.generated.resources.home_unread_count
import com.aimdi.xtanative.ui.generated.resources.posts_scroll_to_top
import com.aimdi.xtanative.ui.plugins.PluginClientBar
import com.aimdi.xtanative.ui.plugins.ShellFeedRow
import com.aimdi.xtanative.ui.plugins.color
import com.aimdi.xtanative.ui.plugins.pluginAccent
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/** The shell's first row: the plugin's avatar, its name and subtitle, its search. */
private val TITLE_ROW = 52.dp

/** How much of the accent the plugin's avatar is filled with. */
private const val DISC_ALPHA = 0.16f

/** What the network's bar does: open the plugin sheet (its avatar) and go back to the top (its title). */
internal class NetworkBarCalls(val onAvatar: () -> Unit, val onTitle: () -> Unit)

/**
 * A network's timeline's header on Home (the unified plugin shell, option
 * A): two rows that hide together as the posts scroll up and come back on
 * any scroll down. The first, 52 dp: the plugin's avatar (its mark on a
 * tinted disc, a dot while it has unread posts), which opens the plugin
 * sheet; its name centred over its subtitle ([ShellSubtitle]), which goes
 * back to the top; then its search ([actions], the plugin's and the
 * app's). The second, 44 dp: the plugin's feeds ([ShellFeeds]) with its
 * filter, or, where it hands none, its sections when they are not on the
 * bottom bar ([sectionsBelow]); a hairline under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetworkTopBar(
    id: String,
    ui: HomeUi,
    networks: HomeNetworks,
    client: PluginClientBar,
    anyUnread: Boolean,
    calls: NetworkBarCalls,
    bar: TopAppBarScrollBehavior,
    sectionsBelow: Boolean = false,
    actions: @Composable RowScope.() -> Unit,
) {
    val content = client.content
    val accent = pluginAccent(content?.accent?.value)
    val handed = client.feeds
    val tabs = content?.tabs?.value.orEmpty()
    val feeds = handed?.feeds ?: tabs.takeIf { it.size > 1 && !sectionsBelow }
    Column(Modifier.background(MaterialTheme.colorScheme.background).windowInsetsPadding(TopAppBarDefaults.windowInsets)) {
        Collapsing(bar) {
            Column {
                TitleRow(id, ui, networks, client, anyUnread, calls) {
                    content?.actions?.value?.invoke(this)
                    actions()
                }
                if (feeds != null) ShellFeedRow(feeds, handed?.filter, accent) else Hairline()
            }
        }
    }
}

/**
 * [content] drawn whole while [bar] is expanded, and moved up out of sight
 * as the list under it scrolls (the TopAppBar's own enterAlways), so the
 * header's rows hide and return together.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Collapsing(bar: TopAppBarScrollBehavior, content: @Composable () -> Unit) {
    var full by remember { mutableIntStateOf(0) }
    SideEffect { if (full > 0 && bar.state.heightOffsetLimit != -full.toFloat()) bar.state.heightOffsetLimit = -full.toFloat() }
    Layout(content, Modifier.clipToBounds()) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        if (full != placeable.height) full = placeable.height
        val offset = bar.state.heightOffset.roundToInt().coerceIn(-placeable.height, 0)
        layout(placeable.width, placeable.height + offset) { placeable.place(0, offset) }
    }
}

/** The first row: avatar, centred name and subtitle, the actions. */
@Composable
private fun TitleRow(id: String, ui: HomeUi, networks: HomeNetworks, client: PluginClientBar, anyUnread: Boolean, calls: NetworkBarCalls, actions: @Composable RowScope.() -> Unit) {
    val name = networkName(id, ui)
    val subtitle = client.subtitle
    Box(Modifier.fillMaxWidth().height(TITLE_ROW)) {
        Avatar(id, name, networks, AvatarMark(anyUnread, client.unread), calls.onAvatar, client.switchAccount, Modifier.align(Alignment.CenterStart).padding(start = 6.dp))
        Column(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 220.dp)
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClickLabel = stringResource(Res.string.posts_scroll_to_top), onClick = calls.onTitle)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    subtitle.status.color()?.let { Box(Modifier.size(6.dp).background(it, CircleShape)) }
                    Text(subtitle.text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.align(Alignment.CenterEnd).padding(end = 2.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** What the avatar carries: the plugin's own unread count where it says one ([count]), else a dot while Home has [unread] posts for it. */
internal class AvatarMark(val unread: Boolean, val count: Int?) {
    /** The badge's text: the count, 99+ past that; null for a dot or nothing. */
    val badge: String? get() = count?.takeIf { it > 0 }?.let { if (it > MAX_BADGE) "$MAX_BADGE+" else "$it" }

    /** Whether a dot stands in for a count the plugin does not give. */
    val dot: Boolean get() = count == null && unread

    companion object {
        const val MAX_BADGE = 99
    }
}

/**
 * The plugin's avatar: its mark on a disc tinted with the accent, ringed
 * with the outline, with its unread count ([AvatarMark]) at its corner; a
 * tap opens the plugin sheet ([PluginSwitcherSheet]), a long press the
 * plugin's account switcher where it has one ([onAccounts]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Avatar(id: String, name: String, networks: HomeNetworks, mark: AvatarMark, onClick: () -> Unit, onAccounts: (() -> Unit)?, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val count = mark.count?.takeIf { it > 0 }?.let { stringResource(Res.string.home_unread_count, it) }
    val label = listOfNotNull(name, count ?: stringResource(Res.string.home_unread).takeIf { mark.dot }, stringResource(Res.string.home_switcher_title)).joinToString(", ")
    val switchLabel = stringResource(Res.string.home_switch_account)
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onAccounts, onLongClickLabel = switchLabel.takeIf { onAccounts != null })
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                if (onAccounts != null) customActions = listOf(CustomAccessibilityAction(switchLabel) { onAccounts(); true })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(32.dp).background(accent.copy(alpha = DISC_ALPHA), CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            networks.Mark(id, 17.dp)
        }
        val badge = mark.badge
        when {
            badge != null -> Text(
                badge,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .background(accent, CircleShape)
                    .defaultMinSize(minWidth = 16.dp)
                    .padding(horizontal = 4.dp),
            )
            mark.dot -> Box(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 3.dp).size(10.dp).background(MaterialTheme.colorScheme.background, CircleShape).padding(2.dp).background(accent, CircleShape))
        }
    }
}

private fun networkName(id: String, ui: HomeUi): String = ui.networks.firstOrNull { it.id == id }?.title ?: id
