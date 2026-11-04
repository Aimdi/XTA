package com.aimdi.xtanative.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.crossfade
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.app_name
import com.aimdi.xtanative.ui.links.LinksFrame
import com.aimdi.xtanative.ui.speech.SpeechBarScaffold
import com.aimdi.xtanative.ui.theme.XtaTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The whole app in an Android activity or a desktop window: [graph]'s screens
 * in XTA's theme, their links opening the way XTA opens them, with the
 * reading-aloud bar under them.
 */
@Composable
fun XtaNativeContent(graph: AppGraph) {
    CompositionLocalProvider(LocalGraph provides graph) {
        XtaTheme { LinksFrame { SpeechBarScaffold { XtaNativeRoot() } } }
    }
}

/** "XTA Native", for a window's title. */
@Composable
fun appName(): String = stringResource(Res.string.app_name)

/** The one image loader of the process, which each shell installs: pictures fade in. */
fun appImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context).crossfade(true).build()
