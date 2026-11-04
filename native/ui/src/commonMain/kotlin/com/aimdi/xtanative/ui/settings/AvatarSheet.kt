package com.aimdi.xtanative.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.store.settings.AccountPrefs
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.settings_avatar_choose
import com.aimdi.xtanative.ui.generated.resources.settings_avatar_clear
import com.aimdi.xtanative.ui.generated.resources.settings_avatar_description
import com.aimdi.xtanative.ui.generated.resources.settings_avatar_error
import com.aimdi.xtanative.ui.generated.resources.settings_avatar_from_account
import com.aimdi.xtanative.ui.generated.resources.settings_avatar_title
import com.aimdi.xtanative.ui.generated.resources.settings_unknown_username
import com.aimdi.xtanative.ui.search.DiscoverIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The picture in the upper left (XTA's showChromeAvatarSheet): one from the
 * device, the first account's X profile photo, or the monogram again once
 * there is a picture. A profile photo that cannot be loaded says so here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarSheet(onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val revision by graph.settings.observe(AccountPrefs.avatarRevision).collectAsStateWithLifecycle(graph.settings.state.value?.get(AccountPrefs.avatarRevision) ?: 0)
    val first = session?.accounts?.firstOrNull()
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    val choose = rememberImageChooser(stringResource(Res.string.settings_avatar_choose)) { read ->
        scope.launch {
            read()?.let { graph.avatar.set(it) }
            onDismiss()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Text(stringResource(Res.string.settings_avatar_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Text(
                stringResource(Res.string.settings_avatar_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            NavigationRow(stringResource(Res.string.settings_avatar_choose), icon = GroupIcons.PhotoLibraryOutlined, chevron = false, onClick = choose)
            if (first != null) {
                NavigationRow(
                    stringResource(Res.string.settings_avatar_from_account),
                    "@" + (first.screenName ?: stringResource(Res.string.settings_unknown_username)),
                    DiscoverIcons.CloudDownload,
                    chevron = false,
                ) {
                    val name = first.screenName?.takeIf { it.isNotEmpty() } ?: return@NavigationRow
                    failed = false
                    scope.launch {
                        if (runCatching { graph.avatar.fromProfile(graph.client, name) }.isSuccess) onDismiss() else failed = true
                    }
                }
            }
            if (revision > 0) {
                NavigationRow(stringResource(Res.string.settings_avatar_clear), icon = Icons.Outlined.AccountCircle, chevron = false) {
                    scope.launch {
                        graph.avatar.clear()
                        onDismiss()
                    }
                }
            }
            if (failed) {
                Text(
                    stringResource(Res.string.settings_avatar_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
