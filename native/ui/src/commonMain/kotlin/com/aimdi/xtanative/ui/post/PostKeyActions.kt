package com.aimdi.xtanative.ui.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.ui.common.rememberCopyText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_link_copied
import com.aimdi.xtanative.ui.home.PostKeys
import com.aimdi.xtanative.ui.saved.KeepNotices
import com.aimdi.xtanative.ui.saved.LocalKeeper
import org.jetbrains.compose.resources.stringResource

/**
 * Whether the ⋯ menu drawn here was opened from the keyboard (the Menu key),
 * so it stands at the post rather than at the pointer ([PostMenuSurface]).
 */
internal val LocalMenuAtPost = staticCompositionLocalOf { false }

/**
 * What the desktop's keys do to [post] as a feed row: open it as a click
 * does, like or save it on this device as its heart and bookmark do, copy
 * its link, and open its ⋯ menu ([openMenu]). The post opened already (the
 * focal one) is not [openable]: Enter and o leave it as it is.
 */
@Composable
internal fun rememberPostKeys(post: XPost, actions: PostActions, openable: Boolean = true, openMenu: () -> Unit): PostKeys {
    val keeper = LocalKeeper.current
    val shown = post.shown
    val url = PostView.url(post, postScope().prefs)
    val copy = rememberCopyText()
    val copied = stringResource(Res.string.reader_link_copied)
    return PostKeys(
        open = { if (openable) actions.openPost(shown.id) },
        like = { actions.keep.toggleLike(keeper, shown) },
        save = { actions.keep.toggleSave(keeper, shown) },
        copyLink = {
            copy(url)
            KeepNotices.say(copied)
        },
        menu = openMenu,
    )
}
