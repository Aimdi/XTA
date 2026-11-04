package com.aimdi.xtanative.ui.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.services.article.Article
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.ProblemView
import com.aimdi.xtanative.ui.common.fullTimeText
import com.aimdi.xtanative.ui.generated.resources.Res
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
)

/**
 * The X article of post [postId]: drawn at once when the post at hand came
 * with its blocks, else read with the post's conversation, which X sends
 * them with. A post that turns out to carry no article leaves its page to
 * the browser.
 */
class ArticleViewModel(private val graph: AppGraph, private val postId: String) : ViewModel() {
    private val state = MutableStateFlow(graph.posts.post(postId).let { ArticleUi(post = it, article = it?.let(ArticleLayout::fullArticle)) })
    val ui: StateFlow<ArticleUi> = state.asStateFlow()

    init {
        if (state.value.article == null) load()
    }

    fun load() {
        viewModelScope.launch {
            state.update { it.copy(loading = true, problem = null) }
            try {
                val focal = XConversation.of(graph.client.conversation(postId), postId).focal
                focal?.let(graph.posts::remember)
                state.update { it.copy(post = focal ?: it.post, article = focal?.let(ArticleLayout::fullArticle), loading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { it.copy(loading = false, problem = Problem.of(error)) }
            }
        }
    }
}

/** An X article read in XTA Native, as XTA reads it in the opened post: the article is the post's own content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(postId: String, actions: PostActions, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: ArticleViewModel = viewModel(key = "article-$postId") { ArticleViewModel(graph, postId) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val article = state.article
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(article?.title ?: stringResource(Res.string.posts_article_on_x), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        PostScopeProvider(focalId = postId) {
            val problem = state.problem
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
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
        OutlinedButton(onClick = { actions.openInBrowser(page) }) { Text(stringResource(Res.string.posts_open_in_browser)) }
    }
}
