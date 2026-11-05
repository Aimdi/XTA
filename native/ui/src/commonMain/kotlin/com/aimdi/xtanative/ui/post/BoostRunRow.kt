package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_boosts
import com.aimdi.xtanative.ui.generated.resources.posts_collapse
import com.aimdi.xtanative.ui.generated.resources.posts_expand
import org.jetbrains.compose.resources.stringResource

/**
 * A run of reposts as one row, when "collapse reposts" is on
 * (boost_run_carousel.dart): compact cards side by side, each naming whose
 * post it is; "Expand" lays the run out as full posts in place, "Collapse"
 * folds it again. A feed draws one per [BoostRuns.Item.Run].
 */
@Composable
fun BoostRunRow(run: BoostRuns.Item.Run, actions: PostActions) {
    var expanded by rememberSaveable(run.key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.posts_boosts),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) PostIcons.UnfoldLess else PostIcons.UnfoldMore, null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(if (expanded) Res.string.posts_collapse else Res.string.posts_expand),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        if (expanded) {
            run.threads.forEach { ThreadRows(it, actions) }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(88.dp),
            ) {
                items(run.threads, key = { it.id }) { thread -> BoostCard(thread, actions) }
            }
            Hairline()
        }
    }
}

@Composable
private fun BoostCard(thread: XThread, actions: PostActions) {
    val boost = thread.posts.firstOrNull() ?: return
    val shown = boost.shown
    val preview = BoostRuns.preview(shown.text)
    Row(
        Modifier
            .width(200.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { actions.openPost(shown.id) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(shown.author.largeAvatarUrl, 32.dp) { actions.openUser(shown.author.screenName, shown.author.id) }
        Column(Modifier.padding(start = 8.dp)) {
            Text(shown.author.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (preview.isNotEmpty()) {
                Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
