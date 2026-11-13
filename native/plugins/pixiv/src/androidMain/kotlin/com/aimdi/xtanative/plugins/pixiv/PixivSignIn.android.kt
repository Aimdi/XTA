package com.aimdi.xtanative.plugins.pixiv

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

internal actual val webSignIn: Boolean = true

/** Android's WebView on Pixiv's page; the redirect carrying the code (or Pixiv's own scheme) never loads. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun PixivLoginPage(url: String, onAddress: (String) -> Boolean, modifier: Modifier) {
    val handle by rememberUpdatedState(onAddress)
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = handle(request.url.toString())

                    override fun onPageStarted(view: WebView, started: String?, favicon: android.graphics.Bitmap?) {
                        if (started != null && handle(started)) view.stopLoading()
                    }
                }
                loadUrl(url)
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.destroy()
        },
        modifier = modifier,
    )
}
