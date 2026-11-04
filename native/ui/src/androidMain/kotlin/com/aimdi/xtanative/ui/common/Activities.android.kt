package com.aimdi.xtanative.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The activity a composition's context belongs to, through its wrappers; null for one of none. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
