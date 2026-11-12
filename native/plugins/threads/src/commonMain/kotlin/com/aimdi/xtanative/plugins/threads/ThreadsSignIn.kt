package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aimdi.xtanative.networks.threads.ThreadsCookiePaste
import com.aimdi.xtanative.networks.threads.ThreadsSessionCheck
import com.aimdi.xtanative.ui.plugins.EmptyPane
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.pluginString

/** Whether this platform has a web view to open Threads' sign-in page in: Android's; the desktop has none. */
internal expect val webSignIn: Boolean

/**
 * Threads' own sign-in page. [onCookies] sees the site's cookies as they
 * change and answers whether it took them; once it has, the page's own copy
 * of the session is cleared, so the session lives only where a pasted one does.
 */
@Composable
internal expect fun ThreadsLoginPage(onCookies: (String) -> Boolean, modifier: Modifier)

/** Where the sign-in page starts, and the sites whose cookies it may leave. */
internal object ThreadsSignInPages {
    const val LOGIN = "https://www.threads.com/login"
    const val SITE = "https://www.threads.com"
    val SITES = listOf(SITE, "https://www.instagram.com")
    const val POLL_MILLIS = 1_000L
}

/**
 * Signing in on threads.com's own page, as the app signs in to X on
 * Android: once the page holds a whole session, its cookies are kept where
 * a pasted session is (the app's secret preferences, in no backup), and
 * the settings say whom they sign in as. Nothing is posted; the desktop,
 * with no web view, says where to find the cookies instead.
 */
@Composable
internal fun ThreadsSignInScreen(app: ThreadsApp) {
    var taken by remember { mutableStateOf(false) }
    PluginScaffold(title = pluginString(Strings.sign_in_web), onBack = app.host::back) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (!webSignIn) {
                EmptyPane(ThreadsIcons.Mark, pluginString(Strings.cookies_how), detail = pluginString(Strings.cookies_steps))
                return@Box
            }
            ThreadsLoginPage(onCookies = { cookies ->
                val session = ThreadsSessionCheck.cookies(cookies) as? ThreadsCookiePaste.Complete
                if (session != null && !taken) {
                    taken = true
                    app.signIn(session.header)
                    app.host.back()
                }
                session != null
            }, Modifier.fillMaxSize())
        }
    }
}
