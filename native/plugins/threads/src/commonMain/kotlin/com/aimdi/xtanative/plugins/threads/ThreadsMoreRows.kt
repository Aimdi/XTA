package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsPaging
import com.aimdi.xtanative.networks.threads.ThreadsPagingState
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.RetryRow
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Asks [paging] for its next page once the reader is within a few rows of
 * [list]'s end, and again as each page lands while they are still there: a
 * page a tab's filter shows nothing of must not stop the list.
 */
@Composable
internal fun ReadsMoreNear(list: LazyListState, paging: ThreadsPaging<*>?, state: ThreadsPagingState<*>?) {
    LaunchedEffect(list, paging, state?.items?.size, state?.next) {
        if (paging == null || state?.next == null) return@LaunchedEffect
        snapshotFlow {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - MORE_WITHIN
        }
            .distinctUntilChanged()
            .collect { near -> if (near) paging.more() }
    }
}

/** The row under a paged list: a spinner while its next page is read, why that failed and a way to ask again, or that it has ended. */
internal fun LazyListScope.moreFoot(key: String, state: ThreadsPagingState<*>?, onRetry: () -> Unit) {
    val problem = state?.problem
    when {
        state?.loading == true -> item(key = "$key-more-loading") { LoadingRow() }
        problem != null -> item(key = "$key-more-problem") { RetryRow(ThreadsFailures.nextPage(problem, currentLanguage()), onRetry) }
        state?.paged == true && state.next == null && !state.onWeb -> item(key = "$key-more-end") { ListEnd() }
    }
}

@Composable
private fun ListEnd() {
    Text(
        pluginString(Strings.list_end),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
    )
}

private const val MORE_WITHIN = 5
