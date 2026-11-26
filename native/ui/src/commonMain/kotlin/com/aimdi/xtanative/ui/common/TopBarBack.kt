package com.aimdi.xtanative.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.home.isFrameRoot
import org.jetbrains.compose.resources.stringResource
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back

/**
 * A top bar's Back arrow, [label] its tip, going back with [onBack]; none on
 * a screen the desktop frame's navigation leads to straight ([isFrameRoot]),
 * where the column or rail beside it is the way on.
 */
@Composable
fun TopBarBack(onBack: () -> Unit, label: String = stringResource(Res.string.back)) {
    if (isFrameRoot()) return
    IconTip(label) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, label) } }
}
