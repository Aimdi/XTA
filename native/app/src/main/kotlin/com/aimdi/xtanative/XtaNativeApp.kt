package com.aimdi.xtanative

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.onlineFlow
import com.aimdi.xtanative.data.plugins.AppPlugins
import com.aimdi.xtanative.plugins.androidPlugins
import com.aimdi.xtanative.ui.appImageLoader
import com.aimdi.xtanative.ui.downloads.AndroidFiles
import com.aimdi.xtanative.ui.speech.VoiceSources

/**
 * The app graph on Android: the app's private files, the phone's connection,
 * the stand-in for X in a -PmockX build, and MediaStore for downloads; beside
 * it the plugins, kept in the private files' plugins/, offered by the
 * published catalogue or the one a -PpluginCatalogue build names. Where
 * voices download from is this build's: its prerelease's, or the server a
 * -PvoiceServer build names.
 */
class XtaNativeApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set
    lateinit var plugins: AppPlugins
        private set

    override fun onCreate() {
        super.onCreate()
        System.setProperty(VoiceSources.RELEASE_TAG_PROPERTY, BuildConfig.RELEASE_TAG)
        if (BuildConfig.VOICE_SERVER.isNotEmpty()) System.setProperty(VoiceSources.SERVER_PROPERTY, BuildConfig.VOICE_SERVER)
        graph = AppGraph(
            filesDir,
            onlineFlow(this),
            BuildConfig.VERSION_NAME,
            mockX = BuildConfig.MOCK_X.ifEmpty { null },
            platformFiles = AndroidFiles(this),
        )
        val catalogue = BuildConfig.PLUGIN_CATALOGUE.ifEmpty { null }
        plugins = if (catalogue != null) AppPlugins(filesDir, graph, androidPlugins(this), catalogue) else AppPlugins(filesDir, graph, androidPlugins(this))
        graph.start()
        plugins.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = appImageLoader(context)
}
