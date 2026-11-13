package com.aimdi.xtanative.ui.plugins

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Android 12's Open by default page for the app, else its app info page (pixiv_settings_browsing.dart's openPixivLinkSettings). */
@Composable
actual fun rememberAppLinkSettings(): (() -> Unit)? {
    val context = LocalContext.current
    return remember(context) { { openLinkSettings(context) } }
}

private fun openLinkSettings(context: Context) {
    val app = Uri.parse("package:" + context.packageName)
    listOf(OPEN_BY_DEFAULT, Settings.ACTION_APPLICATION_DETAILS_SETTINGS).any { action -> start(context, Intent(action, app)) }
}

private fun start(context: Context, intent: Intent): Boolean = try {
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/** Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, which Android 12 added, written out so no older SDK's lack of it matters. */
private const val OPEN_BY_DEFAULT = "android.settings.APP_OPEN_BY_DEFAULT_SETTINGS"
