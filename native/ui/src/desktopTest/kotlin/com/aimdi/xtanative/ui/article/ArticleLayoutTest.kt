package com.aimdi.xtanative.ui.article

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XArticle
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.services.article.Article
import com.aimdi.xtanative.services.article.ArticleBlock
import com.aimdi.xtanative.services.article.ArticleBlockType
import com.aimdi.xtanative.services.article.ArticleEntity
import com.aimdi.xtanative.services.article.ArticleSpan
import com.aimdi.xtanative.services.article.ArticleTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleLayoutTest {
    private fun block(type: ArticleBlockType, text: String = "t", keys: List<Int> = emptyList()) = ArticleBlock(type, listOf(ArticleSpan(text)), keys)

    @Test
    fun blocksBecomeRowsWithTheirEmbedsAndNumberedListsCountFromOneAgain() {
        val article = Article(
            title = "T",
            previewText = "P",
            blocks = listOf(
                block(ArticleBlockType.HEADER_TWO, "Intro"),
                block(ArticleBlockType.ORDERED_LIST_ITEM, "one"),
                block(ArticleBlockType.ORDERED_LIST_ITEM, "two", keys = listOf(1, 2)),
                block(ArticleBlockType.UNSTYLED, "then"),
                block(ArticleBlockType.ORDERED_LIST_ITEM, "again"),
                ArticleBlock(ArticleBlockType.ATOMIC, emptyList(), listOf(3)),
                block(ArticleBlockType.BLOCKQUOTE, "said"),
                block(ArticleBlockType.UNORDERED_LIST_ITEM, "dot"),
            ),
            entities = mapOf(1 to ArticleEntity.Link("example.com"), 2 to ArticleEntity.Divider, 3 to ArticleEntity.Image("https://img")),
        )
        val items = ArticleLayout.items(article)
        assertEquals(
            listOf("Heading", "Numbered 1", "Numbered 2", "Embed Divider", "Paragraph", "Numbered 1", "Embed Image", "Quote", "Bullet"),
            items.map { item ->
                when (item) {
                    is ArticleItem.Heading -> "Heading"
                    is ArticleItem.Numbered -> "Numbered ${item.number}"
                    is ArticleItem.Embed -> "Embed " + item.entity::class.simpleName
                    is ArticleItem.Paragraph -> "Paragraph"
                    is ArticleItem.Quote -> "Quote"
                    is ArticleItem.Bullet -> "Bullet"
                }
            },
            "a link lives inside the text, not under it",
        )
    }

    @Test
    fun aSpanLeadsToItsLinkEntityItsMarkedLinkOrItsMention() {
        val article = Article("T", "P", entities = mapOf(1 to ArticleEntity.Link("example.com/a"), 2 to ArticleEntity.Divider))
        assertEquals(ArticleTap.Link("https://example.com/a"), ArticleLayout.tap(ArticleSpan("a", target = ArticleTarget.Entity(1)), article))
        assertNull(ArticleLayout.tap(ArticleSpan("a", target = ArticleTarget.Entity(2)), article))
        assertEquals(ArticleTap.Link("https://example.org"), ArticleLayout.tap(ArticleSpan("a", target = ArticleTarget.Url("example.org")), article))
        assertEquals(ArticleTap.Profile("maya"), ArticleLayout.tap(ArticleSpan("@maya", target = ArticleTarget.Mention("@maya")), article))
        assertNull(ArticleLayout.tap(ArticleSpan("plain"), article))
    }

    @Test
    fun aPostCarriesItsArticleInFullOnlyWhenXSentTheBlocks() {
        val content = J.parse("""{"title":"Long read","preview_text":"It starts","content_state":{"blocks":[{"type":"unstyled","text":"Hello"}],"entityMap":[]}}""")
        val author = XUser("1", "maya", "Maya")
        val full = XPost("1", author, null, "", article = XArticle("9", "Long read", "It starts", "https://cover", content))
        val article = ArticleLayout.fullArticle(full)!!
        assertEquals("Hello", article.blocks.single().text)
        assertEquals("https://cover", article.coverUrl, "the post's cover stands in when the body names none")
        val slim = full.copy(article = XArticle("9", "Long read", "It starts", null))
        assertNull(ArticleLayout.fullArticle(slim), "a feed's copy has the title only")
        assertEquals("Long read", ArticleLayout.preview(slim)?.title)
        assertNull(ArticleLayout.preview(XPost("2", author, null, "")))

        val linking = XPost("3", author, null, "Read", entities = listOf(XEntity.Link(0, 4, "https://t.co/a", "https://x.com/i/article/9", "x.com/i/article/9")))
        assertEquals("https://x.com/i/article/9", ArticleLayout.linkOf(linking))
    }
}
