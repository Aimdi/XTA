package com.aimdi.xtanative.plugins.substack

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.aimdi.xtanative.networks.substack.SubstackWebPage

internal actual val articleWebView: Boolean = true

/**
 * [page] in Android's WebView, as XTA's reader shows it. Scripts run: the
 * article's page holds only the reader's own (its body is sanitized), and
 * the live page is the publication's website. Only the article's page gets
 * the `XtaReading` and `XtaTts` channels, and its selection toolbar gains
 * [speakLabel]. Third-party cookies are refused, as in the in-app browser.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
internal actual fun SubstackWebView(page: WebPage, speakLabel: String, control: WebPageControl, events: WebPageEvents, modifier: Modifier) {
    val told by rememberUpdatedState(events)
    val label by rememberUpdatedState(speakLabel)
    key(page) {
        AndroidView(
            factory = { context ->
                val article = page is WebPage.Article
                ArticleWebView(context) { label.takeIf { article } }.apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(this, false) }
                    if (article) {
                        addJavascriptInterface(Channel(this) { told.onReading(it) }, "XtaReading")
                        addJavascriptInterface(Channel(this) { told.onSpeakFrom(it) }, "XtaTts")
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            request.isForMainFrame && told.onNavigate(request.url.toString())

                        override fun onPageFinished(view: WebView, url: String?) {
                            told.onLoaded()
                            if (!article) view.evaluateJavascript(SubstackWebPage.LIVE_TEXT) { raw -> SubstackWebPage.scriptResult(raw)?.let { told.onLiveText(it) } }
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) told.onFailed(error.description?.toString().orEmpty())
                        }
                    }
                    val run: (String) -> Unit = { script -> evaluateJavascript(script, null) }
                    tag = run
                    control.run = run
                    when (page) {
                        is WebPage.Article -> loadDataWithBaseURL(page.baseUrl, page.html, "text/html", "utf-8", null)
                        is WebPage.Live -> loadUrl(page.url)
                    }
                }
            },
            onRelease = { view ->
                if (control.run === view.tag) control.run = {}
                view.stopLoading()
                view.destroy()
            },
            modifier = modifier,
        )
    }
}

/** A channel the page posts to (`window.<name>.postMessage(text)`); messages arrive on the main thread. */
private class Channel(private val view: View, private val onMessage: (String) -> Unit) {
    @JavascriptInterface
    fun postMessage(message: String) {
        view.post { onMessage(message) }
    }
}

/** A web view whose selection toolbar also offers [speakLabel], when it gives one: the rest of the article from the selected paragraph is read aloud. */
private class ArticleWebView(context: Context, private val speakLabel: () -> String?) : WebView(context) {
    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? = super.startActionMode(withSpeak(callback), type)

    override fun startActionMode(callback: ActionMode.Callback): ActionMode? = super.startActionMode(withSpeak(callback))

    private fun withSpeak(callback: ActionMode.Callback): ActionMode.Callback = speakLabel()?.let { SpeakCallback(callback, it) } ?: callback

    private inner class SpeakCallback(private val base: ActionMode.Callback, private val label: String) : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = base.onCreateActionMode(mode, menu)

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            base.onPrepareActionMode(mode, menu)
            if (menu.findItem(SPEAK_FROM_HERE) == null) menu.add(Menu.NONE, SPEAK_FROM_HERE, 1, label).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId != SPEAK_FROM_HERE) return base.onActionItemClicked(mode, item)
            // The selection must still be there when the script reads it: the toolbar goes once it has.
            evaluateJavascript(SubstackWebPage.SPEAK_SELECTION) { mode.finish() }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) = base.onDestroyActionMode(mode)

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            if (base is ActionMode.Callback2) base.onGetContentRect(mode, view, outRect) else super.onGetContentRect(mode, view, outRect)
        }
    }
}

private const val SPEAK_FROM_HERE = 0x5e4c
