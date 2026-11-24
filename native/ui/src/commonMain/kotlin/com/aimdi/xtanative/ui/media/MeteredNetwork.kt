package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Whether the network in use is metered (mobile data, a metered Wi-Fi
 * hotspot), followed as it changes while the caller is drawn; never on the
 * desktop.
 */
@Composable
internal expect fun rememberMeteredNetwork(): State<Boolean>

private val NOT_SAVING: State<Boolean> = mutableStateOf(false)

/**
 * Whether Settings › Media's "Save data on mobile networks" saves data now
 * ([DataSaving.onMeteredNetwork]): followed once near the app's root
 * ([rememberSavesMobileData]) for every tile under it; never outside it.
 */
internal val LocalSavesMobileData = compositionLocalOf { NOT_SAVING }

/** [LocalSavesMobileData] now: read where a picture's size or a video's start is decided. */
@Composable
@ReadOnlyComposable
internal fun savesMobileData(): Boolean = LocalSavesMobileData.current.value

/**
 * What [LocalSavesMobileData] is given: the network is followed only while
 * the reader's switch is on ([setting]), so nothing listens while it is off.
 */
@Composable
internal fun rememberSavesMobileData(setting: Boolean): State<Boolean> =
    if (setting) {
        val metered = rememberMeteredNetwork()
        remember(metered) { derivedStateOf { DataSaving.onMeteredNetwork(setting = true, metered = metered.value) } }
    } else {
        NOT_SAVING
    }
