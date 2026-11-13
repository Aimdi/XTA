package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable

/** The desktop opens no links of its own on the system's behalf: there is no such page to send the reader to. */
@Composable
actual fun rememberAppLinkSettings(): (() -> Unit)? = null
