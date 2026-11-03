package com.aimdi.xtanative.services.article

import com.aimdi.xtanative.core.json.J
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArticleParserTest {
    private val tweet = J.parse(javaClass.getResource("/article/tweet_with_article.json")!!.readText())
    private val article = ArticleParser.fromTweet(tweet)!!

    @Test
    fun theTitlePreviewAndCoverAreRead() {
        assertEquals("Why readers read", article.title)
        assertEquals("A short look at reading.", article.previewText)
        assertEquals("https://pbs.twimg.com/media/cover.jpg", article.coverUrl)
        assertEquals(13, article.blocks.size)
    }

    @Test
    fun stylesAndEntitiesCutTheTextCountingAnEmojiAsOneCodePoint() {
        assertEquals(
            listOf(
                ArticleSpan("Reading is "),
                ArticleSpan("😀", bold = true),
                ArticleSpan(" "),
                ArticleSpan("good", italic = true),
                ArticleSpan(" "),
                ArticleSpan("for", target = ArticleTarget.Entity(0)),
                ArticleSpan(" you."),
            ),
            article.blocks[0].spans,
        )
        assertEquals("Reading is 😀 good for you.", article.blocks[0].text)
    }

    @Test
    fun theBlocksOwnLinksAndMentionsBecomeTargets() {
        val quote = article.blocks[11]
        assertEquals(ArticleBlockType.BLOCKQUOTE, quote.type)
        assertEquals(
            listOf(
                ArticleSpan("Read ", underline = true),
                ArticleSpan("@jack", underline = true, target = ArticleTarget.Mention("jack")),
                ArticleSpan(" at ", underline = true),
                ArticleSpan("x.com/jack", underline = true, target = ArticleTarget.Url("x.com/jack")),
            ),
            quote.spans,
        )
    }

    @Test
    fun blockTypesAreReadAndUnknownOnesAreParagraphs() {
        assertEquals(
            listOf(
                ArticleBlockType.UNSTYLED, ArticleBlockType.HEADER_TWO, ArticleBlockType.ORDERED_LIST_ITEM, ArticleBlockType.ORDERED_LIST_ITEM,
                ArticleBlockType.UNSTYLED, ArticleBlockType.ORDERED_LIST_ITEM, ArticleBlockType.ATOMIC, ArticleBlockType.ATOMIC,
                ArticleBlockType.ATOMIC, ArticleBlockType.ATOMIC, ArticleBlockType.ATOMIC, ArticleBlockType.BLOCKQUOTE,
                ArticleBlockType.UNORDERED_LIST_ITEM,
            ),
            article.blocks.map { it.type },
        )
        assertTrue(article.blocks[6].spans.isEmpty(), "an atomic block has no text of its own")
    }

    @Test
    fun orderedItemsAreNumberedFromOneAgainAfterAnyOtherBlock() {
        assertEquals(listOf(null, null, 1, 2, null, 1, null, null, null, null, null, null, null), article.listNumbers)
    }

    @Test
    fun picturesVideosDividersAndCodeAreReadFromTheEntityMap() {
        assertEquals(ArticleEntity.Link("example.com/why"), article.entities[0])
        assertEquals("https://example.com/why", (article.entities[0] as ArticleEntity.Link).openUrl)
        assertEquals(ArticleEntity.Image("https://pbs.twimg.com/media/one.jpg"), article.entities[1])
        assertEquals(ArticleEntity.Divider, article.entities[2])
        assertEquals(ArticleEntity.Markdown("kotlin", "val answer = 42"), article.entities[3])
        val video = assertIs<ArticleEntity.Video>(article.entities[4])
        assertEquals(16.0 / 9.0, video.aspectRatio)
        assertEquals("https://pbs.twimg.com/amplify_video_thumb/222/img/p.jpg", video.media.url)
        assertEquals("https://video.twimg.com/amplify_video/222/vid/avc1/1280x720/b.mp4", video.streamUrl)
    }

    @Test
    fun aMediaEntityXSentNoMediaForIsSkippedNotACrash() {
        assertNull(article.entities[5], "XTA read the missing media entity's fields off null")
        assertNull(article.entities[6], "a kind XTA does not draw")
        assertEquals(emptyList(), article.embedsOf(article.blocks[10]))
    }

    @Test
    fun whatIsDrawnUnderABlockIsItsEntitiesButLinks() {
        assertEquals(listOf<ArticleEntity>(ArticleEntity.Image("https://pbs.twimg.com/media/one.jpg")), article.embedsOf(article.blocks[6]))
        assertEquals(emptyList(), article.embedsOf(article.blocks[0]), "the link lives inside the text")
    }

    @Test
    fun linksAreReadInAnArticleWithoutMediaEntities() {
        val json = J.parse(
            """{"title":"t","content_state":{"blocks":[],"entityMap":[{"key":"0","value":{"type":"LINK","data":{"url":"https://a.example"}}}]}}""",
        )
        assertEquals(mapOf<Int, ArticleEntity>(0 to ArticleEntity.Link("https://a.example")), ArticleParser.fromGraphql(json).entities)
    }

    @Test
    fun theEntityMapIsReadAsAListAsJsonTextOrWrapped() {
        val list = """[{"key":"2","value":{"type":"DIVIDER","data":{}}}]"""
        val expected = mapOf<Int, ArticleEntity>(2 to ArticleEntity.Divider)
        assertEquals(expected, ArticleParser.entities(J.parse(list), J(null)))
        assertEquals(expected, ArticleParser.entities(J(kotlinx.serialization.json.JsonPrimitive(list)), J(null)))
        assertEquals(expected, ArticleParser.entities(J.parse("""{"entityMap":$list}"""), J(null)))
        assertEquals(emptyMap(), ArticleParser.entities(J.parse("42"), J(null)))
    }

    @Test
    fun rangesOutsideTheTextAreDropped() {
        val block = ArticleParser.block(
            J.parse("""{"text":"short","type":"unstyled","inlineStyleRanges":[{"offset":3,"length":40,"style":"BOLD"},{"offset":-1,"length":2,"style":"ITALIC"}],"entityRanges":[{"key":"x","offset":0,"length":2}]}"""),
        )
        assertEquals(listOf(ArticleSpan("sho"), ArticleSpan("rt", bold = true)), block.spans)
        assertEquals(emptyList(), block.entityKeys)
    }

    @Test
    fun codeFencesGiveTheirLanguageAndAnythingElseIsAllCode() {
        assertEquals(ArticleEntity.Markdown("", "plain text"), ArticleEntity.Markdown.of("  plain text \n"))
        assertEquals(ArticleEntity.Markdown("", "```one line```"), ArticleEntity.Markdown.of("```one line```"))
        assertEquals(ArticleEntity.Markdown("dart", "main() {}"), ArticleEntity.Markdown.of("```dart\nmain() {}"))
    }

    @Test
    fun aSavedPostKeepsTheShortFormOfItsArticle() {
        val stored = ArticleParser.stored(article)
        assertEquals("""{"title":"Why readers read","previewText":"A short look at reading.","coverMediaUrl":"https://pbs.twimg.com/media/cover.jpg"}""", stored.toString())
        assertEquals(Article("Why readers read", "A short look at reading.", coverUrl = "https://pbs.twimg.com/media/cover.jpg"), ArticleParser.fromStored(J(stored)))
        assertNull(ArticleParser.fromTweet(J.parse("""{"rest_id":"1"}""")))
    }

    @Test
    fun theWholeTextReadsTitleFirstForReadingAloud() {
        assertTrue(article.plainText.startsWith("Why readers read\n\nReading is 😀 good for you.\n\nChapter one"))
    }
}
