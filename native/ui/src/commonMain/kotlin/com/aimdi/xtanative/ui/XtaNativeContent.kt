package com.aimdi.xtanative.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.crossfade
import com.aimdi.xtanative.ui.common.addPlatformPictureDecoders
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.plugins.AppPlugins
import com.aimdi.xtanative.ui.common.FullPageError
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.SwitchedHapticsFrame
import com.aimdi.xtanative.ui.downloads.DownloadsFrame
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.app_name
import com.aimdi.xtanative.ui.generated.resources.crash_something_broke
import com.aimdi.xtanative.ui.generated.resources.crash_start_failed
import com.aimdi.xtanative.ui.generated.resources.dismiss
import com.aimdi.xtanative.ui.links.LinksFrame
import com.aimdi.xtanative.ui.media.VideoFullscreenFrame
import com.aimdi.xtanative.ui.pluginstore.PluginsFrame
import com.aimdi.xtanative.ui.settings.CrashIssue
import com.aimdi.xtanative.ui.settings.trimImageCache
import com.aimdi.xtanative.ui.speech.SpeechBarScaffold
import com.aimdi.xtanative.ui.theme.XtaTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The whole app in an Android activity or a desktop window: [graph]'s screens
 * in XTA's theme, their links opening the way XTA opens them and their media
 * saved through the downloads, with the reading-aloud bar under them, a video
 * full screen over them all, and [plugins]' tabs and screens among them.
 */
@Composable
fun XtaNativeContent(graph: AppGraph, plugins: AppPlugins) {
    CompositionLocalProvider(LocalGraph provides graph) {
        XtaTheme { SwitchedHapticsFrame { LinksFrame { PluginsFrame(plugins) { DownloadsFrame { VideoFullscreenFrame { SpeechBarScaffold { RootOrBroken(graph) } } } } } } }
    }
}

/**
 * The screens, unless the device's data could not be opened (XTA's failed
 * database migrations), or a screen broke when the app last ran: then what
 * broke, as XTA's error screens show it, the latter until dismissed.
 */
@Composable
private fun RootOrBroken(graph: AppGraph) {
    val failure by graph.startFailure.collectAsState()
    val crash by graph.lastCrash.collectAsState()
    val failed = failure
    val broke = crash
    when {
        failed != null -> FullPageError(stringResource(Res.string.crash_start_failed), failed.toString(), failed.stackTraceToString())
        broke != null -> {
            val issue = remember(broke) { CrashIssue.of(broke.text) }
            FullPageError(
                stringResource(Res.string.crash_something_broke),
                issue?.error ?: broke.text,
                issue?.stack,
                stringResource(Res.string.dismiss) to graph::dismissLastCrash,
            )
        }
        else -> XtaNativeRoot()
    }
}

/** "XTA Native", for a window's title. */
@Composable
fun appName(): String = stringResource(Res.string.app_name)

/** The one image loader of the process, which each shell installs: pictures fade in, the desktop shrinks them smoothly, and those cached too long ago go. */
fun appImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
    .components { addPlatformPictureDecoders() }
    .crossfade(true)
    .build()
    .also(::trimImageCache)
