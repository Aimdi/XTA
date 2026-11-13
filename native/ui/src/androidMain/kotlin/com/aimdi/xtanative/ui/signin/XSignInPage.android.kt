package com.aimdi.xtanative.ui.signin

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.settings_ok
import com.aimdi.xtanative.ui.generated.resources.signin_logging_in_text
import com.aimdi.xtanative.ui.generated.resources.signin_logging_in_title
import com.aimdi.xtanative.ui.settings.SettingsPage
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.stringResource

/**
 * X's own login page in Android's web view (XTA's TwitterLoginWebview),
 * after XTA's note on what signing in does. Once X reaches its home page,
 * the session's cookies and the handle X's page names make the account.
 * "Sign in with Google" opens its popup in a window of its own, which keeps
 * the page that opened it, as XTA's WebViewPopups does.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun XSignInPage(onBack: () -> Unit, onSignedIn: (XAccount) -> Unit) {
    var informed by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val signedIn by rememberUpdatedState(onSignedIn)
    // X can report reaching its home page twice in a row: the account is made once.
    val reading = remember { AtomicBoolean(false) }
    SettingsPage("", onBack) { padding ->
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setSupportMultipleWindows(true)
                    settings.userAgentString = XSignIn.USER_AGENT
                    webViewClient = HomeWatcher { view -> if (reading.compareAndSet(false, true)) read(view, scope, reading) { signedIn(it) } }
                    webChromeClient = PopupWindows(context.activity())
                    loadUrl(XSignIn.LOGIN_PAGE)
                }
            },
            onRelease = { view ->
                view.stopLoading()
                view.destroy()
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
    if (!informed) {
        AlertDialog(
            onDismissRequest = { informed = true },
            title = { Text(stringResource(Res.string.signin_logging_in_title)) },
            text = { Text(stringResource(Res.string.signin_logging_in_text)) },
            confirmButton = { TextButton(onClick = { informed = true }) { Text(stringResource(Res.string.settings_ok)) } },
        )
    }
}

internal actual val signInOffersImport: Boolean = true

internal actual fun forgetWebSessions() {
    runCatching {
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies(null)
        cookies.flush()
    }
}

/** Calls [onHome] each time the page's address becomes X's home page: X moves there without loading a page, so the history is watched. */
private class HomeWatcher(private val onHome: (WebView) -> Unit) : WebViewClient() {
    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        if (url == XSignIn.HOME) onHome(view)
    }
}

/** The handle off [view]'s page, then the account the session's cookies make; [reading] is let go when either is not there yet. */
private fun read(view: WebView, scope: CoroutineScope, reading: AtomicBoolean, onAccount: (XAccount) -> Unit) {
    scope.launch {
        val account = screenName(view)?.let { name -> XSignIn.account(XSignIn.cookiesOf(CookieManager.getInstance().getCookie(XSignIn.LOGIN_PAGE)), name) }
        if (account == null) reading.set(false) else onAccount(account)
    }
}

/** X's home page fills in the handle after its address changed, and no other change follows: it is asked a few times. */
private suspend fun screenName(view: WebView): String? {
    repeat(XSignIn.SCREEN_NAME_TRIES) {
        val name = XSignIn.screenNameIn(evaluated(view, XSignIn.SCREEN_NAME_SCRIPT))
        if (name.isNotEmpty()) return name
        delay(XSignIn.SCREEN_NAME_WAIT_MILLIS)
    }
    return null
}

private suspend fun evaluated(view: WebView, script: String): String? = suspendCancellableCoroutine { continuation ->
    view.evaluateJavascript(script) { result -> if (continuation.isActive) continuation.resume(result) }
}

/**
 * Shows the windows the page opens (Google's sign-in) in a dialog of their
 * own, so the popup keeps `window.opener` to hand its token back; it closes
 * when the page closes it or the reader backs out of it.
 */
private class PopupWindows(private val activity: Activity?) : WebChromeClient() {
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        val owner = activity ?: return false
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val popup = popupFor(owner, view)
        val dialog = Dialog(owner, android.R.style.Theme_DeviceDefault_Light_NoActionBar).apply {
            setContentView(popup)
            setOnDismissListener { popup.destroy() }
        }
        popup.webChromeClient = object : WebChromeClient() {
            override fun onCloseWindow(window: WebView) = dialog.dismiss()
        }
        transport.webView = popup
        resultMsg.sendToTarget()
        dialog.show()
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun popupFor(owner: Activity, opener: WebView): WebView = WebView(owner).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.userAgentString = opener.settings.userAgentString
        webViewClient = WebViewClient()
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
