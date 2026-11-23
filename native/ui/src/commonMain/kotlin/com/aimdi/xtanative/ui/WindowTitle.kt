package com.aimdi.xtanative.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

/**
 * Names the screen [nav] shows in the window's title, where the platform's
 * window has one (the desktop's); nothing on Android. Called from the app's
 * own navigation, which the plugin API never reaches.
 */
@Composable
internal expect fun WindowTitleFollows(nav: NavHostController)
