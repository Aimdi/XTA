package com.aimdi.xtanative.ui.chrome

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.chrome_update_available
import com.aimdi.xtanative.ui.generated.resources.chrome_update_download
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
 * checks at once), the newest XTA Native prerelease newer than this build
 * is offered as XTA offers its own release, its page on GitHub, and with it
 * the file that updates this install (the APK; the tarball or the Arch
 * package), both opened in the browser.
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
        offered = NativeUpdates.check(OkHttpXHttp(), file, installedTag, graph.version)
    }
    offered?.let { release -> UpdateOffer(release, release.fileFor(file)) { offered = null } }
}

@Composable
private fun UpdateOffer(release: NativeRelease, file: String?, onDismiss: () -> Unit) {
    val links = LocalLinks.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.chrome_update_available)) },
        text = { Text(stringResource(Res.string.chrome_update_view_version, release.version.joinToString("."))) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dismiss)) } },
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
