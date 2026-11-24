package com.aimdi.xtanative.ui.post

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

// A touch drag scrolls on Android; links are shared from the post's menu there.
internal actual fun Modifier.dragsLink(url: String?): Modifier = this

internal actual fun Modifier.dragsLinks(linkAt: (Offset) -> String?): Modifier = this
