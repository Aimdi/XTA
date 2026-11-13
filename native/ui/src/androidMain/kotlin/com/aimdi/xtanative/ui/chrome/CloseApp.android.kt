package com.aimdi.xtanative.ui.chrome

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberCloseApp(): () -> Unit {
    val context = LocalContext.current
    return remember(context) { ActivityFinish(context) }
}

/** Finishes the activity [context] belongs to, as Flutter's SystemNavigator.pop finishes XTA's. */
private class ActivityFinish(private val context: Context) : () -> Unit {
    override fun invoke() {
        context.activity()?.finish()
    }
}

/** The activity [this] context belongs to, through the wrappers around it. */
private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
