package com.aimdi.xtanative.ui.links

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import com.aimdi.xtanative.services.links.InstalledBrowser
import com.aimdi.xtanative.services.links.LinkLaunch
import com.aimdi.xtanative.ui.common.findActivity

@Composable
internal actual fun rememberLinkPlatform(): LinkPlatform {
    val context = LocalContext.current
    return remember(context) { AndroidLinks(context) }
}

/**
 * The text another app shares here (an ACTION_SEND of text/plain, which the
 * app's manifest takes): the one the activity was started with, once, and
 * each one that reaches it afterwards.
 */
@Composable
internal actual fun SharedTextEffect(onText: (String) -> Unit) = IntentEffect(::sharedText, onText)

/**
 * A link another app opens here (an ACTION_VIEW of a host the manifest
 * claims: X's, its short links and mirrors, Pixiv's pages), as
 * [SharedTextEffect] hands shared text.
 */
@Composable
internal actual fun IncomingLinkEffect(onLink: (String) -> Unit) = IntentEffect(::viewedLink, onLink)

/** Hands [onFound] what [read] finds in the intent the activity was started with, once, and in each one that reaches it afterwards. */
@Composable
private fun IntentEffect(read: (Intent?) -> String?, onFound: (String) -> Unit) {
    val activity = LocalContext.current.findActivity() as? ComponentActivity ?: return
    val latest by rememberUpdatedState(onFound)
    // An intent is handled once: the activity keeps it when it is made again for a new configuration.
    var handled by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(activity) {
        if (!handled) {
            handled = true
            read(activity.intent)?.let(latest)
        }
        val listener = Consumer<Intent> { intent -> read(intent)?.let(latest) }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
}

private fun sharedText(intent: Intent?): String? {
    val handed = intent?.takeIf { it.action == Intent.ACTION_SEND } ?: return null
    return handed.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: handed.getStringExtra(Intent.EXTRA_TEXT) ?: ""
}

private fun viewedLink(intent: Intent?): String? =
    intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

/**
 * Links leaving the app on Android (urls.dart, browsers.dart): Custom Tabs
 * (a VIEW carrying the Custom Tabs session extra, which a browser that has
 * them opens as a tab and any other browser as a page), a browser by its
 * package, or the system's choice. A link the app itself claims (x.com)
 * goes to a browser by name, since a plain VIEW of it would land back here;
 * every other launch falls back to the next way rather than doing nothing.
 */
private class AndroidLinks(private val context: Context) : LinkPlatform {
    override val desktop: Boolean = false

    override fun launch(launch: LinkLaunch) {
        val url = launch.url
        when (launch) {
            is LinkLaunch.CustomTab, is LinkLaunch.InAppBrowser -> customTab(url, if (LinkSteps.claimedByApp(url)) defaultBrowser() else null) || view(url, null)
            is LinkLaunch.External -> (launch.browser.isNotEmpty() && view(url, launch.browser)) || view(url, null)
            // Never a VIEW that names no browser: it would land in this app again.
            is LinkLaunch.RealBrowser -> realBrowser(launch)
            is LinkLaunch.Desktop -> view(url, null)
        }
    }

    /**
     * A page the app claims, in a browser by name (urls.dart's openInRealBrowser):
     * the one picked, then a Custom Tab where the reader keeps links in the
     * app, then the default browser, else another installed one; never this app.
     */
    private fun realBrowser(launch: LinkLaunch.RealBrowser): Boolean {
        val named = launch.browser.takeIf { it.isNotEmpty() && it != context.packageName }
        if (named != null && view(launch.url, named)) return true
        val browser = LinkSteps.realBrowser(defaultBrowser(), browsers(), context.packageName) ?: return false
        if (launch.customTabFirst && customTab(launch.url, browser)) return true
        return view(launch.url, browser) || customTab(launch.url, browser)
    }

    /**
     * Whether a plain VIEW of [url] could land in this app again: an x.com
     * page the reader let the app open by default, or one Android would offer
     * in a chooser beside it. Asked only for the hosts the manifest claims.
     */
    override fun reopensApp(url: String): Boolean {
        if (!LinkSteps.claimedByApp(url)) return false
        return runCatching {
            val handler = context.packageManager.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)), PackageManager.MATCH_DEFAULT_ONLY)
            val owner = handler?.activityInfo?.packageName
            owner == null || owner == "android" || owner == context.packageName
        }.getOrDefault(true)
    }

    override fun share(text: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        return start(Intent.createChooser(send, null))
    }

    /**
     * Every app that opens an https link, one entry per app, by name
     * (MainActivity.kt's listBrowsers): visible through the manifest's queries.
     */
    override fun browsers(): List<InstalledBrowser> = runCatching {
        val manager = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        manager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { InstalledBrowser(it.activityInfo.packageName, it.loadLabel(manager).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())

    /** The browser the system opens web pages with; null when it would ask, or cannot say. */
    private fun defaultBrowser(): String? = runCatching {
        context.packageManager.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://")), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }.getOrNull()?.takeIf { it != "android" && it != context.packageName }

    private fun view(url: String, browser: String?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (browser != null) intent.setPackage(browser)
        return start(intent)
    }

    private fun customTab(url: String, browser: String?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.putExtras(Bundle().apply { putBinder(CUSTOM_TABS_SESSION, null) })
        intent.putExtra(CUSTOM_TABS_SHARE_STATE, 0)
        if (browser != null) intent.setPackage(browser)
        return start(intent)
    }

    private fun start(intent: Intent): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    private companion object {
        /** The extra that makes a VIEW a Custom Tab (androidx.browser's CustomTabsIntent.EXTRA_SESSION). */
        const val CUSTOM_TABS_SESSION = "android.support.customtabs.extra.SESSION"

        /** The tab's share button as the browser sets it (CustomTabsIntent.EXTRA_SHARE_STATE, SHARE_STATE_DEFAULT). */
        const val CUSTOM_TABS_SHARE_STATE = "androidx.browser.customtabs.extra.SHARE_STATE"
    }
}
