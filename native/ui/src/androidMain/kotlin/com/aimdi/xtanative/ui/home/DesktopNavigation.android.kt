package com.aimdi.xtanative.ui.home

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

/** Android goes back with its own gesture and button: the content alone. */
@Composable
actual fun DesktopNavigation(nav: NavHostController, content: @Composable () -> Unit) {
    content()
}
