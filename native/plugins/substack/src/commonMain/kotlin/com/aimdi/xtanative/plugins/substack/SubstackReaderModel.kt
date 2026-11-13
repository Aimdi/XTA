package com.aimdi.xtanative.plugins.substack

import com.aimdi.xtanative.networks.substack.SubstackArticle
import com.aimdi.xtanative.networks.substack.SubstackArticleCache
import com.aimdi.xtanative.networks.substack.SubstackHtml
import com.aimdi.xtanative.networks.substack.SubstackParsedArticles
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.networks.substack.SubstackReaderBody
import com.aimdi.xtanative.networks.substack.SubstackReading
import com.aimdi.xtanative.store.reading.ArticleReading
import com.aimdi.xtanative.store.settings.PostsPrefs
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the reader shows of a post. */
internal sealed interface ReaderContent {
    data object Loading : ReaderContent

    /** Its body, as blocks; [preview] when it is the free part of a paid post. */
    data class Article(val article: SubstackArticle, val preview: Boolean, val keptImages: Map<String, File>) : ReaderContent

    /** A paid post without a body: the paid pane, never the publisher's page. */
    data object Paywall : ReaderContent

    /** No body but an address, or a post that could not be read: the post's page, in the web view where there is one (Android), else opened in the browser. */
    data object Live : ReaderContent

    data object Empty : ReaderContent

    data class Failed(val error: Throwable) : ReaderContent
}

/**
 * One post open in the reader, as XTA's SubstackReaderScreen loads it: its
 * offline copy, else the kept body while it is still the same post, else the
 * body it came with, else Substack's; then the article's place, type and
 * read mark through the app's [ArticleReading]. A refresh keeps the article
 * on screen and says when it failed.
 */
internal class ReaderModel(private val app: SubstackApp, opened: SubstackPost, private val scope: CoroutineScope) {
    data class State(
        val post: SubstackPost,
        val content: ReaderContent = ReaderContent.Loading,
        val refreshing: Boolean = false,
        val refreshError: Throwable? = null,
        val reading: ArticleReading? = null,
        /** The prose of the publication's live page, once the web view has it. */
        val liveText: String? = null,
    ) {
        val speakText: String
            get() = SubstackHtml.speakText(
                post.title,
                post.excerpt,
                post.authorName,
                post.publicationName,
                bodyPlain = (content as? ReaderContent.Article)?.article?.plainText ?: liveText.takeIf { content is ReaderContent.Live },
            )
    }

    private val mutable = MutableStateFlow(State(opened))
    val state: StateFlow<State> = mutable.asStateFlow()
    private var job: Job? = null

    init {
        load(force = false)
    }

    fun load(force: Boolean) {
        val showing = state.value.content is ReaderContent.Article
        job?.cancel()
        mutable.update { if (force && showing) it.copy(refreshing = true, refreshError = null) else it.copy(content = ReaderContent.Loading, refreshError = null) }
        job = scope.launch {
            val post = state.value.post
            val loaded = try {
                resolve(post, force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.afterFailure(e, refresh = force && showing) }
                return@launch
            }
            show(loaded)
        }
    }

    /** A refresh that failed keeps the article and says so; a post that could not be read shows its page (web view or browser), else why. */
    private fun State.afterFailure(error: Exception, refresh: Boolean): State = when {
        refresh -> copy(refreshing = false, refreshError = error)
        SubstackReading.fallsBackToSite(post) -> copy(content = ReaderContent.Live, refreshing = false)
        else -> copy(content = ReaderContent.Failed(error), refreshing = false)
    }

    private suspend fun resolve(post: SubstackPost, force: Boolean): SubstackPost {
        if (!force) {
            app.offlineCopy(post)?.let(SubstackOffline::postOf)?.takeIf { !it.bodyHtml.isNullOrBlank() }?.let { return it }
            app.articles.get(post.publication, post.slug)?.takeIf { SubstackArticleCache.isCurrent(it, post) }?.let { return it }
            if (!post.bodyHtml.isNullOrBlank()) return post
        }
        return app.client.post(post.publication, post.slug).also { app.launch { app.articles.put(it) } }
    }

    private suspend fun show(post: SubstackPost) {
        val content = when (SubstackReading.bodyFor(post)) {
            SubstackReaderBody.ARTICLE -> {
                val article = withContext(Dispatchers.Default) { parsed.of(post) } ?: return show(post.copy(bodyHtml = null))
                val kept = runCatching { app.host.offline.articleImages(SubstackOffline.idOf(post)) }.getOrDefault(emptyMap())
                ReaderContent.Article(article, SubstackReading.isPreview(post), kept)
            }
            SubstackReaderBody.PAYWALL -> ReaderContent.Paywall
            SubstackReaderBody.LIVE_SITE -> ReaderContent.Live
            SubstackReaderBody.EMPTY -> ReaderContent.Empty
        }
        val reading = state.value.reading ?: open(post)
        reading.allowAutomaticCompletion = content is ReaderContent.Article && !content.preview
        mutable.update { it.copy(post = post, content = content, refreshing = false, reading = reading) }
    }

    /** The article's place and type; its timers run beyond the screen, so the last place is written as it leaves. */
    private suspend fun open(post: SubstackPost): ArticleReading = ArticleReading.open(
        journal = app.host.readingJournal,
        appearance = app.host.readingAppearance,
        articleId = ReaderIds.readingIdOf(post),
        scope = app.scope,
        onCompleted = { app.read.markRead(listOf(state.value.post)) },
        remembers = { app.host.settings.state.value?.get(PostsPrefs.rememberReadingPosition) != false },
        alreadyCompleted = app.read.isRead(post),
        allowAutomaticCompletion = false,
    )

    /** What the live page reads as, for Listen; too little to be the article is ignored. */
    fun liveText(text: String) {
        if (text.trim().length >= MIN_LIVE_TEXT) mutable.update { it.copy(liveText = text) }
    }

    /** The reader came into view, or left it: only time in view counts toward finishing, and leaving writes the place. */
    fun active(active: Boolean) {
        state.value.reading?.setActive(active)
    }

    private companion object {
        const val MIN_LIVE_TEXT = 40
        val parsed = SubstackParsedArticles()
    }
}
