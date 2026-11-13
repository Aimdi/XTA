package com.aimdi.xtanative.ui.media

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import com.aimdi.xtanative.ui.common.findActivity

/** Turned to the video's orientation, upright or sideways either way up, with the system bars hidden until a swipe shows them a moment (immersiveSticky). */
@Composable
internal actual fun FullscreenWindow(aspectRatio: Float) {
    val activity = LocalContext.current.findActivity() ?: return
    val portrait = VideoRules.portrait(aspectRatio)
    DisposableEffect(activity, portrait) {
        val before = activity.requestedOrientation
        activity.requestedOrientation = if (portrait) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            bars.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = before
        }
    }
}

/**
 * The activity's picture-in-picture (video_pip.dart, MainActivity's
 * enterPictureInPicture), where the phone has it; the activity says when it
 * goes in or out, and out of it while stopped is its window closed.
 */
@Composable
internal actual fun rememberPictureInPicture(onDismissed: () -> Unit): PictureInPicture? {
    val activity = LocalContext.current.findActivity() as? ComponentActivity ?: return null
    val supported = remember(activity) { activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }
    if (!supported) return null
    val pip = remember(activity) { ActivityPictureInPicture(activity) }
    val dismissed by rememberUpdatedState(onDismissed)
    DisposableEffect(activity, pip) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            pip.active = info.isInPictureInPictureMode
            if (!info.isInPictureInPictureMode && !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) dismissed()
        }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return pip
}

private class ActivityPictureInPicture(private val activity: ComponentActivity) : PictureInPicture {
    override var active by mutableStateOf(activity.isInPictureInPictureMode)

    /** Android allows a window between 1:2.39 and 2.39:1 and throws outside it, or where picture-in-picture is off for the app. */
    override fun enter(aspectRatio: Float): Boolean {
        val params = PictureInPictureParams.Builder().setAspectRatio(Rational(VideoRules.pictureInPictureRatio(aspectRatio), 1000)).build()
        return try {
            activity.enterPictureInPictureMode(params)
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
