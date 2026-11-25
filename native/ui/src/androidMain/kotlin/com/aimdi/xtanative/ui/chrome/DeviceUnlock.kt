package com.aimdi.xtanative.ui.chrome

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/**
 * The phone's own unlock, asked of the reader for the app's lock
 * ([AppLockRules]): the fingerprint or face the phone knows, or its screen
 * lock. The app sees only whether it passed. From Android 11 the system's
 * prompt takes either; before, its confirm-your-lock page does, whose answer
 * [activity] hands to [onResult].
 */
class DeviceUnlock(private val activity: Activity, private val onUnlocked: () -> Unit) {
    private val keyguard: KeyguardManager? = activity.getSystemService(KeyguardManager::class.java)
    private var asking: CancellationSignal? = null

    /** Whether the phone has a screen lock at all; without one there is nothing to ask for. */
    val secure: Boolean get() = keyguard?.isDeviceSecure == true

    /** Asks, under [title]; a second asking while one is up is left out. A phone that cannot ask is let in. */
    fun ask(title: String) {
        if (asking != null) return
        if (!secure) return onUnlocked()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) prompt(title) else confirm(title)
        } catch (_: RuntimeException) {
            // No way to ask on this phone: the Unlock button asks again.
            asking = null
        }
    }

    /** The answer of the confirm-your-lock page; true when [requestCode] was this one's. */
    fun onResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQUEST) return false
        asking = null
        if (resultCode == Activity.RESULT_OK) onUnlocked()
        return true
    }

    /** Takes a prompt still up away, as its activity goes. */
    fun cancel() {
        asking?.cancel()
        asking = null
    }

    private fun prompt(title: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val signal = CancellationSignal().also { asking = it }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                asking = null
                onUnlocked()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                asking = null
                // The screen lock was taken off the phone meanwhile: nothing is left to ask for.
                if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL && !secure) onUnlocked()
            }
        }
        BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
            .authenticate(signal, activity.mainExecutor, callback)
    }

    @Suppress("DEPRECATION")
    private fun confirm(title: String) {
        val intent = keyguard?.createConfirmDeviceCredentialIntent(title, null) ?: return onUnlocked()
        asking = CancellationSignal()
        activity.startActivityForResult(intent, REQUEST)
    }

    private companion object {
        const val REQUEST = 4_203
    }
}
