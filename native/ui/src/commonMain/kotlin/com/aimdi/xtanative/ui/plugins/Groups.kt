package com.aimdi.xtanative.ui.plugins

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_add_to_group
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.group.DiscoveryIcons
import org.jetbrains.compose.resources.stringResource

/**
 * "Add to group" for one of the plugin's accounts, as a bar's button (a
 * profile's, a community's): the app's group picker, [PluginHost.addToGroup];
 * [follow] follows it in the plugin once a group is picked.
 */
@Composable
fun AddToGroupButton(host: PluginHost, account: String, follow: suspend () -> Unit = {}) {
    AddToGroupButton(host, account, stringResource(Res.string.posts_add_to_group), follow)
}

/** The same button named in the plugin's own words ([label], RSS's "Add to a group"). */
@Composable
fun AddToGroupButton(host: PluginHost, account: String, label: String, follow: suspend () -> Unit = {}) {
    IconButton(onClick = { host.addToGroup(account, follow) }) { Icon(AddToGroupIcon, label) }
}

/** The glyph XTA's plugins draw "Add to group" with: Material's group_add, outlined. */
val AddToGroupIcon: ImageVector get() = DiscoveryIcons.GroupAddOutlined

/** "Add to group" in the reader's language, for a plugin's own button, chip or menu entry that opens [PluginHost.addToGroup]. */
@Composable
fun addToGroupText(): String = stringResource(Res.string.posts_add_to_group)
