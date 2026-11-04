package com.aimdi.xtanative.plugins.websources

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.websources.WebSourceDefinition
import com.aimdi.xtanative.networks.websources.WebSourceItem
import com.aimdi.xtanative.networks.websources.WebSourceOpening
import com.aimdi.xtanative.networks.websources.WebSourceReading
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How [source]'s posts open, as XTA's openWebSourceItem: its full text in
 * the RSS reader when the post carries it or the source's article rule says
 * where it is (read behind a dialog, which closing gives up on), else its
 * link, else its discussion. A post's pictures open in the viewer.
 */
@Composable
internal fun rememberItemActions(app: WebSourcesApp, source: WebSourceDefinition): ItemActions {
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf<Pair<Job, WebSourceItem>?>(null) }
    reading?.let { (job, item) ->
        ReadingDialog {
            job.cancel()
            reading = null
            WebSourceReading.address(item)?.let(app.host::openLink)
        }
    }
    return remember(app, source) {
        ItemActions(
            open = { item ->
                when (val opening = WebSourceReading.opening(source, item)) {
                    is WebSourceOpening.Reader -> app.opened(source, item, opening.html)
                    is WebSourceOpening.Address -> app.opened(source, item, null)
                    WebSourceOpening.Article -> {
                        val job = scope.launch {
                            val fetched = app.article(source, item)
                            reading = null
                            app.opened(source, item, fetched)
                        }
                        reading = job to item
                    }
                }
            },
            openLink = app.host::openLink,
            openPictures = { item, index -> app.host.openMedia(item.pictures.map { MediaItem(imageUrl = it, alt = item.title) }, index) },
            more = { item -> app.host.openPostMenu(app.shared.menu(source, item)) },
        )
    }
}

/** "Reading the source…", while a source's file or an article is read; closing it gives up. */
@Composable
internal fun ReadingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Spacer(Modifier.width(16.dp))
                Text(pluginString(Strings.reading))
            }
        },
        confirmButton = {},
    )
}
