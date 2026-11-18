package com.aimdi.xtanative.ui.feed

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_collapse
import com.aimdi.xtanative.ui.generated.resources.posts_link_more
import com.aimdi.xtanative.ui.post.PostIcons
import org.jetbrains.compose.resources.stringResource

/**
 * The posts sharing one article (link_stack.dart): the newest, then "More
 * posts sharing this link: N", which lays the others out under it, and
 * "Collapse", which folds them again. [draw] draws a post from its key.
 */
@Composable
fun LinkStackRow(keys: List<String>, draw: @Composable (String) -> Unit) {
    var open by rememberSaveable(keys.first()) { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    Column(Modifier.fillMaxWidth().then(if (reduced) Modifier else Modifier.animateContentSize())) {
        key(keys.first()) { draw(keys.first()) }
        TextButton(onClick = { open = !open }, modifier = Modifier.padding(horizontal = 16.dp)) {
            Icon(if (open) PostIcons.UnfoldLess else XIcons.Link, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                if (open) stringResource(Res.string.posts_collapse) else stringResource(Res.string.posts_link_more, keys.size - 1),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (open) keys.drop(1).forEach { other -> key(other) { draw(other) } }
    }
}
