package com.aimdi.xtanative.plugins.reddit

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Reddit's sign-in page in Android's web view. The redirect back to the app
 * resolves to no page, so it is caught as navigation, before it loads, and
 * once more as the page it would have started.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun RedditSignInPage(url: String, modifier: Modifier, onAddress: (String) -> Boolean) {
    val answer by rememberUpdatedState(onAddress)
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = answer(request.url.toString())

                    override fun onPageStarted(view: WebView, started: String?, favicon: Bitmap?) {
                        if (started != null && answer(started)) view.stopLoading()
                    }
                }
                loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
        modifier = modifier,
    )
}
