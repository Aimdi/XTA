package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import com.aimdi.xtanative.ui.links.LocalLinks

/**
 * Opens a link outside the app, as XTA's "Open in browser" does: in the
 * browser the reader's link settings name, never in a plugin's screen or the
 * app's own, which [PluginHost.openLink] would choose for a link one of them
 * reads (a plugin's own post, say).
 */
@Composable
fun rememberOpenOutside(): (String) -> Unit {
    val links = LocalLinks.current
    val uri = LocalUriHandler.current
    return remember(links, uri) { { url -> if (links != null) links.openOutside(url) else runCatching { uri.openUri(url) } } }
}

/**
 * Hands a text to the system's share sheet (Android's); where there is none,
 * the desktop's, it is copied instead. The function answers whether it was
 * shared, so a caller can say "Copied" otherwise.
 */
@Composable
fun rememberShareText(): (String) -> Boolean {
    val links = LocalLinks.current
    val clipboard = LocalClipboardManager.current
    return remember(links, clipboard) {
        { text ->
            val shared = links?.share(text) == true
            if (!shared) clipboard.setText(AnnotatedString(text))
            shared
        }
    }
}
