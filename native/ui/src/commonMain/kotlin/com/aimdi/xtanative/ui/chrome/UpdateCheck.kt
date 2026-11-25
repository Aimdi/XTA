package com.aimdi.xtanative.ui.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.UpdatePrefs
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.chrome_update_available
import com.aimdi.xtanative.ui.common.pointerOnlyFocus
import com.aimdi.xtanative.ui.common.rememberCopyText
import com.aimdi.xtanative.ui.generated.resources.chrome_update_copy
import com.aimdi.xtanative.ui.generated.resources.chrome_update_download
import com.aimdi.xtanative.ui.generated.resources.chrome_update_install_package
import com.aimdi.xtanative.ui.generated.resources.chrome_update_install_tarball
import com.aimdi.xtanative.ui.generated.resources.chrome_update_skip
import com.aimdi.xtanative.ui.generated.resources.chrome_update_view
import com.aimdi.xtanative.ui.generated.resources.chrome_update_view_version
import com.aimdi.xtanative.ui.generated.resources.dismiss
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.speech.VoiceSources
import java.util.concurrent.atomic.AtomicBoolean
import org.jetbrains.compose.resources.stringResource

/** The check runs once a launch, as XTA's _updateDialogShown keeps it: not again for a screen made anew. */
private val checked = AtomicBoolean(false)

/** The file that updates this install: Android's APK, or the desktop's tarball or package, by where it runs from. */
@Composable
internal expect fun updateFile(): UpdateFile

/**
 * XTA's update check: once a launch, once the first-launch cards have been
 * seen, while Settings › General › Check for updates is on (turning it on
 * checks at once), the newest XTA Native release newer than this build
 * (for a prerelease's install, the newest release or prerelease)
 * is offered as XTA offers its own release, its page on GitHub, and with it
 * the file that updates this install (the APK; the tarball or the Arch
 * package), both opened in the browser. On the desktop it says how to
 * install that file (a command to copy), and any offer can be skipped: that
 * version is not offered again, a newer one is.
 */
@Composable
fun UpdateCheck() {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val due = settings?.let { it[GeneralPrefs.checkForUpdates] && it[GeneralPrefs.introSeen] } == true
    val file = updateFile()
    var offered by remember { mutableStateOf<NativeRelease?>(null) }
    LaunchedEffect(due) {
        if (!due || !checked.compareAndSet(false, true)) return@LaunchedEffect
        val installedTag = System.getProperty(VoiceSources.RELEASE_TAG_PROPERTY).orEmpty()
        val skipped = settings?.get(UpdatePrefs.skippedTag).orEmpty()
        offered = NativeUpdates.check(OkHttpXHttp(), file, installedTag, graph.version)?.takeIf { NativeUpdates.offered(it, skipped) }
    }
    offered?.let { release ->
        val command = release.fileNameFor(file)?.let { NativeUpdates.installCommand(file, it) }
        UpdateOffer(
            release = release,
            file = release.fileFor(file),
            install = command?.let { file to it },
            onSkip = {
                offered = null
                graph.keep { graph.settings.set(UpdatePrefs.skippedTag, release.tag) }
            },
        ) { offered = null }
    }
}

@Composable
private fun UpdateOffer(release: NativeRelease, file: String?, install: Pair<UpdateFile, String>?, onSkip: () -> Unit, onDismiss: () -> Unit) {
    val links = LocalLinks.current
    val copyText = rememberCopyText()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.chrome_update_available)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.chrome_update_view_version, release.version.joinToString(".")))
                install?.let { (kind, command) ->
                    Text(stringResource(if (kind == UpdateFile.PACKAGE) Res.string.chrome_update_install_package else Res.string.chrome_update_install_tarball))
                    SelectionContainer(Modifier.pointerOnlyFocus()) { Text(command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { copyText(command) }) { Text(stringResource(Res.string.chrome_update_copy)) }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text(stringResource(Res.string.chrome_update_skip)) }
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dismiss)) }
        },
        confirmButton = {
            TextButton(onClick = {
                links?.openOutside(release.page)
                onDismiss()
            }) { Text(stringResource(Res.string.chrome_update_view)) }
            if (file != null) {
                TextButton(onClick = {
                    links?.openOutside(file)
                    onDismiss()
                }) { Text(stringResource(Res.string.chrome_update_download)) }
            }
        },
    )
}
