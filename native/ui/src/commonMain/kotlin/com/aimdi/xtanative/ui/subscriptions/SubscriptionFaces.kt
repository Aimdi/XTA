package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_search_term
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import com.aimdi.xtanative.ui.pluginstore.PluginMark
import com.aimdi.xtanative.ui.plugins.MarkedAvatar
import org.jetbrains.compose.resources.stringResource

/**
 * What stands beside a followed thing's name (subscription_look.dart): an X
 * account's face, a search's mark, and a plugin's account's face with its
 * network's mark at its foot, or that mark alone where it has no picture.
 */
@Composable
fun SubscriptionFace(row: SubscriptionRow, size: Dp) {
    when {
        row.search -> Box(Modifier.size(size), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Search, contentDescription = null) }
        row.plugin != null -> PluginAccountFace(row, size)
        else -> Avatar(row.avatarUrl, size)
    }
}

@Composable
private fun PluginAccountFace(row: SubscriptionRow, size: Dp) {
    val running by LocalPlugins.current.running.collectAsStateWithLifecycle()
    val owner = running.firstOrNull { it.plugin.id == row.plugin }
    val url = row.avatarUrl
    if (url == null) {
        PluginMark(owner, row.name, size)
    } else {
        MarkedAvatar(url, owner?.plugin?.icon, owner?.plugin?.brandColor ?: Color.Unspecified, size)
    }
}

/** The line under a followed thing's name: an account's handle, what a plugin says of its own, or that it is a search. */
@Composable
fun subscriptionSubtitle(row: SubscriptionRow): String =
    if (row.search) stringResource(Res.string.groups_search_term) else row.subtitle ?: "@${row.screenName}"
