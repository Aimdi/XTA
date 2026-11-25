package com.aimdi.xtanative.ui.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.app_lock_title
import com.aimdi.xtanative.ui.generated.resources.app_lock_unlock
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › General › Lock the app, on Android: opening the app asks for
 * the phone's fingerprint, face or screen lock, and so does coming back to
 * it after [GRACE_MILLIS] out of sight. A phone with no screen lock has
 * nothing to ask for, so there the app stays open.
 */
object AppLockRules {
    /** A glance at another app and back does not ask again. */
    const val GRACE_MILLIS = 60_000L

    /**
     * Whether the app locks as it comes into sight: [awayMillis] is how long
     * it was out of sight, null when this run has not shown it yet.
     */
    fun locks(enabled: Boolean, deviceSecure: Boolean, awayMillis: Long?): Boolean =
        enabled && deviceSecure && (awayMillis == null || awayMillis >= GRACE_MILLIS)
}

/**
 * Where this run of the app stands with its lock, shared by the activities
 * that show the screens and the lock. On the main thread only.
 */
object AppLockSession {
    /** Locked: the screens stay behind the lock page until the reader unlocks. */
    var locked by mutableStateOf(false)
        private set

    private var shown = false
    private var leftAt: Long? = null

    /** The screens come into sight at [now] (a clock that runs through sleep): locks when [AppLockRules] say so. Whether it is locked. */
    fun arrive(enabled: Boolean, deviceSecure: Boolean, now: Long): Boolean {
        val away = if (shown) leftAt?.let { now - it } ?: 0 else null
        shown = true
        if (!locked && AppLockRules.locks(enabled, deviceSecure, away)) locked = true
        if (!locked) leftAt = null
        return locked
    }

    /** The screens go out of sight at [now]; while locked, the time they first did stands. */
    fun leave(now: Long) {
        if (!locked && leftAt == null) leftAt = now
    }

    /** The reader unlocked. */
    fun unlock() {
        locked = false
        leftAt = null
    }

    /** As a new run of the app starts, for tests. */
    internal fun reset() {
        locked = false
        shown = false
        leftAt = null
    }
}

/**
 * The page a locked app shows in place of its screens: that it is locked,
 * and a button asking the phone to unlock again, for a reader who backed out
 * of the first asking. [asksAtOnce] asks as the page appears; [onUnlock]
 * takes the words the phone's own prompt is titled with.
 */
@Composable
fun AppLockPage(asksAtOnce: Boolean, onUnlock: (title: String) -> Unit) {
    val title = stringResource(Res.string.app_lock_title)
    LaunchedEffect(Unit) { if (asksAtOnce) onUnlock(title) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, Modifier.padding(top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onUnlock(title) }) { Text(stringResource(Res.string.app_lock_unlock)) }
        }
    }
}
