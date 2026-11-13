package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The desktop has no web view: every article reads in the native reader, and a live page opens in the browser. */
internal actual val articleWebView: Boolean = false

/** Never shown, as [articleWebView] is false here. */
@Composable
internal actual fun SubstackWebView(page: WebPage, speakLabel: String, control: WebPageControl, events: WebPageEvents, modifier: Modifier) {
    Box(modifier)
}
