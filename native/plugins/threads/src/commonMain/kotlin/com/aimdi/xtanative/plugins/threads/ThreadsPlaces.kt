package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.plugins.pluginPlural
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val POST_KEY = "post:"

/** The post a list row's [key] names (`post:<id>`); null for a row that is none. */
internal fun postIdOf(key: Any?): String? = (key as? String)?.takeIf { it.startsWith(POST_KEY) }?.removePrefix(POST_KEY)

/** Tells [onPlace] which post is first in view, so a section reopens there, now and after a restart. */
@Composable
internal fun KeepsPlace(list: LazyListState, onPlace: (ThreadsPlace) -> Unit) {
    LaunchedEffect(list) {
        snapshotFlow {
            val first = list.layoutInfo.visibleItemsInfo.firstOrNull { postIdOf(it.key) != null }
            // Above the first post there is only what stands over the feed: its top.
            if (first == null || list.firstVisibleItemIndex == 0) ThreadsPlace() else ThreadsPlace(postIdOf(first.key), -first.offset)
        }.distinctUntilChanged().collect(onPlace)
    }
}

/**
 * "↑ N new posts" over a feed while posts a read brought in wait above the
 * reader ([shown], the feed's post ids in order); a tap goes to the top,
 * and reaching it any way puts the pill away.
 */
@Composable
internal fun FreshPill(list: LazyListState, shown: List<String>, padding: PaddingValues, modifier: Modifier = Modifier) {
    var before by remember { mutableStateOf<List<String>?>(null) }
    var fresh by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(shown) {
        fresh = fresh + ThreadsFresh.arrived(before, shown)
        before = shown
    }
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex == 0 }.collect { top -> if (top) fresh = emptySet() }
    }
    val count by remember(shown, fresh) {
        derivedStateOf {
            val first = list.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { postIdOf(it.key) }
            ThreadsFresh.above(shown, fresh, first?.let(shown::indexOf) ?: 0)
        }
    }
    val scope = rememberCoroutineScope()
    if (count == 0) return
    Button(
        onClick = { scope.launch { list.animateScrollToItem(0) } },
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        modifier = modifier.padding(top = padding.calculateTopPadding() + 8.dp),
    ) {
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(pluginPlural(Strings.new_posts, count, count), modifier = Modifier.padding(start = 4.dp))
    }
}
