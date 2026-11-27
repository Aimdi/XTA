package com.aimdi.xtanative.ui.post

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import com.aimdi.xtanative.ui.common.onSecondaryClick
import com.aimdi.xtanative.ui.common.onSecondaryClickAt
import com.aimdi.xtanative.ui.common.rememberCopyText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.plugin_open
import com.aimdi.xtanative.ui.generated.resources.posts_open_in_browser
import com.aimdi.xtanative.ui.generated.resources.posts_open_in_new_window
import com.aimdi.xtanative.ui.generated.resources.reader_link_copied
import com.aimdi.xtanative.ui.generated.resources.reader_link_copy
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.saved.KeepNotices
import org.jetbrains.compose.resources.stringResource

/** Which links of a post's text a right-click opens a menu for: what has a web address ([MiddleClickLinks]), not a contract address. */
internal object LinkMenus {
    fun opensFor(tag: String?): Boolean = MiddleClickLinks.webAddressOf(tag) != null
}

/**
 * A link's menu, as a browser's: [open] it as a click does, Open in new
 * window where the app has a screen for it ([LocalWindowOpens]), Open in
 * browser at its web address, and Copy link, the address as it leaves the
 * app (cleaned of tracking unless the reader turned that off). [tag] is the
 * link's (an address, `@name`, `#tag` or `$SYMBOL`). It shows where a post's
 * menu shows, beside the pointer on the desktop.
 */
@Composable
internal fun LinkMenu(tag: String, open: () -> Unit, onDismiss: () -> Unit) {
    val windows = LocalWindowOpens.current
    val links = LocalLinks.current
    val outside = rememberOpenOutside()
    val copy = rememberCopyText()
    val copied = stringResource(Res.string.reader_link_copied)
    val address = MiddleClickLinks.webAddressOf(tag)
    val inWindow = remember(windows, tag) { windows?.link?.invoke(tag) }
    val then = { action: () -> Unit -> { onDismiss(); action() } }
    PostMenuSurface(onDismiss) {
        MenuRow(Icons.AutoMirrored.Filled.ArrowForward, stringResource(Res.string.plugin_open), then(open))
        inWindow?.let { MenuRow(NewWindowIcon, stringResource(Res.string.posts_open_in_new_window), then(it)) }
        if (address != null) {
            MenuGroupDivider()
            MenuRow(PostIcons.OpenInNew, stringResource(Res.string.posts_open_in_browser), then { outside(address) })
            MenuRow(
                PostIcons.ContentCopy,
                stringResource(Res.string.reader_link_copy),
                then {
                    copy(links?.prepare(address) ?: address)
                    KeepNotices.say(copied)
                },
            )
        }
    }
}

/** The link of a post's text whose menu is open, if any ([linkMenus]), drawn by [Shown]. */
internal class LinkMenuState {
    var link by mutableStateOf<LinkAnnotation.Clickable?>(null)

    @Composable
    fun Shown() {
        val shown = link ?: return
        LinkMenu(shown.tag, open = { shown.linkInteractionListener?.onClick(shown) }) { link = null }
    }
}

/**
 * A right-click on a link, mention or tag of [text], laid out as [layout]
 * holds it, opens its menu in [menu] rather than the post's; a right-click
 * off one is left to the post around it.
 */
@Composable
internal fun Modifier.linkMenus(text: AnnotatedString, layout: TextLayoutHolder, menu: LinkMenuState): Modifier {
    val any = remember(text) { text.getLinkAnnotations(0, text.length).any { LinkMenus.opensFor((it.item as? LinkAnnotation.Clickable)?.tag) } }
    if (!any) return this
    val onLink = { position: Offset -> linkAt(text, layout.result, position, LinkMenus::opensFor) }
    return onSecondaryClickAt(accepts = { onLink(it) != null }) { position -> onLink(position)?.let { menu.link = it } }
        // Taken, so a selectable text's own menu (Copy, Select all) does not open over the link's.
        .pointerInput(text, layout) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val press = event.changes.firstOrNull() ?: continue
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed && onLink(press.position) != null) press.consume()
                }
            }
        }
}

/** A right-click anywhere on this card of a link to [url] opens the link's menu, [open] opening it as a click does; nothing without one. */
@Composable
internal fun Modifier.cardLinkMenu(url: String?, open: () -> Unit): Modifier {
    if (url == null || !LinkMenus.opensFor(url)) return this
    var shown by remember(url) { mutableStateOf(false) }
    if (shown) LinkMenu(url, open) { shown = false }
    return onSecondaryClick { shown = true }
}
