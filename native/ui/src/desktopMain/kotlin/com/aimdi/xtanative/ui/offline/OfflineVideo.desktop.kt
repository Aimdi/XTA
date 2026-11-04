package com.aimdi.xtanative.ui.offline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.store.downloads.mimeTypeFor
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.downloads_open_file
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import java.io.File
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The desktop has no player of its own: a click hands the kept video to the one the system has. */
@Composable
internal actual fun OfflineVideo(file: File, active: Boolean, modifier: Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val open = stringResource(Res.string.downloads_open_file)
    Box(
        modifier.clickable(role = Role.Button, onClickLabel = open) {
            scope.launch { if (!graph.platformFiles.openFile(file, mimeTypeFor(file.name))) KeepNotices.show(KeepNotice.OpenFailed) }
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            Text(open, color = Color.White, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
