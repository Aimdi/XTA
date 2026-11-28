package com.aimdi.xtanative.ui.post

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.aimdi.xtanative.core.x.model.XPost

// A touch drag scrolls on Android; links are shared from the post's menu there.
internal actual fun Modifier.dragsLink(url: String?): Modifier = this

internal actual fun Modifier.dragsLinks(linkAt: (Offset) -> String?): Modifier = this

internal actual fun Modifier.dragsPost(post: XPost, url: String?): Modifier = this

@Composable
internal actual fun Modifier.takesPostDrops(onPost: (XPost) -> Unit): Modifier = this
