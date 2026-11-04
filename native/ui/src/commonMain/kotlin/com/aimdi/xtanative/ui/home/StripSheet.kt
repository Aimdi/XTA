package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_add_timeline
import com.aimdi.xtanative.ui.generated.resources.home_plugin_store
import com.aimdi.xtanative.ui.generated.resources.home_strip_available
import com.aimdi.xtanative.ui.generated.resources.home_strip_empty
import com.aimdi.xtanative.ui.generated.resources.home_strip_intro
import com.aimdi.xtanative.ui.generated.resources.home_strip_pinned
import com.aimdi.xtanative.ui.generated.resources.home_strip_remove
import com.aimdi.xtanative.ui.generated.resources.home_strip_reorder
import com.aimdi.xtanative.ui.generated.resources.home_strip_title
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import com.aimdi.xtanative.ui.subscriptions.rememberRowDrag
import org.jetbrains.compose.resources.stringResource

/**
 * Which plugin timelines sit next to For you (XTA's feed strip add sheet):
 * those on Home, to open, take off or drag into another order by their
 * handle, then those that could be added. With no plugin installed, the way
 * to the plugin store.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StripSheet(
    ui: HomeUi,
    networks: HomeNetworks,
    routes: HomeRoutes,
    onOpen: (String) -> Unit,
    onPin: (String) -> Unit,
    onUnpin: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pinned = StripPins.pinned(ui.pins, ui.networks)
    val candidates = StripPins.candidates(ui.pins, ui.networks)
    val list = rememberLazyListState()
    val drag = rememberRowDrag(list)
    val byId = pinned.associateBy { it.id }
    val keys: List<Any> = pinned.map { it.id }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            item(key = "title") {
                Column {
                    Text(stringResource(Res.string.home_strip_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
                    Text(
                        stringResource(Res.string.home_strip_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
                    )
                }
            }
            if (pinned.isEmpty() && candidates.isEmpty()) {
                item(key = "empty") {
                    NoPlugins(routes.exists(HomeDestination.PLUGINS.route)) {
                        onDismiss()
                        routes.openIfExists(HomeDestination.PLUGINS.route)
                    }
                }
            }
            if (pinned.isNotEmpty()) item(key = "pinned") { Section(stringResource(Res.string.home_strip_pinned)) }
            items(drag.shown(keys), key = { it }) { key ->
                val network = byId[key] ?: return@items
                val dragged = drag.key == key
                Box(Modifier.graphicsLayer { translationY = if (dragged) drag.offset else 0f }.let { if (dragged) it.background(MaterialTheme.colorScheme.surfaceContainerHigh) else it }) {
                    NetworkRow(network, networks, onClick = { onOpen(network.id) }) {
                        IconButton(onClick = { onUnpin(network.id) }) { Icon(HomeIcons.RemoveCircle, stringResource(Res.string.home_strip_remove)) }
                        DragHandle(
                            Modifier.pointerInput(key, keys) {
                                detectDragGestures(
                                    onDragStart = { drag.start(key, keys) },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        drag.drag(amount.y)
                                    },
                                    onDragEnd = { drag.end()?.let { (from, to) -> onMove(from, to) } },
                                    onDragCancel = { drag.end() },
                                )
                            },
                        )
                    }
                }
            }
            if (candidates.isNotEmpty()) {
                item(key = "available") {
                    Column {
                        if (pinned.isNotEmpty()) Hairline(Modifier.padding(vertical = 4.dp))
                        Section(stringResource(Res.string.home_strip_available))
                    }
                }
                items(candidates, key = { "add-" + it.id }) { network ->
                    // Adding a timeline opens it, as in XTA.
                    val add = {
                        onPin(network.id)
                        onOpen(network.id)
                    }
                    NetworkRow(network, networks, onClick = add) {
                        IconButton(onClick = add) { Icon(Icons.Filled.AddCircle, stringResource(Res.string.home_add_timeline)) }
                    }
                }
            }
        }
    }
}

/** The handle a pinned timeline is dragged by, a 48 dp target: "Reorder". */
@Composable
private fun DragHandle(modifier: Modifier) {
    val label = stringResource(Res.string.home_strip_reorder)
    Box(modifier.size(48.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(GroupIcons.DragHandle, contentDescription = null)
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun NetworkRow(network: StripNetwork, networks: HomeNetworks, onClick: () -> Unit, controls: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { networks.Mark(network.id, 24.dp) }
        Spacer(Modifier.width(16.dp))
        Text(network.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        controls()
    }
}

/** No plugin to pin yet: what to install them from. */
@Composable
private fun NoPlugins(store: Boolean, onStore: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(Res.string.home_strip_empty), textAlign = TextAlign.Center)
        if (store) {
            Button(onClick = onStore) {
                Icon(HomeIcons.Extension, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.home_plugin_store))
            }
        }
    }
}
