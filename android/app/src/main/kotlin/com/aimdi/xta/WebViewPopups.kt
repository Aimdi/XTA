package com.aimdi.xta

import android.app.Activity
import android.app.Dialog
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugins.webviewflutter.WebChromeClientProxyApi
import io.flutter.plugins.webviewflutter.WebViewFlutterAndroidExternalApi
import java.util.Collections
import java.util.WeakHashMap

/**
 * webview_flutter discards the WebView created for `window.open`, so the opened page has no `window.opener`. X's
 * "Sign in with Google" button needs it to hand its token back, so the popup is shown here instead.
 *
 * Only the WebView Dart names is patched: replacing a chrome client drops the plugin's own callbacks (progress, file
 * chooser, permissions), which the app's other WebViews rely on. The plugin only accepts its own
 * SecureWebChromeClient, hence the inheritance.
 *
 * Ported from QuaX (commit ece7270).
 */
object WebViewPopups {
    private val patched: MutableSet<WebView> = Collections.newSetFromMap(WeakHashMap())

    /** False when no WebView answers to [identifier]. */
    @Suppress("DEPRECATION")
    fun install(activity: Activity, engine: FlutterEngine, identifier: Long): Boolean {
        val webView = WebViewFlutterAndroidExternalApi.getWebView(engine, identifier) ?: return false
        if (patched.add(webView)) {
            webView.webChromeClient = PopupChromeClient(activity)
        }
        return true
    }

    private class PopupChromeClient(private val activity: Activity) :
        WebChromeClientProxyApi.SecureWebChromeClient() {
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean {
            val popup = createPopup(view)
            val dialog = Dialog(activity, android.R.style.Theme_DeviceDefault_Light_NoActionBar).apply {
                setContentView(popup)
                setOnDismissListener { popup.destroy() }
            }
            popup.webChromeClient = object : WebChromeClient() {
                override fun onCloseWindow(window: WebView) = dialog.dismiss()
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = popup
            resultMsg.sendToTarget()
            dialog.show()
            return true
        }

        private fun createPopup(opener: WebView): WebView = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.userAgentString = opener.settings.userAgentString
            webViewClient = WebViewClient()
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }
}
