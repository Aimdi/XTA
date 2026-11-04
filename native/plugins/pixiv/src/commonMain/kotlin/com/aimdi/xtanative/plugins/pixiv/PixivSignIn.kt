package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
 * is traded for the account's tokens, which stay on this device. The
 * desktop has no web view: a refresh token pasted in the settings signs in there.
 */
@Composable
internal fun PixivSignInScreen() {
    val runtime = LocalPixiv.current
    val language = currentLanguage()
    val pkce = remember { PixivLogin.pkce() }
    var trading by remember { mutableStateOf(false) }
    PluginScaffold(title = pluginString(Strings.sign_in), onBack = runtime.host::back) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !webSignIn -> PixivEmpty(pluginString(Strings.sign_in_needs_android))
                trading -> LoadingRow()
                else -> PixivLoginPage(PixivLogin.loginUrl(pkce.challenge).toString(), onAddress = { address ->
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
        }
    }
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
