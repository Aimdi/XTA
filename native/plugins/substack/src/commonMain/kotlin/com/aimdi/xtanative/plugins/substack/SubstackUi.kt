package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.substack.SubstackPublication
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.addToGroupText
import com.aimdi.xtanative.ui.plugins.networkProblemText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.problemOf
import com.aimdi.xtanative.ui.post.PostIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Add to group for [publication] (substack_group.dart), following it here
 * once a group is picked: [outlined] as a publication's page and its similar
 * ones draw it, else as a search's and the add screen's rows do.
 */
@Composable
internal fun GroupButton(app: SubstackApp, publication: SubstackPublication, outlined: Boolean = true, enabled: Boolean = true) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(PostIcons.GroupAdd, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(addToGroupText())
    }
    if (outlined) {
        OutlinedButton(onClick = { app.addToGroup(publication) }, enabled = enabled, content = content)
    } else {
        TextButton(onClick = { app.addToGroup(publication) }, enabled = enabled, content = content)
    }
}

/** A publication's logo in [shape], or [fallback] (the newspaper mark) where it has none. */
@Composable
internal fun PublicationLogo(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    fallback: ImageVector = SubstackIcons.Newspaper,
    fallbackSize: Dp = size / 2,
) {
    val frame = modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
    if (url.isNullOrEmpty()) {
        Box(frame, contentAlignment = Alignment.Center) {
            Icon(fallback, null, Modifier.size(fallbackSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = frame)
    }
}

/** What a pane shows in place of its list: a mark, a title, a line, and what to do about it. */
@Composable
internal fun EmptyState(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null, description: String? = null, actions: @Composable ColumnScope.() -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let { Icon(it, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.outline) }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        actions()
    }
}

/**
 * A failure in a row where what failed would have been: why, in plain words,
 * and a way to try again. [message] is XTA's own sentence when the row is a
 * reload over something the reader can keep reading.
 */
@Composable
internal fun FailureRow(error: Throwable, onRetry: () -> Unit, modifier: Modifier = Modifier, message: String? = null) {
    Column(modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            message ?: networkProblemText(problemOf(error), NETWORK),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text(pluginString(Strings.retry)) }
    }
}

/** The end of a list read page by page: the spinner, the next page's failure, its Load more button, or nothing. */
@Composable
internal fun LoadMoreRow(loading: Boolean, error: Throwable?, canLoadMore: Boolean, onLoadMore: () -> Unit, enabled: Boolean = true) {
    when {
        loading -> LoadingRow()
        error != null -> FailureRow(error, onLoadMore)
        canLoadMore -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            OutlinedButton(onClick = onLoadMore, enabled = enabled) { Text(pluginString(Strings.load_more)) }
        }
    }
}

/** A sheet's title, with its close button. */
@Composable
internal fun SheetHeading(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, pluginString(Strings.close)) }
    }
}

/** Where a screen of the plugin says what happened (a follow, an export), as XTA's snack bars do. */
internal class Notices(val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(text: String) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(text)
        }
    }
}

@Composable
internal fun rememberNotices(): Notices {
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    return remember(host, scope) { Notices(host, scope) }
}

/** [content] with [notices] shown over its bottom. */
@Composable
internal fun WithNotices(notices: Notices, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize()) {
        content()
        SnackbarHost(notices.host, Modifier.align(Alignment.BottomCenter).padding(8.dp))
    }
}

internal const val NETWORK = "Substack"
