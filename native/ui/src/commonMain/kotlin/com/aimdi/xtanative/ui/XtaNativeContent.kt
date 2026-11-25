package com.aimdi.xtanative.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.aimdi.xtanative.core.net.AppProxy
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.data.LocalPostCache
import com.aimdi.xtanative.data.LocalTranslation
import com.aimdi.xtanative.data.plugins.AppPlugins
import com.aimdi.xtanative.store.crash.CrashIssue
import com.aimdi.xtanative.ui.common.FullPageError
import com.aimdi.xtanative.ui.common.LocalOnline
import com.aimdi.xtanative.ui.common.LocalProfileFilters
import com.aimdi.xtanative.ui.common.LocalSessions
import com.aimdi.xtanative.ui.common.LocalSettingsStore
import com.aimdi.xtanative.ui.common.LocalXClient
import com.aimdi.xtanative.ui.common.SwitchedHapticsFrame
import com.aimdi.xtanative.ui.common.addPlatformPictureDecoders
import com.aimdi.xtanative.ui.common.platformPictureDiskCache
import com.aimdi.xtanative.ui.downloads.DownloadsFrame
import com.aimdi.xtanative.ui.downloads.LocalPlatformFiles
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.app_name
import com.aimdi.xtanative.ui.generated.resources.crash_something_broke
import com.aimdi.xtanative.ui.generated.resources.crash_start_failed
import com.aimdi.xtanative.ui.generated.resources.dismiss
import com.aimdi.xtanative.ui.home.DesktopActionsHost
import com.aimdi.xtanative.ui.home.LocalDesktopActionsHost
import com.aimdi.xtanative.ui.links.LinksFrame
import com.aimdi.xtanative.ui.media.VideoFullscreenFrame
import com.aimdi.xtanative.ui.pluginstore.PluginsFrame
import com.aimdi.xtanative.ui.post.SharedPostPrefs
import com.aimdi.xtanative.ui.saved.LocalKeeper
import com.aimdi.xtanative.ui.settings.trimImageCache
import com.aimdi.xtanative.ui.speech.SpeechBarScaffold
import com.aimdi.xtanative.ui.theme.XtaTheme
import okhttp3.Dispatcher
import org.jetbrains.compose.resources.stringResource

/**
 * The whole app in an Android activity or a desktop window: [graph]'s screens
 * in XTA's theme, their links opening the way XTA opens them and their media
 * saved through the downloads, with the reading-aloud bar under them, a video
 * full screen over them all, and [plugins]' tabs and screens among them.
 * A [window] of the app's own beside its main one starts on its screen.
 */
@Composable
fun XtaNativeContent(graph: AppGraph, plugins: AppPlugins, window: SecondaryWindow? = null) {
    CompositionLocalProvider(
        LocalSecondaryWindow provides window,
        LocalGraph provides graph,
        // The narrow parts, for what reads one or two of them rather than the whole graph.
        LocalSettingsStore provides graph.settings,
        LocalKeeper provides graph.keeper,
        LocalProfileFilters provides graph.profileFilters,
        LocalOnline provides graph.online,
        LocalSessions provides graph.sessions,
        LocalXClient provides graph.client,
        LocalTranslation provides graph.translation,
        LocalPostCache provides graph.posts,
        LocalPlatformFiles provides graph.platformFiles,
        LocalDesktopActionsHost provides remember { DesktopActionsHost() },
    ) {
        XtaTheme { SharedPostPrefs { SwitchedHapticsFrame { LinksFrame { PluginsFrame(plugins) { DownloadsFrame { VideoFullscreenFrame { SpeechBarScaffold { RootOrBroken(graph) } } } } } } } }
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

/**
 * The one image loader of the process, which each shell installs: pictures come over [AppProxy]'s client (the
 * proxy the reader set) with a dispatcher of their own, fade in, the desktop shrinks them smoothly and caches them
 * in the user's own cache folder, and those cached too long ago go.
 */
fun appImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
    .components {
        add(OkHttpNetworkFetcherFactory(callFactory = { pictureClient }))
        addPlatformPictureDecoders()
    }
    .apply { platformPictureDiskCache()?.let { diskCache(it) } }
    .crossfade(true)
    .build()
    .also(::trimImageCache)

/** The pictures' client: [AppProxy]'s, built when the first picture loads. */
private val pictureClient by lazy { AppProxy.client.newBuilder().dispatcher(Dispatcher()).build() }
