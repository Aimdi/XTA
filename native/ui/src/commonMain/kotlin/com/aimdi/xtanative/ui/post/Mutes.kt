package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.feed.MuteList
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.LocalSettingsStore
import com.aimdi.xtanative.ui.common.resizing
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_fold_hide_again
import com.aimdi.xtanative.ui.generated.resources.posts_muted_reason
import com.aimdi.xtanative.ui.generated.resources.posts_muted_show
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import com.aimdi.xtanative.ui.saved.Keeper
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import java.time.Instant
import org.jetbrains.compose.resources.stringResource

/** The mute list [SharedPostPrefs] read once for every card under it; null outside it. */
internal val LocalMuteList = compositionLocalOf<MuteList?> { null }

/** The app-wide mute list ([MuteList]) the reader keeps, kept and changed on this device only. */
internal object Mutes {
    fun of(settings: Settings?): MuteList = settings?.let { MuteList.parse(it[PostsPrefs.muteList]) } ?: MuteList()

    /**
     * The mute list changed by [edit], written to its end whatever happens to
     * the menu that asked, with an Undo when [offerUndo] (a mute from a post's
     * menu, which folds the post away under the pointer).
     */
    fun change(keeper: Keeper, offerUndo: Boolean = false, edit: (MuteList) -> MuteList) = keeper.keep {
        var before = ""
        keeper.settings.update { settings ->
            before = settings[PostsPrefs.muteList]
            settings.with(PostsPrefs.muteList, edit(MuteList.parse(before)).encode())
        }
        if (offerUndo) {
            val old = before
            KeepNotices.show(KeepNotice.ChangesSaved(undo = { keeper.settings.set(PostsPrefs.muteList, old); true }))
        }
    }

    /**
     * Why [thread] folds where it is drawn in [scope]: not when it holds the
     * opened post, which the reader asked for, and not by the mute of the
     * profile on screen, whose posts are what the reader came for.
     */
    fun reason(mutes: MuteList, thread: XThread, scope: PostScope, now: Instant = Instant.now()): String? {
        if (mutes.isEmpty) return null
        val focal = scope.focalId
        if (focal != null && thread.posts.any { it.id == focal || it.shown.id == focal }) return null
        return mutes.reason(thread, now, exceptHandle = scope.profileScreenName)
    }
}

/** Why a plugin's [post] from [network] folds in a host's timeline: a muted account of its network, or a muted word in what it shows. */
internal fun Mutes.reason(mutes: MuteList, network: String, post: NetworkPost, now: Instant = Instant.now()): String? {
    if (mutes.isEmpty) return null
    val text = listOfNotNull(post.title, post.text.joinToString("") { it.text }, post.card?.title, post.quoted?.text?.joinToString("") { it.text }).joinToString(" ")
    return mutes.reason(network, post.authorHandle, post.authorId, text, now)
}

/** The reader's mute list as it changes: [SharedPostPrefs]'s, else read here. */
@Composable
internal fun muteList(): MuteList {
    LocalMuteList.current?.let { return it }
    val settings = settingsNow(LocalSettingsStore.current)
    val raw = settings?.get(PostsPrefs.muteList)
    return remember(raw) { MuteList.parse(raw) }
}

/**
 * A muted post in a list: a small row saying why, which shows [content] on a
 * click and folds it again on the next, so a mute never makes a post vanish
 * without a trace. [key] keeps it open while the list scrolls.
 */
@Composable
internal fun MutedFold(key: String, reason: String, content: @Composable () -> Unit) {
    var open by rememberSaveable(key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().resizing(LocalReducedMotion.current)) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        Row(
            Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { open = !open }.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (open) GroupIcons.ExpandLess else GroupIcons.VisibilityOffOutlined, contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (open) stringResource(Res.string.groups_fold_hide_again) else stringResource(Res.string.posts_muted_reason, reason),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!open) Text(stringResource(Res.string.posts_muted_show), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (open) content() else Hairline()
    }
}
