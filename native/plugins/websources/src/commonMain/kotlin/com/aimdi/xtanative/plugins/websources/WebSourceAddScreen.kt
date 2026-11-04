package com.aimdi.xtanative.plugins.websources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.websources.WebSourceDefinition
import com.aimdi.xtanative.networks.websources.WebSourceFile
import com.aimdi.xtanative.networks.websources.WebSourceListing
import com.aimdi.xtanative.networks.websources.WebSourceRefusal
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Where installing a source file stands: reading it, asking the reader, or why it cannot be. */
internal sealed interface InstallStep {
    data object Idle : InstallStep
    data class Reading(val job: Job) : InstallStep
    data class Asking(val source: WebSourceDefinition, val origin: String, val fromLink: Boolean) : InstallStep

    /** The file was refused, or ([refused] null) could not be read at all. */
    data class Refused(val refused: WebSourceFile.Refused?) : InstallStep
    data object Installed : InstallStep
}

/**
 * Installing a source from a file's address, as XTA's installWebSourceFrom:
 * the file is read behind a dialog, its sites agreed to by the reader, then
 * kept. Nothing is read from a source's sites before the reader agrees.
 */
internal class InstallFlow(private val app: WebSourcesApp, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow<InstallStep>(InstallStep.Idle)
    val step: StateFlow<InstallStep> = mutable.asStateFlow()
    val catalogue = MutableStateFlow<Result<List<WebSourceListing>>?>(null)

    fun loadCatalogue() {
        catalogue.value = null
        scope.launch { catalogue.value = attempt { app.client.catalogue() } }
    }

    /** What the reader typed: an HTTPS address, else refused as insecure. */
    fun fromLink(text: String) {
        val url = text.trim().toHttpUrlOrNull()
        if (url == null || url.scheme != "https" || url.host.isEmpty()) {
            mutable.value = InstallStep.Refused(WebSourceFile.Refused(WebSourceRefusal.INSECURE, text.trim()))
            return
        }
        read(url.toString(), fromLink = true)
    }

    fun read(address: String, fromLink: Boolean = false) {
        val url = address.toHttpUrlOrNull() ?: return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            mutable.value = when (val file = attempt { app.client.sourceFile(url) }.getOrNull()) {
                null -> InstallStep.Refused(null)
                is WebSourceFile.Refused -> InstallStep.Refused(file)
                is WebSourceFile.Read -> InstallStep.Asking(file.source, address, fromLink)
            }
        }
        mutable.value = InstallStep.Reading(job)
        job.start()
    }

    fun agree(asked: InstallStep.Asking) {
        mutable.value = InstallStep.Idle
        app.launch { app.install(asked.source, asked.origin) }
        if (asked.fromLink) mutable.value = InstallStep.Installed
    }

    fun dismiss() {
        (mutable.value as? InstallStep.Reading)?.job?.cancel()
        mutable.value = InstallStep.Idle
    }

    private suspend fun <T> attempt(read: suspend () -> T): Result<T> = try {
        Result.success(read())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Add source (web_source_add_screen.dart): a source file's link, and the
 * sources this repository publishes, each added once its sites are agreed to.
 */
@Composable
internal fun WebSourceAddScreen(app: WebSourcesApp) {
    val flow = rememberKept("websource-add") { scope -> InstallFlow(app, scope).also { it.loadCatalogue() } }
    val step by flow.step.collectAsState()
    var link by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(step) { if (step == InstallStep.Installed) app.host.back() }
    PluginScaffold(title = pluginString(Strings.add), onBack = app.host::back) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            item(key = "link") {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text(pluginString(Strings.link)) },
                    placeholder = { Text(pluginString(Strings.link_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { flow.fromLink(link) }),
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                    FilledTonalButton(onClick = { flow.fromLink(link) }) { Text(pluginString(Strings.add_link)) }
                }
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text(pluginString(Strings.published), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
            }
            published(app, flow)
        }
    }
    InstallDialogs(flow, step)
}

private fun LazyListScope.published(app: WebSourcesApp, flow: InstallFlow) {
    item(key = "published") {
        val catalogue by flow.catalogue.collectAsState()
        val installed by app.installed.collectAsState()
        val listings = catalogue?.getOrNull()
        when {
            catalogue == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            listings == null -> Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pluginString(Strings.published_unreachable), modifier = Modifier.weight(1f))
                TextButton(onClick = flow::loadCatalogue) { Text(pluginString(Strings.retry)) }
            }
            else -> Column { listings.forEach { listing -> ListingRow(listing, installed.orEmpty().any { it.source.id == listing.id }) { flow.read(listing.url.toString()) } } }
        }
    }
}

@Composable
private fun ListingRow(listing: WebSourceListing, installed: Boolean, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(listing.name, style = MaterialTheme.typography.bodyLarge)
            if (listing.description.isNotEmpty()) Text(listing.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (installed) Text(pluginString(Strings.installed), style = MaterialTheme.typography.labelLarge) else FilledTonalButton(onClick = onAdd) { Text(pluginString(Strings.install)) }
    }
}

@Composable
private fun InstallDialogs(flow: InstallFlow, step: InstallStep) {
    when (step) {
        is InstallStep.Reading -> ReadingDialog(flow::dismiss)
        is InstallStep.Asking -> ConsentDialog(step.source, step.origin.toHttpUrlOrNull()?.host, update = false, onDismiss = flow::dismiss) { flow.agree(step) }
        is InstallStep.Refused -> RefusalDialog(step.refused, flow::dismiss)
        InstallStep.Idle, InstallStep.Installed -> Unit
    }
}
