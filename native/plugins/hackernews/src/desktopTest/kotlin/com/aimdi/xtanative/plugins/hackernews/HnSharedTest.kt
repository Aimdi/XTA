package com.aimdi.xtanative.plugins.hackernews

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.hackernews.HnStory
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.TextRun
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A story among other networks' posts and in the app's Saved, as XTA keeps it there. */
class HnSharedTest {
    private val story = HnStory(
        8863,
        "My YC app",
        url = "https://www.dropbox.com/",
        text = "",
        author = "dhouston",
        score = 111,
        commentCount = 71,
        createdAt = Instant.parse("2007-04-04T19:16:40Z"),
    )

    @Test
    fun savedKeepsAStoryAsXtasLinkRecordUnderItsPage() {
        val archive = HnShared.archive(story.copy(text = "Body"))
        assertEquals("hackernews:https://news.ycombinator.com/item?id=8863", archive.id)
        assertEquals("dhouston", archive.authorId)
        val content = J(archive.content)
        assertEquals("link", content["xtaPlugin"].string)
        assertEquals("hackernews", content["source"].string)
        assertEquals(story.hnUrl, content["url"].string)
        assertEquals("dhouston", content["author"].string)
        assertEquals("My YC app\nBody", content["text"].string)
        assertEquals(0, content["images"].list.size)
        assertEquals("My YC app", J(HnShared.archive(story).content)["text"].string, "a link story is its title")
        assertEquals("hackernews", SavedContent.of(archive.content).network, "Saved hands it to this plugin")
    }

    @Test
    fun aRecordReadsBackAsTheStoryItsPageNames() {
        val read = HnShared.story(HnShared.archive(story.copy(text = "Line one\nLine two")).content)!!
        assertEquals(8863, read.id)
        assertEquals("My YC app", read.title)
        assertEquals("Line one\nLine two", read.text)
        assertEquals("dhouston", read.author)
        assertEquals(story.hnUrl, read.hnUrl)
    }

    @Test
    fun anotherRecordIsNoneOfIts() {
        assertNull(HnShared.story(PluginPostArchive.link("substack", "https://a.substack.com/p/x", "A", "T").content))
        assertNull(HnShared.story(PluginPostArchive.link("hackernews", "https://news.ycombinator.com/user?id=pg", "pg", "T").content), "not a story's page")
        assertNull(HnShared.story(J.parse("""{"xtaPlugin":"reddit","post":{}}""").raw!!))
    }

    @Test
    fun theSharedRowShowsWhatTheStoryCardDoes() {
        val post = HnShared.post(story)
        assertEquals("8863", post.id)
        assertEquals("dhouston", post.authorName)
        assertEquals("My YC app", post.title)
        assertEquals(emptyList(), post.text, "the card shows the title alone")
        assertEquals("https://www.dropbox.com/", post.card?.url)
        assertEquals("dropbox.com", post.card?.domain)
        assertEquals(71L, post.replies)
        assertEquals(111L, post.likes, "its points")
        assertEquals(story.createdAt, post.createdAt, "where it sits among the other networks'")
        assertEquals(story.hnUrl, post.url)
    }

    @Test
    fun savedShowsAStorysTextWithItsTitle() {
        val ask = HnStory(1, "Ask HN: Why?", text = "Because.", author = "pg")
        assertEquals(listOf(TextRun.Plain("Because.")), HnShared.post(ask, words = true).text)
        assertTrue(HnShared.post(ask).card == null, "a text post links nowhere else")
    }
}
