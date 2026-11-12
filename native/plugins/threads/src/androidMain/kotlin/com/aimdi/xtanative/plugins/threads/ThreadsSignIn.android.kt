package com.aimdi.xtanative.plugins.threads

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.aimdi.xtanative.networks.threads.ThreadsSession
import kotlinx.coroutines.delay

internal actual val webSignIn: Boolean = true

/** Android's web view on threads.com's sign-in page, its cookies looked at each second until they hold a whole session. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun ThreadsLoginPage(onCookies: (String) -> Boolean, modifier: Modifier) {
    val taking by rememberUpdatedState(onCookies)
    LaunchedEffect(Unit) {
        while (true) {
            val cookies = siteCookies(ThreadsSignInPages.SITE)
            if (cookies != null && taking(cookies)) {
                forgetWebSession()
                break
            }
            delay(ThreadsSignInPages.POLL_MILLIS)
        }
    }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                loadUrl(ThreadsSignInPages.LOGIN)
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.destroy()
        },
        modifier = modifier,
    )
}

private fun siteCookies(site: String): String? = runCatching { CookieManager.getInstance().getCookie(site) }.getOrNull()

/** Expires every cookie threads.com and Instagram's sign-in left in the web view: the kept session is the only copy. */
private fun forgetWebSession() {
    runCatching {
        val manager = CookieManager.getInstance()
        ThreadsSignInPages.SITES.forEach { site ->
            val host = site.substringAfter("://")
            val names = ThreadsSession.parseCookieHeader(siteCookies(site).orEmpty()).keys
            names.forEach { name ->
                listOf("", "; Domain=$host", "; Domain=.${host.removePrefix("www.")}").forEach { domain ->
                    manager.setCookie(site, "$name=; Max-Age=0; Path=/$domain")
                }
            }
        }
        manager.flush()
    }
}
