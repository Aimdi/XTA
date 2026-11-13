package com.aimdi.xtanative.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.new_posts
import org.jetbrains.compose.resources.pluralStringResource

/** The top of the scroll of one of several lists ([page]), asked for by tapping a title; [count] tells the requests apart. */
internal data class ToTop(val page: Int, val count: Int)

/** "↑ 12 new posts": the posts the plugins brought while the reader was down the list, which wait for them. */
@Composable
internal fun NewPostsPill(count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(
            pluralStringResource(Res.plurals.new_posts, count, count),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
