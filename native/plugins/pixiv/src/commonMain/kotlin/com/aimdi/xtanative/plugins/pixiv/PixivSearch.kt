package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivLinkRef
import com.aimdi.xtanative.networks.pixiv.PixivLinks
import com.aimdi.xtanative.networks.pixiv.PixivSearchKind
import com.aimdi.xtanative.networks.pixiv.PixivTrendTag
import com.aimdi.xtanative.networks.pixiv.Pixivision
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons
import kotlinx.coroutines.launch

/** The app's search over Pixiv works: the app draws the field, this the results of what was sent. */
internal class PixivAppSearch(private val runtime: () -> PixivRuntime) : PluginSearch() {
    override fun hint(language: String): String = Strings.search_hint.get(language)

    /** The app's search opens Pixiv's own (pixiv_plugin.dart's openSearch): suggestions, id shortcuts, pasted links, search by image, its landing. */
    override fun ownScreen(query: String?): String = PixivRoutes.of(PixivRoute.Search(query.orEmpty(), PixivSearchKind.WORKS))

    @Composable
    override fun Results(query: String, frame: PluginFrame) {
        PixivRoot(runtime()) {
            if (!PixivAccounts.signedIn(pixivSettings())) {
                PixivEmpty(pluginString(Strings.not_configured))
            } else {
                val model = rememberSearch(PixivSearchKind.WORKS, query)
                SearchBody(model, frame.padding, typing = null)
            }
        }
    }
}

/** The search as a screen of its own, opened on [query] (a tag, a link's search, the app's search): the field is its bar, beside the back arrow, as XTA's is the app bar's title. */
@Composable
internal fun PixivSearchScreen(query: String, kind: PixivSearchKind) {
    val runtime = LocalPixiv.current
    val below = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            PixivSearchBody(kind, query, PaddingValues(bottom = below), embedded = false, onBack = runtime.host::back)
        }
    }
}

/** The search with its own field, as the home tab's Search section and the search screen draw it; [onBack] puts a back arrow before the field. */
@Composable
internal fun PixivSearchBody(kind: PixivSearchKind, initial: String, padding: PaddingValues, embedded: Boolean, onBack: (() -> Unit)? = null) {
    val model = rememberSearch(kind, initial)
    val view by model.view.collectAsState()
    var text by remember(model) { mutableStateOf(model.view.value.typed.ifEmpty { initial }.let { TextFieldValue(it, TextRange(it.length)) }) }
    val show = { value: String -> text = TextFieldValue(value, TextRange(value.length)) }
    val submit = rememberSubmit(model, show)
    var byImage by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, pluginString(Strings.back)) } }
            Box(Modifier.weight(1f)) {
                SearchField(kind, text, autofocus = !embedded && initial.isEmpty(), onChange = { value ->
                    if (value.text != text.text) model.type(value.text)
                    text = value
                }, onPaste = { pasted -> pasteInto(model, text, pasted, show, submit)?.let { text = it } }, onImageSearch = { byImage = true }) { submit(text.text) }
            }
        }
        SearchBody(model, padding, typing = SearchTyping(view.picking, onPick = { tag -> show(model.pick(tag)) }, onSearch = submit))
    }
    if (byImage && kind.isWorks) {
        val sauce = rememberSauceSearch()
        SauceNaoSheet(sauce) {
            byImage = false
            sauce.reset()
        }
    }
}

/** What the field adds to the search body: the picker while typing, the landing before a search. */
private class SearchTyping(val picking: Boolean, val onPick: (PixivTrendTag) -> Unit, val onSearch: (String) -> Unit)

@Composable
private fun rememberSearch(kind: PixivSearchKind, query: String): PixivSearchModel {
    val runtime = LocalPixiv.current
    return rememberKept("pixiv-search-${kind.name}-$query") { scope ->
        PixivSearchModel(runtime, scope, kind).also { model ->
            val word = query.trim()
            if (word.isNotEmpty() && PixivTyping.numericId(word) == null) model.search(word) else if (word.isNotEmpty()) model.type(word)
            model.loadLanding()
        }
    }
}

/** Searches what was typed; a Pixiv link opens what it names instead. */
@Composable
private fun rememberSubmit(model: PixivSearchModel, show: (String) -> Unit): (String) -> Unit {
    val runtime = LocalPixiv.current
    val focus = LocalFocusManager.current
    return remember(model, runtime, focus) {
        { raw ->
            val word = raw.trim()
            val link = if (PixivTyping.numericId(word) == null) PixivLinks.parse(word) else null
            when {
                word.isEmpty() -> Unit
                link != null -> runtime.open(PixivRoutes.routeOf(link))
                else -> {
                    focus.clearFocus()
                    show(word)
                    model.search(word)
                }
            }
        }
    }
}

/** Pasted text goes where the cursor is; a link replaces the field and opens. Answers the field's new value, or null when a link took over. */
private fun pasteInto(model: PixivSearchModel, value: TextFieldValue, pasted: String, show: (String) -> Unit, submit: (String) -> Unit): TextFieldValue? {
    val clean = pasted.trim()
    if (clean.isEmpty()) return value
    val link = if (model.kind.isWorks || PixivTyping.numericId(clean) == null) PixivLinks.parse(clean) else null
    if (link != null) {
        show(clean)
        model.type(clean)
        submit(clean)
        return null
    }
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    val text = value.text.replaceRange(start, end, clean)
    model.type(text)
    return TextFieldValue(text, TextRange(start + clean.length))
}

/** The field: Paste, Search by image for works (pixiv_search_screen.dart), then Search. */
@Composable
private fun SearchField(
    kind: PixivSearchKind,
    value: TextFieldValue,
    autofocus: Boolean,
    onChange: (TextFieldValue) -> Unit,
    onPaste: (String) -> Unit,
    onImageSearch: () -> Unit,
    onSubmit: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(pluginString(if (kind.isWorks) Strings.search_hint else Strings.novel_search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        trailingIcon = {
            Row {
                IconButton(onClick = { scope.launch { clipboard.getClipEntry()?.plainText()?.let(onPaste) } }) { Icon(PixivIcons.ContentPaste, pluginString(Strings.search_paste)) }
                if (kind.isWorks) IconButton(onClick = onImageSearch) { Icon(PixivIcons.ImageSearch, pluginString(Strings.saucenao_title)) }
                IconButton(onClick = onSubmit) { Icon(Icons.Filled.Search, pluginString(Strings.search)) }
            }
        },
        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp).then(if (autofocus) Modifier.focusRequester(focus) else Modifier),
    )
    if (autofocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** The picker while typing, the landing before a search, then the results and the creators found. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBody(model: PixivSearchModel, padding: PaddingValues, typing: SearchTyping?) {
    val view by model.view.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        val picking = typing?.picking == true
        if (view.searched && !picking) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(pluginString(if (model.kind.isWorks) Strings.tab_illusts else Strings.profile_novels)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(pluginString(Strings.tab_users)) })
            }
        }
        Box(Modifier.weight(1f)) {
            when {
                picking -> SearchPicker(view, model.kind, typing!!.onPick)
                !view.searched -> if (typing != null) SearchLanding(model, view, padding, typing.onSearch)
                tab == 1 -> PixivUsersFeed(model.users, pluginString(Strings.search_empty), padding, novels = !model.kind.isWorks)
                else -> SearchResults(model, view, padding)
            }
        }
    }
}

@Composable
private fun SearchResults(model: PixivSearchModel, view: PixivSearchView, padding: PaddingValues) {
    val settings = pixivSettings()
    var sheet by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        SearchFilterBar(view, model.kind, PixivSearchMemory.fresh(settings), onChange = { model.applyFilter(it) }) { sheet = true }
        Box(Modifier.weight(1f)) {
            if (!model.kind.isWorks) {
                PixivNovelFeed(model.novels, pluginString(Strings.search_empty), padding)
            } else {
                PixivWorksGrid(model.works, pluginString(Strings.search_empty), padding = padding, header = {
                    if (view.previewMode) item(key = "preview-note", span = StaggeredGridItemSpan.FullLine) { PreviewNote() }
                    if (view.showsPopularStrip) item(key = "popular", span = StaggeredGridItemSpan.FullLine) { PopularStrip(view) }
                })
            }
        }
    }
    if (sheet) SearchFilterSheet(view, model.kind, onClose = { sheet = false }) { filter, remember -> model.applyFilter(filter, remember) }
}

/** Popular order without Premium: Pixiv's free preview, which ignores dates. */
@Composable
private fun PreviewNote() {
    val style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)) {
        Icon(PostIcons.Sparkle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(8.dp))
        Column {
            Text(pluginString(Strings.search_preview_note), style = style)
            Text(pluginString(Strings.search_preview_dates), style = style)
        }
    }
}

/** The popular works for the words searched, a strip over newest-first results. */
@Composable
private fun PopularStrip(view: PixivSearchView) {
    val runtime = LocalPixiv.current
    val works = pixivMutes().filter(view.popular)
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(pluginString(Strings.popular_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(works.size, key = { works[it].id }) { index ->
                PixivPicture(
                    works[index].thumbnailUrl,
                    Modifier.size(110.dp).clip(RoundedCornerShape(8.dp)).clickable { runtime.openWork(works, index) },
                    contentDescription = works[index].title,
                )
            }
        }
    }
}

/** Tags for the word being typed, under the ways to open what an id names. */
@Composable
private fun SearchPicker(view: PixivSearchView, kind: PixivSearchKind, onPick: (PixivTrendTag) -> Unit) {
    val runtime = LocalPixiv.current
    val copy = rememberCopy()
    val language = currentLanguage()
    LazyColumn(Modifier.fillMaxSize()) {
        view.numericId?.let { id ->
            items(shortcuts(kind, id, language), key = { "id-" + it.first }) { (label, ref) ->
                ShortcutRow(shortcutIcon(ref), label) { runtime.open(PixivRoutes.routeOf(ref)) }
            }
            item(key = "id-rule") { HorizontalDivider() }
        }
        items(view.suggestions, key = { "tag-" + it.name }) { tag ->
            TagSuggestion(tag, onPick = { onPick(tag) }, onCopy = { copy(tag.name, Strings.search_tag_copied) })
        }
    }
}

/** Open the artwork, the creator or the Pixivision article an id names; for novels, the novel, the series or the creator. */
private fun shortcuts(kind: PixivSearchKind, id: Long, language: String): List<Pair<String, PixivLinkRef>> = if (kind.isWorks) {
    listOf(
        Strings.search_open_artwork.get(language, "$id") to PixivLinkRef.Artwork(id),
        Strings.search_open_user.get(language, "$id") to PixivLinkRef.User(id),
        Strings.search_open_pixivision.get(language, "$id") to PixivLinkRef.PixivisionPage(id, Pixivision.language(language)),
    )
} else {
    listOf(
        Strings.search_open_novel.get(language, "$id") to PixivLinkRef.Novel(id),
        Strings.search_open_novel_series.get(language, "$id") to PixivLinkRef.NovelSeries(id),
        Strings.search_open_user.get(language, "$id") to PixivLinkRef.User(id),
    )
}

private fun shortcutIcon(ref: PixivLinkRef) = when (ref) {
    is PixivLinkRef.Artwork -> PostIcons.Image
    is PixivLinkRef.User -> Icons.Outlined.Person
    is PixivLinkRef.Novel -> PixivIcons.MenuBook
    is PixivLinkRef.NovelSeries -> PixivIcons.Collections
    else -> PostIcons.Article
}
