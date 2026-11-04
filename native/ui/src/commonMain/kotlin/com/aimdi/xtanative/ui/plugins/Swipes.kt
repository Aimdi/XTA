package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.search.DiscoverIcons

/**
 * [content], a row of an account the plugin follows, swiped from its end
 * over the theme's error colour and the remove-person glyph to unfollow it,
 * as XTA's lists of followed accounts do (TikTok's, Instagram's): the swipe
 * asks [onUnfollow], which confirms first, and the row springs back
 * ([SwipeRules]). Give [content] the list's background, so the colour
 * shows only under the swipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToUnfollow(onUnfollow: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { value -> SwipeRules.settles(value == SwipeToDismissBoxValue.EndToStart, onUnfollow) })
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(DiscoverIcons.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
            }
        },
        content = content,
    )
}
