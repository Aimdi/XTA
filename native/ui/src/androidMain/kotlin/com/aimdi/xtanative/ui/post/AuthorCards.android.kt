package com.aimdi.xtanative.ui.post

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.core.x.model.XUser

@Composable
internal actual fun AuthorHoverCard(user: XUser, avatarUrl: String?, onOpen: () -> Unit, content: @Composable () -> Unit) = content()
