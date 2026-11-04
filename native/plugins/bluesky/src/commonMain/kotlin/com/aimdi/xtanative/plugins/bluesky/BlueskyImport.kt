package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.bluesky.Bluesky
import com.aimdi.xtanative.networks.bluesky.BlueskyPaging
import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.networks.bluesky.BlueskyUrls
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.search.DiscoverIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/** What each import says and asks for (bluesky_import_*_screen.dart). */
private class ImportTexts(val title: PluginText, val intro: PluginText, val note: PluginText, val label: PluginText, val hint: PluginText, val invalid: PluginText)

private val ImportKind.texts: ImportTexts
    get() = when (this) {
        ImportKind.FOLLOWING -> ImportTexts(Strings.import_following, Strings.import_following_intro, Strings.import_following_note, Strings.add_account, Strings.handle_hint, Strings.invalid_handle)
        ImportKind.LIST -> ImportTexts(Strings.import_list, Strings.import_list_intro, Strings.import_list_note, Strings.import_list, Strings.import_list_hint, Strings.invalid_list)
        ImportKind.STARTER -> ImportTexts(Strings.import_starter, Strings.import_starter_intro, Strings.import_list_note, Strings.import_starter, Strings.import_starter_hint, Strings.invalid_starter)
    }

/**
 * Copies a public account's following, a public list's members or a starter
 * pack's into the follows kept here, page by page, those already followed
 * skipped. Read-only: nothing is written to Bluesky.
 */
@Composable
internal fun ImportScreen(app: BlueskyApp, kind: ImportKind, initial: String) {
    val run = rememberKept("bluesky-import:$kind") { scope -> ImportRun(app, scope) }
    val state by run.state.collectAsState()
    val texts = kind.texts
    var text by rememberSaveable { mutableStateOf(initial) }
    var refused by rememberSaveable { mutableStateOf(false) }
    val language = currentLanguage()
    val start = { refused = !run.start(kind, text, language) }
    PluginScaffold(title = pluginString(texts.title), onBack = app.host::back) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = padding.calculateTopPadding()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(pluginString(texts.intro))
            Text(pluginString(texts.note))
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    refused = false
                },
                singleLine = true,
                label = { Text(pluginString(texts.label)) },
                placeholder = { Text(pluginString(texts.hint)) },
                prefix = if (kind == ImportKind.FOLLOWING) ({ Text("@") }) else null,
                isError = refused,
                supportingText = if (refused) ({ Text(pluginString(texts.invalid)) }) else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { start() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { start() }, enabled = state !is ImportRun.State.Running) {
                Icon(DiscoverIcons.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(pluginString(Strings.import_action))
            }
            Progress(state)
        }
    }
}

@Composable
private fun Progress(state: ImportRun.State) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when (state) {
            ImportRun.State.Idle -> Unit
            is ImportRun.State.Running -> {
                CircularProgressIndicator()
                Text(pluginString(Strings.imported_so_far, state.count.toString()), modifier = Modifier.padding(top = 12.dp))
            }
            is ImportRun.State.Done -> {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                Text(pluginString(Strings.import_finished, state.count.toString()), modifier = Modifier.padding(top = 12.dp))
            }
            is ImportRun.State.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pluginString(Strings.unable_to_import) + ": " + state.reason, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}

/** One import at a time, kept while its screen is on the back stack, counting the accounts it added. */
internal class ImportRun(private val app: BlueskyApp, private val scope: CoroutineScope) {
    sealed interface State {
        data object Idle : State

        data class Running(val count: Int) : State

        data class Done(val count: Int) : State

        data class Failed(val reason: String) : State
    }

    private val mutable = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = mutable.asStateFlow()
    private var job: Job? = null

    /** Starts copying what [input] names; false, and nothing started, when it names nothing of [kind]. */
    fun start(kind: ImportKind, input: String, language: String): Boolean {
        if (job?.isActive == true) return true
        val pages = pagesOf(kind, input) ?: return false
        job = scope.launch {
            var count = 0
            mutable.value = State.Running(0)
            mutable.value = try {
                pages.collect { people ->
                    count += app.follows.followAll(people, System.currentTimeMillis())
                    mutable.value = State.Running(count)
                }
                State.Done(count)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                State.Failed(errorText(e).get(language))
            }
        }
        return true
    }

    /** Every page of accounts [input] names; null when it names none. */
    private fun pagesOf(kind: ImportKind, input: String): Flow<List<BlueskyProfile>>? = when (kind) {
        ImportKind.FOLLOWING -> BlueskyUrls.handle(input)?.let { actor -> BlueskyPaging.walk { cursor -> app.client.follows(actor, cursor = cursor) } }
        ImportKind.LIST -> BlueskyUrls.listRef(input)?.let { ref -> members { app.client.resolveListUri(ref) } }
        ImportKind.STARTER -> BlueskyUrls.starterPackRef(input)?.let { ref ->
            members {
                app.client.starterPackListUri(app.client.resolveStarterPackUri(ref))?.ifEmpty { null }
                    ?: throw NetworkRequestException(Bluesky.NETWORK, 200, "starter pack has no list")
            }
        }
    }

    /** Every page of the members of the list [listUri] finds. */
    private fun members(listUri: suspend () -> String): Flow<List<BlueskyProfile>> = flow {
        val uri = listUri()
        emitAll(BlueskyPaging.walk { cursor -> app.client.list(uri, cursor = cursor).members })
    }
}
