package com.aimdi.xtanative.plugins.stocks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.stocks.CryptoMarket
import com.aimdi.xtanative.networks.stocks.StocksSearch
import com.aimdi.xtanative.networks.stocks.StocksSearchResult
import com.aimdi.xtanative.networks.stocks.TickerSearchHit
import com.aimdi.xtanative.networks.stocks.Watchlist
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where the add sheet's search stands: what was asked, on which chip, and what came back. */
internal data class AddSearch(
    val query: String = "",
    val crypto: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val result: StocksSearchResult? = null,
)

/**
 * The add sheet's search as XTA's StocksSearchStore runs it: typing waits
 * [StocksSearch.DEBOUNCE] before a search goes out, a pasted address goes at
 * once and switches to tokens, and only the newest search's answer lands.
 */
@Stable
internal class AddSearchModel(private val search: StocksSearch, private val scope: CoroutineScope) {
    var state by mutableStateOf(AddSearch())
        private set
    private var job: Job? = null

    fun change(query: String, crypto: Boolean = state.crypto, immediate: Boolean = false) {
        job?.cancel()
        val planned = search.plan(query, crypto)
        state = AddSearch(planned.query, planned.crypto, loading = planned.query.isNotEmpty())
        if (planned.query.isEmpty()) return
        job = scope.launch {
            if (!immediate && planned.address == null) delay(StocksSearch.DEBOUNCE.toMillis())
            state = try {
                state.copy(loading = false, result = search.run(planned.query, planned.crypto))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.copy(loading = false, failed = true)
            }
        }
    }
}

/**
 * Search by name or symbol, or paste a contract address (stocks_add_sheet.dart):
 * an address is recognised as typed and resolved to the token on every
 * network it exists on. [onPick] is handed a ticker (`AAPL`) or an encoded
 * token (`crypto:v1:…`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StocksAddSheet(app: StocksApp, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val model = remember(app) { AddSearchModel(app.search, scope) }
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val state = model.state
    LaunchedEffect(focus) { focus.requestFocus() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.68f).imePadding()) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !state.crypto, onClick = { model.change(text, crypto = false) }, label = { Text(pluginString(Strings.plugin_name)) })
                FilterChip(selected = state.crypto, onClick = { model.change(text, crypto = true) }, label = { Text(pluginString(Strings.crypto)) })
            }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    model.change(it)
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
                placeholder = { Text(pluginString(if (state.crypto) Strings.crypto_search else Strings.search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { model.change(text, immediate = true) }),
            )
            if (state.crypto) {
                Text(pluginString(Strings.crypto_identity_hint), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.fillMaxWidth().height(2.dp)) { if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            AddResults(state, retry = { model.change(state.query, immediate = true) }, onPick)
        }
    }
}

@Composable
private fun AddResults(state: AddSearch, retry: () -> Unit, onPick: (String) -> Unit) {
    val result = state.result
    val typed = if (state.crypto) null else Watchlist.normaliseTicker(state.query)?.takeIf { symbol -> result?.stocks.orEmpty().none { it.symbol == symbol } }
    LazyColumn(Modifier.fillMaxWidth()) {
        if (state.failed) {
            item { ListItem(headlineContent = { Text(pluginString(Strings.data_unavailable)) }, trailingContent = { TextButton(onClick = retry) { Text(pluginString(Strings.retry)) } }) }
        }
        if (result != null && state.crypto && result.tokens.isEmpty()) {
            item { ListItem(headlineContent = { Text(pluginString(if (result.address == null) Strings.no_results else Strings.crypto_no_token)) }) }
        }
        if (result != null && !state.crypto && Watchlist.normaliseTicker(state.query) == null && result.stocks.isEmpty()) {
            item { ListItem(headlineContent = { Text(pluginString(Strings.error)) }) }
        }
        if (typed != null) {
            item { ListItem(headlineContent = { Text("$$typed") }, leadingContent = { Icon(Icons.Default.Add, null) }, modifier = Modifier.clickable { onPick(typed) }) }
        }
        items(result?.stocks.orEmpty(), key = { "stock-" + it.symbol }) { hit -> StockHit(hit) { onPick(hit.symbol) } }
        items(result?.tokens.orEmpty(), key = { "token-" + it.asset.id }) { market -> TokenHit(market) { onPick(market.asset.encode()) } }
    }
}

@Composable
private fun StockHit(hit: TickerSearchHit, onTap: () -> Unit) {
    val about = listOfNotNull(hit.name, hit.exchange).joinToString(" · ")
    ListItem(
        headlineContent = { Text("$" + hit.symbol) },
        supportingContent = if (about.isEmpty()) null else ({ Text(about) }),
        modifier = Modifier.clickable(onClick = onTap),
    )
}

/** `$PEPE · Pepe` over `Ethereum · 0x6982…1933`, priced on the right: two tokens sharing a symbol told apart by network and contract. */
@Composable
private fun TokenHit(market: CryptoMarket, onTap: () -> Unit) {
    StocksQuoteRow(title = market.asset.fullLabel, subtitle = market.asset.subtitle, quote = market.quote, onTap = onTap)
}

/**
 * The watchlist to tidy (the manage sheet of stocks_screen.dart): each entry
 * by its label, a token over its network and cut contract, a long press
 * copying that contract whole; the bin takes it off the watchlist, a tap
 * opens its page. [say] tells the reader what a long press did.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun StocksManageSheet(app: StocksApp, onDismiss: () -> Unit, onRemoved: (String) -> Unit, say: (String) -> Unit) {
    val entries by app.watchlist.state.collectAsState()
    val copy = rememberCopyAddress(say)
    val shown = entries ?: return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            item {
                ListItem(headlineContent = { Text(pluginString(Strings.manage_watchlist)) }, supportingContent = { Text(pluginString(Strings.feed_hint)) })
            }
            items(shown.ids, key = { it }) { symbol ->
                val asset = shown.assets[symbol]
                ListItem(
                    headlineContent = { Text(shown.label(symbol)) },
                    supportingContent = if (asset == null) null else ({ Text(asset.subtitle) }),
                    trailingContent = {
                        IconButton(onClick = {
                            app.launch { app.watchlist.remove(symbol) }
                            onRemoved(symbol)
                        }) { Icon(Icons.Outlined.Delete, pluginString(Strings.unsubscribe)) }
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            onDismiss()
                            app.open(symbol)
                        },
                        onLongClick = asset?.let { { copy(it.address) } },
                    ),
                )
            }
        }
    }
}

/** Copies a token's whole contract, the thing a reader pastes into a wallet or explorer, and [say]s so: a long press gives no other sign. */
@Composable
internal fun rememberCopyAddress(say: (String) -> Unit): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val copied = pluginString(Strings.crypto_address_copied)
    return remember(clipboard, haptics, copied, say) {
        { address ->
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            clipboard.setText(AnnotatedString(address))
            say(copied)
        }
    }
}
