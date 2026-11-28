package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivFailure
import com.aimdi.xtanative.networks.pixiv.PixivLogin
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.CancellationException

/** Whether this platform has a web view to open Pixiv's sign-in page in: Android's; the desktop has none. */
internal expect val webSignIn: Boolean

/** Pixiv's sign-in page at [url]; [onAddress] sees each address it goes to and answers whether it took it, so the page goes no further. */
@Composable
internal expect fun PixivLoginPage(url: String, onAddress: (String) -> Boolean, modifier: Modifier)

/**
 * Signing in on Pixiv's own page, as XTA does: the redirect's one-time code
 * is traded for the account's tokens, which stay on this device. Android
 * shows the page in its web view; the desktop opens it in the reader's
 * browser, which hands the code back as a `pixiv://` address
 * ([PixivSignInReturnScreen]), or the reader pastes it.
 */
@Composable
internal fun PixivSignInScreen() {
    val runtime = LocalPixiv.current
    PluginScaffold(title = pluginString(Strings.sign_in), onBack = runtime.host::back) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (webSignIn) WebViewSignIn() else BrowserSignIn()
        }
    }
}

@Composable
private fun WebViewSignIn() {
    val runtime = LocalPixiv.current
    val language = currentLanguage()
    val pkce = remember { PixivLogin.pkce() }
    var trading by remember { mutableStateOf(false) }
    if (trading) return LoadingRow()
    PixivLoginPage(PixivLogin.loginUrl(pkce.challenge).toString(), onAddress = { address ->
        val code = PixivLogin.codeFrom(address)
        when {
            trading -> true
            code != null -> {
                trading = true
                runtime.launch { signIn(runtime, code, pkce.verifier, language) }
                true
            }
            PixivLogin.deniedIn(address) -> {
                runtime.tell(Strings.sign_in_failed.get(language))
                runtime.host.back()
                true
            }
            else -> false
        }
    }, Modifier.fillMaxSize())
}

/**
 * The desktop's sign-in: Pixiv's page in the browser, the code it hands back
 * taken here (or pasted, with the steps to find it), and the refresh token
 * of the settings for readers who have one.
 */
@Composable
private fun BrowserSignIn() {
    val runtime = LocalPixiv.current
    val outside = rememberOutside()
    val language = currentLanguage()
    val state by runtime.browserSignIn.state.collectAsState()
    ClosesOnceSignedIn(state.finished)
    var pasted by remember { mutableStateOf("") }
    val submit = { text: String ->
        pasted = ""
        runtime.launch { tellOutcome(runtime, handBack(runtime, text), language) }
    }
    if (state.trading) return LoadingRow()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val width = Modifier.widthIn(max = 560.dp).fillMaxWidth()
        Text(pluginString(Strings.sign_in_browser_intro), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = width)
        Button(onClick = { outside.open(runtime.browserSignIn.start()) }) { Text(pluginString(Strings.sign_in_open_browser)) }
        if (state.waiting) Text(pluginString(Strings.sign_in_waiting), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = width)
        HorizontalDivider(width.padding(vertical = 8.dp))
        Text(pluginString(Strings.sign_in_paste_title), style = MaterialTheme.typography.titleSmall, modifier = width)
        Text(pluginString(Strings.sign_in_paste_steps), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = width)
        OutlinedTextField(
            value = pasted,
            onValueChange = { text ->
                // A whole address pasted (not typed) is traded at once: its code lasts only seconds.
                if (text.length - pasted.length > PASTED && PixivLogin.codeFrom(text.trim()) != null) submit(text) else pasted = text
            },
            placeholder = { Text(pluginString(Strings.sign_in_paste_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (pasted.isNotBlank()) submit(pasted) }),
            modifier = width,
        )
        OutlinedButton(enabled = pasted.isNotBlank(), onClick = { submit(pasted) }) { Text(pluginString(Strings.sign_in_paste_use)) }
        HorizontalDivider(width.padding(vertical = 8.dp))
        TextButton(onClick = { runtime.open(PixivRoute.Settings) }) { Text(pluginString(Strings.advanced_token)) }
    }
}

/**
 * Where the browser hands Pixiv's sign-in back (`xta-native
 * pixiv://account/login?code=…`): the code is traded with the sign-in
 * started here, then this screen closes, and the sign-in screen below it too
 * once it worked.
 */
@Composable
internal fun PixivSignInReturnScreen(code: String?) {
    val runtime = LocalPixiv.current
    val language = currentLanguage()
    var handled by rememberSaveable { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(code) {
        if (handled) {
            done = true
            return@LaunchedEffect
        }
        handled = true
        if (code == null) {
            runtime.browserSignIn.cancel()
            runtime.tell(Strings.sign_in_failed.get(language))
            done = true
            return@LaunchedEffect
        }
        runtime.launch {
            tellOutcome(runtime, handBack(runtime, code), language)
            done = true
        }
    }
    LaunchedEffect(done) { if (done) runtime.host.back() }
    PluginScaffold(title = pluginString(Strings.sign_in), onBack = runtime.host::back) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) { LoadingRow() }
    }
}

/** A sign-in screen opened before a sign-in finished closes once it did, as the web view's does. */
@Composable
private fun ClosesOnceSignedIn(finished: Int) {
    val runtime = LocalPixiv.current
    val atOpen = rememberSaveable { finished }
    LaunchedEffect(finished) { if (finished > atOpen) runtime.host.back() }
}

/**
 * In place of a read that needs an account, signed out: [message] and the
 * way to sign in, never a Retry that cannot work.
 */
@Composable
internal fun PixivSignInPrompt(message: String, modifier: Modifier = Modifier) {
    val runtime = LocalPixiv.current
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(onClick = { runtime.open(PixivRoute.SignIn) }) { Text(pluginString(Strings.sign_in)) }
    }
}

/** More characters than this arriving in one change are a paste. */
private const val PASTED = 8

private suspend fun handBack(runtime: PixivRuntime, text: String): PixivCodeOutcome =
    runtime.browserSignIn.handBack(text) { code, verifier -> PixivSession.signIn(runtime, code, verifier) }

private fun tellOutcome(runtime: PixivRuntime, outcome: PixivCodeOutcome, language: String) {
    signInOutcomeText(outcome, language)?.let(runtime::tell)
}

/** What the reader is told of a code handed back; nothing while another is being traded. */
internal fun signInOutcomeText(outcome: PixivCodeOutcome, language: String): String? = when (outcome) {
    is PixivCodeOutcome.SignedIn -> Strings.signed_in.get(language, outcome.user.displayName)
    is PixivCodeOutcome.Failed -> Strings.sign_in_failed.get(language) + ": " + failureText(PixivFailure.of(outcome.error)).get(language)
    PixivCodeOutcome.NoCode -> Strings.sign_in_no_code.get(language)
    PixivCodeOutcome.Expired -> Strings.sign_in_expired.get(language)
    PixivCodeOutcome.Busy -> null
}

private suspend fun signIn(runtime: PixivRuntime, code: String, verifier: String, language: String) {
    val said = try {
        val user = PixivSession.signIn(runtime, code, verifier)
        Strings.signed_in.get(language, user.displayName)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Strings.sign_in_failed.get(language) + ": " + failureText(PixivFailure.of(e)).get(language)
    }
    runtime.tell(said)
    runtime.host.back()
}
