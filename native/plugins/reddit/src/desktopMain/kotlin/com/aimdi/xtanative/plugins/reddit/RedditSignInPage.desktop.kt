package com.aimdi.xtanative.plugins.reddit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.pluginString

/** The desktop has no web view to watch Reddit's sign-in in: it says so, as XTA's desktop does. */
@Composable
internal actual fun RedditSignInPage(url: String, modifier: Modifier, onAddress: (String) -> Boolean) {
    MessageText(pluginString(Strings.sign_in_needs_android), modifier)
}
