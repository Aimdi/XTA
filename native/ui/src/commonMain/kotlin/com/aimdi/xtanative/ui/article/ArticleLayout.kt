package com.aimdi.xtanative.ui.article

import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.services.article.Article
import com.aimdi.xtanative.services.article.ArticleBlock
import com.aimdi.xtanative.services.article.ArticleBlockType
import com.aimdi.xtanative.services.article.ArticleEntity
import com.aimdi.xtanative.services.article.ArticleParser
import com.aimdi.xtanative.services.article.ArticleSpan
import com.aimdi.xtanative.services.article.ArticleTarget
import com.aimdi.xtanative.services.links.XLinks
import com.aimdi.xtanative.ui.common.FindBlock

/** One row of an X article as XTA's ArticleWidget lays it out. */
sealed interface ArticleItem {
    data class Paragraph(val block: ArticleBlock) : ArticleItem

    data class Heading(val block: ArticleBlock) : ArticleItem

    data class Bullet(val block: ArticleBlock) : ArticleItem

    data class Numbered(val number: Int, val block: ArticleBlock) : ArticleItem

    data class Quote(val block: ArticleBlock) : ArticleItem

    /** A picture, a video, code or a divider a block points at, under its text. */
    data class Embed(val entity: ArticleEntity) : ArticleItem
}

/** A row of an article's page above its text ([ArticleLayout.lead]). */
enum class ArticleLead { COVER, TITLE, HEADER }

/** Where a tap on a span of an article's text leads. */
sealed interface ArticleTap {
    data class Link(val url: String) : ArticleTap

    data class Profile(val screenName: String) : ArticleTap
}

object ArticleLayout {
    /** [article]'s rows: each block's text in its kind, numbered lists counted, then what it embeds. */
    fun items(article: Article): List<ArticleItem> {
        val numbers = article.listNumbers
        return article.blocks.flatMapIndexed { index, block ->
            val text = when (block.type) {
                ArticleBlockType.ATOMIC -> null
                ArticleBlockType.HEADER_TWO -> ArticleItem.Heading(block)
                ArticleBlockType.UNORDERED_LIST_ITEM -> ArticleItem.Bullet(block)
                ArticleBlockType.ORDERED_LIST_ITEM -> ArticleItem.Numbered(numbers[index] ?: 1, block)
                ArticleBlockType.BLOCKQUOTE -> ArticleItem.Quote(block)
                ArticleBlockType.UNSTYLED -> ArticleItem.Paragraph(block)
            }
            listOfNotNull(text) + article.embedsOf(block).map(ArticleItem::Embed)
        }
    }

    /**
     * What the find bar looks through in [article] ([FindBlock]): its title,
     * then each row's text, keyed as [ArticleBody] draws them, all in the
     * list's row [item] (null on a page that scrolls as a whole).
     */
    fun findBlocks(article: Article, item: Int? = null): List<FindBlock> =
        listOf(FindBlock(TITLE_KEY, article.title, item)) +
            items(article).mapIndexedNotNull { index, row -> blockOf(row)?.let { FindBlock(rowKey(index), it.text, item) } }

    /**
     * The rows an article read as a list starts with, in order: its cover
     * when it has one, its title, and the line under it when the page has
     * one ([header]). The reader's place counts the text's rows after these.
     */
    fun lead(article: Article, header: Boolean): List<ArticleLead> = listOfNotNull(
        ArticleLead.COVER.takeIf { article.coverUrl != null },
        ArticleLead.TITLE,
        ArticleLead.HEADER.takeIf { header },
    )

    /** [findBlocks] for an article read as a list that starts with [lead]: each text in the list's row that draws it. */
    fun findRows(article: Article, lead: List<ArticleLead>): List<FindBlock> =
        listOf(FindBlock(TITLE_KEY, article.title, lead.indexOf(ArticleLead.TITLE).coerceAtLeast(0))) +
            items(article).mapIndexedNotNull { index, row -> blockOf(row)?.let { FindBlock(rowKey(index), it.text, lead.size + index) } }

    /**
     * The name the reader's place in the article of post [postId] is kept
     * under: X's own, so it meets no plugin's (`rss:…`, `substack:…`).
     */
    fun readingId(postId: String): String = "x-article:$postId"

    /** The block of text [item] draws; none for what an article embeds. */
    fun blockOf(item: ArticleItem): ArticleBlock? = when (item) {
        is ArticleItem.Paragraph -> item.block
        is ArticleItem.Heading -> item.block
        is ArticleItem.Bullet -> item.block
        is ArticleItem.Numbered -> item.block
        is ArticleItem.Quote -> item.block
        is ArticleItem.Embed -> null
    }

    /** The find bar's key of an article's title. */
    const val TITLE_KEY = "article-title"

    /** The find bar's key of the text of an article's row [index] ([items]). */
    fun rowKey(index: Int): String = "article-$index"

    /** Where [span] leads in [article]: a link entity or a link or mention X marked in the block; nothing for plain text. */
    fun tap(span: ArticleSpan, article: Article): ArticleTap? = when (val target = span.target) {
        is ArticleTarget.Entity -> (article.entities[target.key] as? ArticleEntity.Link)?.let { ArticleTap.Link(it.openUrl) }
        is ArticleTarget.Url -> ArticleTap.Link(com.aimdi.xtanative.services.article.articleLinkUrl(target.url))
        is ArticleTarget.Mention -> target.screenName.removePrefix("@").takeIf { it.isNotEmpty() }?.let(ArticleTap::Profile)
        null -> null
    }

    /**
     * The article [post] carries in full, read from the JSON X sent with it;
     * null when X sent only its title (a feed's copy) or none.
     */
    fun fullArticle(post: XPost): Article? {
        val article = post.shown.article ?: return null
        if (!article.content["content_state"].exists) return null
        return ArticleParser.fromGraphql(article.content).let { full ->
            full.copy(coverUrl = full.coverUrl ?: article.coverUrl, title = full.title.ifEmpty { article.title })
        }
    }

    /** What a post shows of its article before it is read: title, preview and cover. */
    fun preview(post: XPost): Article? = post.shown.article?.let { Article(it.title, it.previewText, coverUrl = it.coverUrl) }

    /** The X article link a post carries without its article, which a browser can still open. */
    fun linkOf(post: XPost): String? =
        XLinks.firstArticleLink(post.shown.entities.filterIsInstance<XEntity.Link>().map { it.expanded })
}
