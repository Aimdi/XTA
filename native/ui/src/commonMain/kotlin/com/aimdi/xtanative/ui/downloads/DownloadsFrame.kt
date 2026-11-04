package com.aimdi.xtanative.ui.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.media.LocalMediaSaver

/** What the downloads give the screens in [content]: the media viewer's Save, said at the foot of the window as every download is. */
@Composable
fun DownloadsFrame(content: @Composable () -> Unit) {
    val graph = LocalGraph.current
    val saver = remember(graph) { Downloading.saver(graph) }
    CompositionLocalProvider(LocalMediaSaver provides saver, content = content)
}
