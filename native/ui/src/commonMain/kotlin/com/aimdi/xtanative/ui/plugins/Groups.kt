package com.aimdi.xtanative.ui.plugins

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_add_to_group
import com.aimdi.xtanative.ui.post.PostIcons
import org.jetbrains.compose.resources.stringResource

/**
 * "Add to group" for one of the plugin's accounts, as a bar's button (a
 * profile's, a community's): the app's group picker, [PluginHost.addToGroup];
 * [follow] follows it in the plugin once a group is picked.
 */
@Composable
fun AddToGroupButton(host: PluginHost, account: String, follow: suspend () -> Unit = {}) {
    IconButton(onClick = { host.addToGroup(account, follow) }) { Icon(PostIcons.People, stringResource(Res.string.posts_add_to_group)) }
}
