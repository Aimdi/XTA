package com.aimdi.xtanative.ui.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.services.ai.TranslationStatus
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
 * its link, and open its ⋯ menu ([openMenu]); open its author's profile, it
 * in a window of its own, its first picture or video, and translate it
 * ([translation], none where it is offered no translation). The post opened
 * already (the focal one) is not [openable]: Enter and o leave it as it is.
 */
@Composable
internal fun rememberPostKeys(
    post: XPost,
    actions: PostActions,
    openable: Boolean = true,
    translation: PostTranslation? = null,
    openMenu: () -> Unit,
): PostKeys {
    val keeper = LocalKeeper.current
    val shown = post.shown
    val scope = postScope()
    val url = PostView.url(post, scope.prefs)
    val copy = rememberCopyText()
    val copied = stringResource(Res.string.reader_link_copied)
    val windows = LocalWindowOpens.current
    return PostKeys(
        open = { if (openable) actions.openPost(shown.id) },
        like = { actions.keep.toggleLike(keeper, shown) },
        save = { actions.keep.toggleSave(keeper, shown) },
        copyLink = {
            copy(url)
            KeepNotices.say(copied)
        },
        menu = openMenu,
        author = { actions.openUser(shown.author.screenName, shown.author.id) },
        inWindow = { windows?.post?.invoke(shown.id) },
        media = { PostKeyRules.mediaIndex(shown, scope.sensitive.hidden)?.let { actions.openMedia(shown.id, it) } },
        translate = { translation?.let(PostKeyRules::toggle) },
    )
}

/** What the desktop's post keys decide apart from the screen ([rememberPostKeys]). */
internal object PostKeyRules {
    /** The picture or video m and v open in the viewer: the post's first, none while its media is hidden behind the sensitive gate. */
    fun mediaIndex(post: XPost, sensitiveHidden: Boolean): Int? = 0.takeIf { post.media.isNotEmpty() && !(post.sensitive && sensitiveHidden) }

    /** t on a post: translated back to its own words, else translated, as its button does; a translation under way is left to finish. */
    fun toggle(translation: PostTranslation) {
        when (translation.status) {
            TranslationStatus.TRANSLATED -> translation.showOriginal()
            TranslationStatus.TRANSLATING -> Unit
            TranslationStatus.ORIGINAL, TranslationStatus.FAILED -> translation.translate()
        }
    }
}
