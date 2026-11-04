package com.aimdi.xtanative.ui.offline

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.io.File

/**
 * A video kept on the device, playing while [active]: Android plays it in
 * place with its controls, and the desktop with the viewer's through libmpv;
 * a desktop without libmpv hands it to the system's player.
 */
@Composable
internal expect fun OfflineVideo(file: File, active: Boolean, modifier: Modifier)
