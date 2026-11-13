package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.networks.threads.ThreadsConversation
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.fullTimeText
import com.aimdi.xtanative.ui.plugins.PluginAvatar
import com.aimdi.xtanative.ui.post.AuthorLine

/**
 * An author's own thread as one piece of writing: the posts they chained,
 * in order, the opened one among them. Threads has no long posts as X has
 * its articles and notes, so a long thought is written as a thread of
 * several; read post by post, each repeats its author, its counts and its
 * buttons between two paragraphs.
 */
internal data class ThreadsArticle(val posts: List<ThreadsPost>) {
    /** The post the thread starts with: whose it is and when it was written. */
    val first: ThreadsPost get() = posts.first()

    /** What reading it aloud says: its author once, then every post's words. */
    fun spokenText(): String = (listOf(first.authorName) + posts.map { it.text }).filter { it.isNotBlank() }.joinToString("\n\n")
}

/** When a conversation reads as an article, and which of its posts make it. */
internal object ThreadsArticles {
    /** A thread shorter than this is a post, not an article. */
    const val MIN_POSTS = 2

    /**
     * The opened post's author's own thread in [conversation]: their posts
     * right above it, the post, and those they chained under it, as far as
     * each is theirs. Null while the page is not read, when the author is
     * not named, or when that is fewer than [MIN_POSTS] posts.
     */
    fun of(conversation: ThreadsConversation): ThreadsArticle? {
        if (!conversation.loaded || conversation.focus.handle.isEmpty()) return null
        val posts = conversation.ancestors.takeLastWhile(conversation::byAuthor) + conversation.focus + conversation.continuation.takeWhile(conversation::byAuthor)
        return ThreadsArticle(posts).takeIf { posts.size >= MIN_POSTS }
    }

    /** Whether the screen shows the article: only while the reader asked for it ([asked]) and there is one. */
    fun reading(asked: Boolean, article: ThreadsArticle?): Boolean = asked && article != null
}

private val ArticleStyle = TextStyle(fontSize = 17.sp, lineHeight = 26.sp)

/**
 * [article] read as one: its author and date once, then each post's words
 * (spoilers covered, each post's text to select and copy), attached text,
 * poll, pictures and videos, link card and quote under one another, without
 * the cards' counts and buttons. A picture opens in the viewer among all the
 * article's pictures.
 */
@Composable
internal fun ThreadsArticleView(app: ThreadsApp, article: ThreadsArticle) {
    val list = rememberLazyListState()
    CompositionLocalProvider(LocalThreadsGallery provides article.posts) {
        LazyColumn(state = list, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
            item(key = "author") { ArticleAuthor(app, article.first) }
            items(article.posts, key = { "part:" + it.id }, contentType = { "part" }) { post -> ArticlePart(app, post) }
            item(key = "end") { Hairline(Modifier.padding(top = 20.dp)) }
        }
    }
}

@Composable
private fun ArticleAuthor(app: ThreadsApp, post: ThreadsPost) {
    Row(Modifier.fillMaxWidth().clickable(onClickLabel = post.authorName) { app.openProfile(post.handle) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        PluginAvatar(post.avatarUrl, post.authorName, post.handle, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            AuthorLine(post.authorName, post.handle, "", verified = post.isVerified)
            Text(fullTimeText(post.publishedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One post of the article: what it says and carries, nothing about it. */
@Composable
private fun ArticlePart(app: ThreadsApp, post: ThreadsPost) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        ThreadsCaptionText(app, post, Modifier, style = ArticleStyle, selectable = true)
        post.facts.attachment?.let { TextAttachmentBlock(app, post, it, focal = true, Modifier.partGap()) }
        post.facts.poll?.let { ThreadsPollView(it, Modifier.partGap()) }
        ThreadsPostMedia(app, post, Modifier.partGap(), opens = false)
        post.linkCard?.let { ThreadsLink(app, post, it, Modifier.partGap(), opens = false) }
        post.quoted?.let { ThreadsQuote(app, it, Modifier.partGap()) }
    }
}
