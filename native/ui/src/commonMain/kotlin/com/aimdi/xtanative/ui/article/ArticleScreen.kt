package com.aimdi.xtanative.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.aimdi.xtanative.store.reading.ArticleReading
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.ui.common.WithReadingColumn
import com.aimdi.xtanative.ui.plugins.ArticleReaderControls
import com.aimdi.xtanative.ui.plugins.KeepsReaderPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.x.model.XConversation
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.services.article.Article
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.ProblemView
import com.aimdi.xtanative.ui.common.ReadingPage
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.common.fullTimeText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.home.FindInPage
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.posts_article_on_x
import com.aimdi.xtanative.ui.generated.resources.posts_open_in_browser
import com.aimdi.xtanative.ui.post.AuthorLine
import com.aimdi.xtanative.ui.post.FooterCounts
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.PostFooter
import com.aimdi.xtanative.ui.post.PostScopeProvider
import com.aimdi.xtanative.ui.post.PostView
import com.aimdi.xtanative.ui.post.rememberPostPrefs
import com.aimdi.xtanative.ui.speech.ReadAloudButton
import com.aimdi.xtanative.ui.speech.SpeechTexts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

data class ArticleUi(
    val post: XPost? = null,
    val article: Article? = null,
    val loading: Boolean = false,
    val problem: Problem? = null,
    /** The reader's place and type in [article], once it is opened; null before, and where it could not be. */
    val reading: ArticleReading? = null,
    /** The place could not be opened: the article is read as a plain page. */
    val plain: Boolean = false,
)

/**
 * The X article of post [postId]: drawn at once when the post at hand came
 * with its blocks, else read with the post's conversation, which X sends
 * them with. A post that turns out to carry no article leaves its page to
 * the browser. Its place and type are the app's [ArticleReading], as the
 * other networks' readers keep them, under [ArticleLayout.readingId]; an
 * article finished is marked so on this device only.
 */
class ArticleViewModel(private val graph: AppGraph, private val postId: String) : ViewModel() {
    private val state = MutableStateFlow(graph.posts.post(postId).let { ArticleUi(post = it, article = it?.let(ArticleLayout::fullArticle)) })
    val ui: StateFlow<ArticleUi> = state.asStateFlow()

    /** Outlives the screen: the place is written once more as it closes. */
    private val outlives = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        if (state.value.article == null) load() else openReading()
    }

    fun load() {
        viewModelScope.launch {
            state.update { it.copy(loading = true, problem = null) }
            try {
                val focal = XConversation.of(graph.client.conversation(postId), postId).focal
                focal?.let(graph.posts::remember)
                state.update { it.copy(post = focal ?: it.post, article = focal?.let(ArticleLayout::fullArticle), loading = false) }
                if (state.value.article != null) openReading()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { it.copy(loading = false, problem = Problem.of(error)) }
            }
        }
    }

    private fun openReading() {
        if (state.value.reading != null) return
        viewModelScope.launch {
            try {
                val reading = ArticleReading.open(
                    journal = graph.readingJournal,
                    appearance = graph.readingAppearance,
                    articleId = ArticleLayout.readingId(postId),
                    scope = outlives,
                    onCompleted = {},
                    remembers = { graph.settings.state.value?.get(PostsPrefs.rememberReadingPosition) != false },
                    journalOnOpen = false,
                )
                state.update { it.copy(reading = reading) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.update { it.copy(plain = true) }
            }
        }
    }

    override fun onCleared() {
        state.value.reading?.let { reading -> outlives.launch(NonCancellable) { reading.close() } }
    }
}

/**
 * An X article read in XTA Native, as XTA reads it in the opened post: the
 * article is the post's own content. Under the bar, the readers' line
 * ([ArticleReaderControls]): how far in, the type, back to the beginning and
 * finished; the article opens where it was left ([KeepsReaderPlace]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(postId: String, actions: PostActions, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: ArticleViewModel = viewModel(key = "article-$postId") { ArticleViewModel(graph, postId) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val article = state.article
    val reading = state.reading.takeIf { article != null }
    val list = rememberLazyListState()
    val coroutines = rememberCoroutineScope()
    val lead = remember(article, state.post != null) { article?.let { ArticleLayout.lead(it, header = state.post != null) }.orEmpty() }
    // Ctrl+F finds in the article (the desktop's find bar).
    val findBlocks = remember(article, lead, reading != null) {
        article?.let { if (reading != null) ArticleLayout.findRows(it, lead) else ArticleLayout.findBlocks(it) }.orEmpty()
    }
    DisposableEffect(reading) {
        reading?.setActive(true)
        onDispose { reading?.setActive(false) }
    }
    FindInPage(findBlocks, list.takeIf { reading != null }) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                TopAppBar(
                    title = { TipText(article?.title ?: stringResource(Res.string.posts_article_on_x), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconTip(stringResource(Res.string.back)) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } } },
                    actions = { article?.let { ListenToArticle(it) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                reading?.let { ArticleReaderControls(it, supportsAppearance = true, canComplete = true) { coroutines.launch { list.scrollToItem(0) } } }
            }
        },
    ) { padding ->
        PostScopeProvider(focalId = postId) {
            val problem = state.problem
            when {
                article != null && reading != null -> ReaderArticle(article, reading, lead, list, padding, actions) { state.post?.let { ArticleByline(it, actions) } }
                // The place is being opened: a moment, and the article comes where it was left.
                article != null && !state.plain -> Unit
                else -> ReadingPage(padding, PagePadding) {
                    when {
                        article != null -> ArticleBody(article, actions, header = { state.post?.let { ArticleByline(it, actions) } })
                        state.loading -> LoadingRow()
                        problem != null -> ProblemView(problem, vm::load)
                        else -> NoArticle(state.post, actions)
                    }
                }
            }
        }
    }
    }
}

private val PagePadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

/** [article] as the reader's list, in the type and at the place [reading] keeps. */
@Composable
private fun ReaderArticle(
    article: Article,
    reading: ArticleReading,
    lead: List<ArticleLead>,
    list: LazyListState,
    padding: PaddingValues,
    actions: PostActions,
    header: @Composable () -> Unit,
) {
    val look by reading.state.collectAsStateWithLifecycle()
    val items = remember(article) { ArticleLayout.items(article) }
    val type = readerType(look.fontSize, look.lineHeight)
    KeepsReaderPlace(reading, list, before = lead.size, blocks = items.size)
    val around = PaddingValues(
        start = 16.dp,
        top = padding.calculateTopPadding() + 8.dp,
        end = 16.dp,
        bottom = padding.calculateBottomPadding() + 8.dp,
    )
    WithReadingColumn(around, list) { column ->
        SelectionContainer {
            LazyColumn(state = list, contentPadding = column, modifier = Modifier.fillMaxSize()) {
                articleRows(article, lead, items, actions, type, header)
            }
        }
    }
}

/** Listen in the article's bar: reads [article] aloud under its title, the bar's own; pressed again while it is read, it stops. */
@Composable
private fun ListenToArticle(article: Article) {
    val title = article.title.ifEmpty { stringResource(Res.string.posts_article_on_x) }
    ReadAloudButton(title, { SpeechTexts.article(article) })
}

/** Who wrote it and when, with the post's counts, under the title as XTA puts the post's footer there. */
@Composable
private fun ArticleByline(post: XPost, actions: PostActions) {
    val prefs = rememberPostPrefs()
    val shown = post.shown
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(shown.author.largeAvatarUrl, 32.dp) { actions.openProfile(shown.author.screenName) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                AuthorLine(shown.author.name, shown.author.screenName, "", shown.author.verified)
                Text(fullTimeText(shown.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PostFooter(shown, actions, FooterCounts.of(shown, prefs, showViews = true))
    }
}

/** X sent no article with the post: its page, for the browser. */
@Composable
private fun NoArticle(post: XPost?, actions: PostActions) {
    val prefs = rememberPostPrefs()
    val page = post?.let { ArticleLayout.linkOf(it) ?: PostView.url(it, prefs) } ?: return
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(Res.string.posts_article_on_x), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        TextButton(onClick = { actions.openInBrowser(page) }) { Text(stringResource(Res.string.posts_open_in_browser)) }
    }
}
