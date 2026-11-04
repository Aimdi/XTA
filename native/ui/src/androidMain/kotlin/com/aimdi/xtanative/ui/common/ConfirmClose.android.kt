package com.aimdi.xtanative.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberCloseApp(): () -> Unit {
    val activity = LocalContext.current.owningActivity()
    return remember(activity) { { activity?.finish() } }
}

private tailrec fun Context.owningActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.owningActivity()
    else -> null
}
