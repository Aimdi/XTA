package com.aimdi.xtanative

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.aimdi.xtanative.ui.chrome.AppLockPage
import com.aimdi.xtanative.ui.chrome.AppLockSession
import com.aimdi.xtanative.ui.chrome.DeviceUnlock
import com.aimdi.xtanative.ui.common.LocalSettingsStore
import com.aimdi.xtanative.ui.theme.XtaTheme

/**
 * The lock page (Settings › General › Lock the app), over the screens and
 * whatever sheet or dialog they had open: it asks the phone to unlock as it
 * appears and goes once that passed. Back leaves the app rather than the
 * lock, and the page is kept out of screenshots and the recents' preview.
 */
class LockActivity : ComponentActivity() {
    private lateinit var unlock: DeviceUnlock

    /** What the phone's prompt was last titled with, to ask again under. */
    private var title: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        unlock = DeviceUnlock(this, ::unlocked)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
        val graph = (application as XtaNativeApp).graph
        setContent {
            CompositionLocalProvider(LocalSettingsStore provides graph.settings) {
                XtaTheme { AppLockPage(asksAtOnce = savedInstanceState == null, onUnlock = ::ask) }
            }
        }
    }

    /**
     * A lock page left under screens unlocked since has nothing more to
     * keep. One come back to asks again by itself where the phone's prompt
     * stands over this page (Android 11 and later); the older confirm page
     * replaces it, so there the button asks.
     */
    override fun onStart() {
        super.onStart()
        when {
            !AppLockSession.locked -> close()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> title?.let(unlock::ask)
        }
    }

    private fun ask(title: String) {
        this.title = title
        unlock.ask(title)
    }

    override fun onDestroy() {
        unlock.cancel()
        super.onDestroy()
    }

    @Deprecated("The confirm-your-lock page of Android 10 and earlier answers here.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        if (!unlock.onResult(requestCode, resultCode)) super.onActivityResult(requestCode, resultCode, data)
    }

    private fun unlocked() {
        AppLockSession.unlock()
        close()
    }

    private fun close() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
