package com.aimdi.xtanative.ui.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.ui.media.LocalMediaSaveAs
import com.aimdi.xtanative.ui.media.LocalMediaSaver
import com.aimdi.xtanative.ui.media.MediaSaver

/** What the downloads give the screens in [content]: the media viewer's Save and Save as…, said at the foot of the window as every download is. */
@Composable
fun DownloadsFrame(content: @Composable () -> Unit) {
    val graph = LocalGraph.current
    val saver = remember(graph) { Downloading.saver(graph) }
    val saveAs = remember(graph) { MediaSaver { media -> Downloading.saveAs(graph, media.url, media.fileName) } }
    CompositionLocalProvider(LocalMediaSaver provides saver, LocalMediaSaveAs provides saveAs, content = content)
}
