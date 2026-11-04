package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.booru.BooruEndpoints
import com.aimdi.xtanative.networks.booru.BooruTagOperator
import com.aimdi.xtanative.networks.booru.BooruText
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.addToGroupText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

/**
 * What can be done with one of a post's tags (booru_tag_sheet.dart): search
 * it; add it to, or exclude it from, the [search] the post was opened from;
 * follow it, or put it in groups as XTA's subscriptions put a followed tag;
 * hide it; read its wiki on [site] (not Gelbooru); copy it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagActions(app: BooruApp, opened: OpenedTag, search: BooruSearch?, site: SiteRef?, onDismiss: () -> Unit) {
    val tag = opened.tag
    val followed = app.follows.state.collectAsState().value.orEmpty().contains(tag)
    val settings by app.settings.collectAsState()
    val hidden = tag in BooruConfig.muted(settings ?: app.current())
    var wiki by remember { mutableStateOf(false) }
    val outside = rememberOutside()
    val copied = copiedNotice(app)
    val done = { action: () -> Unit -> action(); onDismiss() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text(tokenText(tag, opened.category), style = MaterialTheme.typography.titleMedium) },
                supportingContent = opened.category?.let { { Text(kindLabel(it)) } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
            Hairline()
            Action(Icons.Filled.Search, pluginString(Strings.search_tag)) { done { app.openSearch(tag) } }
            if (search != null) {
                Action(Icons.Filled.Add, pluginString(Strings.add_to_search)) { done { search.refine(tag); app.host.back() } }
                Action(BooruIcons.Remove, pluginString(Strings.exclude_from_search)) { done { search.refine(BooruTagOperator.EXCLUDE.prefix + tag); app.host.back() } }
            }
            Action(if (followed) Icons.Filled.Check else BooruIcons.Sell, pluginString(if (followed) Strings.unfollow_tag else Strings.follow_tag)) {
                done { app.launch { if (followed) app.unfollow(tag) else app.follows.add(tag) } }
            }
            Action(PostIcons.People, addToGroupText()) { done { app.addToGroup(tag) } }
            Action(if (hidden) BooruIcons.Visibility else GroupIcons.VisibilityOffOutlined, pluginString(if (hidden) Strings.unhide_tag else Strings.hide_tag)) {
                done { app.update { if (hidden) BooruConfig.withoutMuted(it, tag) else BooruConfig.withMuted(it, tag) } }
            }
            if (BooruEndpoints.supportsWiki(app.site(site).engine)) Action(BooruIcons.Wiki, pluginString(Strings.wiki)) { wiki = true }
            Action(BooruIcons.Copy, pluginString(Strings.copy_tag)) { done { outside.copy(tag, copied) } }
        }
    }
    if (wiki) WikiSheet(app, tag, site, onDismiss = { wiki = false; onDismiss() })
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
}

/** [tag]'s wiki page on [site], as plain text; a tag without one says so. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WikiSheet(app: BooruApp, tag: String, site: SiteRef?, onDismiss: () -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    val page = produceState<Result<String?>?>(null, attempt) {
        value = null
        value = loadWiki(app, tag, site)
    }.value
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 200.dp).navigationBarsPadding()) {
            val body = page?.getOrNull()
            when {
                page == null -> LoadingRow()
                page.isFailure -> RetryMessage(errorText(page.exceptionOrNull()), onRetry = { attempt++ })
                body.isNullOrEmpty() -> EmptyPane(BooruIcons.Wiki, pluginString(Strings.wiki_empty))
                else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                    Text(tag, style = MaterialTheme.typography.titleLarge)
                    SelectionContainer(Modifier.padding(top = 12.dp)) { Text(BooruText.plain(body), style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

private suspend fun loadWiki(app: BooruApp, tag: String, site: SiteRef?): Result<String?> = try {
    Result.success(app.client.wiki(tag, app.site(site)))
} catch (error: kotlin.coroutines.cancellation.CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}
