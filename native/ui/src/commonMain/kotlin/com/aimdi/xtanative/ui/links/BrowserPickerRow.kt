package com.aimdi.xtanative.ui.links

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.services.links.BrowserPicker
import com.aimdi.xtanative.services.links.InstalledBrowser
import com.aimdi.xtanative.services.links.LinkDestination
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_links_in_app
import com.aimdi.xtanative.ui.generated.resources.reader_links_in_app_description
import com.aimdi.xtanative.ui.generated.resources.reader_links_open_in
import com.aimdi.xtanative.ui.generated.resources.reader_links_open_in_description
import com.aimdi.xtanative.ui.generated.resources.reader_links_system
import com.aimdi.xtanative.ui.media.ReaderIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * Settings' "Open links in" (_browser_picker.dart): one choice among the
 * app's own browser, the system's default and each installed browser, in a
 * sheet. Nothing on the desktop, whose links always go to its default
 * browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserPickerRow(modifier: Modifier = Modifier) {
    val links = LocalLinks.current ?: return
    if (links.desktop) return
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val browsers by produceState(emptyList<InstalledBrowser>(), links) { value = withContext(Dispatchers.Default) { links.browsers() } }
    val selected = BrowserPicker.selected(LinkSteps.settingsOf(settings), browsers)
    var open by remember { mutableStateOf(false) }

    Row(
        modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.reader_links_open_in), style = MaterialTheme.typography.bodyLarge)
            Text(destinationLabel(selected, browsers), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(ReaderIcons.UnfoldMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) {
                    Text(stringResource(Res.string.reader_links_open_in), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(Res.string.reader_links_open_in_description),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                BrowserPicker.options(browsers).forEach { destination ->
                    DestinationRow(
                        label = destinationLabel(destination, browsers),
                        description = if (destination == LinkDestination.InApp) stringResource(Res.string.reader_links_in_app_description) else null,
                        selected = destination == selected,
                    ) {
                        open = false
                        scope.launch {
                            graph.settings.update { current ->
                                LinkSteps.withLinkSettings(current, BrowserPicker.choose(LinkSteps.settingsOf(current), destination))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationRow(label: String, description: String?, selected: Boolean, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onPick).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun destinationLabel(destination: LinkDestination, browsers: List<InstalledBrowser>): String = when (destination) {
    LinkDestination.InApp -> stringResource(Res.string.reader_links_in_app)
    LinkDestination.SystemDefault -> stringResource(Res.string.reader_links_system)
    is LinkDestination.Browser -> BrowserPicker.label(destination.packageName, browsers)
}
