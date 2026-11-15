package com.aimdi.xtanative.data

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The app graph, for the app's own screens and view models. Shared parts a
 * plugin may draw read narrower locals instead ([com.aimdi.xtanative.ui.common.LocalSettingsStore]),
 * so the plugin API does not reach the app through it.
 */
val LocalGraph = staticCompositionLocalOf<AppGraph> { error("No AppGraph provided") }
