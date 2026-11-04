package com.aimdi.xtanative.ui.links

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.aimdi.xtanative.services.links.LinkBrowserSession
import com.aimdi.xtanative.services.links.LinkNavigation
import com.aimdi.xtanative.services.links.linkNavigation

/**
 * The page in Android's WebView (link_browser_page.dart). Back steps through
 * the page's own history first; only when there is none does it leave the
 * screen. Third-party cookies are refused: an article does not get to set
 * them for the ad networks it embeds.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun LinkWebPage(url: String, session: LinkBrowserSession, guard: (String) -> Boolean, control: LinkPageControl, modifier: Modifier) {
    var canGoBack by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<WebView?>(null) }
    val asks by rememberUpdatedState(guard)
    BackHandler(enabled = canGoBack) { page?.goBack() }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(this, false) }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, started: String?, favicon: Bitmap?) {
                        session.started(started.orEmpty())
                        canGoBack = view.canGoBack()
                    }

                    override fun onPageFinished(view: WebView, finished: String?) {
                        session.finished(finished.orEmpty())
                        canGoBack = view.canGoBack()
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) session.stopped()
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url.toString()
                        return when (linkNavigation(target)) {
                            LinkNavigation.ASK_APP -> request.isForMainFrame && asks(target)
                            LinkNavigation.ALLOW -> false
                            LinkNavigation.BLOCK -> true
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        session.progressed(newProgress)
                    }
                }
                control.reload = { reload() }
                page = this
                session.started(url)
                loadUrl(url)
            }
        },
        onRelease = { view ->
            control.reload = {}
            page = null
            view.stopLoading()
            view.destroy()
        },
        modifier = modifier,
    )
}
