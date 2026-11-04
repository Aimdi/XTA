package com.aimdi.xtanative

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.onlineFlow
import com.aimdi.xtanative.ui.appImageLoader
import com.aimdi.xtanative.ui.downloads.AndroidFiles

/**
 * The app graph on Android: the app's private files, the phone's connection,
 * the stand-in for X in a -PmockX build, and MediaStore for downloads.
 */
class XtaNativeApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(
            filesDir,
            onlineFlow(this),
            BuildConfig.VERSION_NAME,
            mockX = BuildConfig.MOCK_X.ifEmpty { null },
            platformFiles = AndroidFiles(this),
        )
        graph.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = appImageLoader(context)
}
