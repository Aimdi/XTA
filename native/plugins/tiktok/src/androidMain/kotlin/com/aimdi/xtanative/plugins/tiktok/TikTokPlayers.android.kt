package com.aimdi.xtanative.plugins.tiktok

import android.annotation.SuppressLint
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

internal actual val inAppPlayer: Boolean = true

/** Android's VideoView, centred at the video's shape: a tap pauses and plays again. */
@Composable
internal actual fun DirectPlayer(url: String, headers: Map<String, String>, modifier: Modifier, onError: () -> Unit) {
    val failed by rememberUpdatedState(onError)
    AndroidView(
        factory = { context ->
            val video = VideoView(context).apply {
                setOnPreparedListener { player ->
                    player.isLooping = true
                    start()
                }
                setOnErrorListener { _, _, _ ->
                    failed()
                    true
                }
                setOnClickListener { if (isPlaying) pause() else start() }
                setVideoURI(Uri.parse(url), headers)
            }
            FrameLayout(context).apply {
                addView(video, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            }
        },
        onRelease = { frame -> (frame.getChildAt(0) as? VideoView)?.stopPlayback() },
        modifier = modifier,
    )
}

/** TikTok's embedded player in a web view that may play without a tap, a thin bar while the page loads. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun EmbedPlayer(url: String, cookies: Map<String, String>, userAgent: String, modifier: Modifier) {
    var loading by remember(url) { mutableStateOf(true) }
    Box(modifier) {
        AndroidView(
            factory = { context ->
                val jar = CookieManager.getInstance()
                cookies.forEach { (name, value) -> jar.setCookie("https://www.tiktok.com", "$name=$value; Domain=.tiktok.com; Path=/") }
                WebView(context).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString = userAgent
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, finished: String?) {
                            loading = false
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) loading = false
                        }
                    }
                    loadUrl(url)
                }
            },
            onRelease = { view ->
                view.stopLoading()
                view.destroy()
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
    }
}
