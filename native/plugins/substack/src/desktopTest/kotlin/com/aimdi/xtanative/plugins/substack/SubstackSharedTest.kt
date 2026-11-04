package com.aimdi.xtanative.plugins.substack

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.substack.SubstackNote
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.networks.substack.SubstackPublication
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.TextRun
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Substack's articles and notes among other networks' posts, and in the app's Saved as XTA keeps them. */
class SubstackSharedTest {
    private val post = SubstackPost(
        id = "42",
        title = "The long read",
        slug = "the-long-read",
        publicationBaseUrl = "https://example.substack.com",
        publicationName = "Example",
        subtitle = "Why it matters",
        postDate = "2026-11-02T08:00:00.000Z",
        canonicalUrl = "https://example.substack.com/p/the-long-read",
        coverImage = "https://substackcdn.com/image/cover.jpg",
        authorName = "Ann Writer",
        reactionCount = 12,
        commentCount = 3,
    )

    @Test
    fun anArticleIsKeptAsItsCardFilesIt() {
        val archive = SubstackShared.archive(post)
        assertEquals("substack:https://example.substack.com/p/the-long-read", archive.id)
        assertEquals("Ann Writer", archive.authorId)
        val record = J(archive.content)
        assertEquals("link", record["xtaPlugin"].string)
        assertEquals("substack", record["source"].string)
        assertEquals("The long read\nWhy it matters", record["text"].string)
        assertEquals(listOf("https://substackcdn.com/image/cover.jpg"), record["images"].list.map { it.string })
        assertEquals("substack", SavedContent.of(archive.content).network, "Saved hands it to this plugin")
    }

    @Test
    fun anArticleWithoutAnAddressOrAuthorIsKeptUnderItsPublication() {
        val bare = post.copy(canonicalUrl = null, authorName = null, subtitle = null, coverImage = null)
        val record = J(SubstackShared.archive(bare).content)
        assertEquals("https://example.substack.com/p/the-long-read", record["url"].string)
        assertEquals("Example", record["author"].string)
        assertEquals("The long read", record["text"].string)
        assertEquals(0, record["images"].list.size)
    }

    @Test
    fun theSharedRowShowsWhatTheCardDoes() {
        val shown = SubstackShared.post(post, "https://substackcdn.com/logo.png")
        assertEquals(post.key, shown.id)
        assertEquals("example", shown.authorId, "the publication, as the groups hold it")
        assertEquals("Example", shown.authorName)
        assertEquals("example.substack.com", shown.authorHandle)
        assertEquals("https://substackcdn.com/logo.png", shown.avatarUrl)
        assertEquals("The long read", shown.title)
        assertEquals(listOf(TextRun.Plain("Why it matters")), shown.text)
        assertEquals(listOf("https://substackcdn.com/image/cover.jpg"), shown.media.map { it.imageUrl })
        assertEquals(Instant.parse("2026-11-02T08:00:00Z"), shown.createdAt)
        assertEquals(3L, shown.replies)
        assertEquals(12L, shown.likes)
    }

    @Test
    fun savedDrawsAKeptArticleHeadlineOverItsExcerpt() {
        val archive = PluginPostArchive.ofLink(SubstackShared.archive(post).content, SubstackShared.SOURCE)!!
        val shown = SubstackShared.saved(archive)
        assertEquals(archive.id, shown.id, "what the row files and takes out")
        assertEquals("The long read", shown.title)
        assertEquals(listOf(TextRun.Plain("Why it matters")), shown.text)
        assertEquals("Ann Writer", shown.authorName)
        assertEquals("example.substack.com", shown.authorHandle)
        assertEquals("https://example.substack.com/p/the-long-read", shown.url)
    }

    @Test
    fun aNoteIsKeptWithItsWordsAndItsPublication() {
        val note = SubstackNote(
            id = "c-1",
            body = "A first line\nand a second",
            authorName = "Ann",
            authorHandle = "ann",
            imageUrl = "https://substackcdn.com/note.jpg",
            url = "https://substack.com/@ann/note/c-1",
            publication = SubstackPublication("example", "https://example.substack.com", "Example"),
        )
        val archive = SubstackShared.archive(note)!!
        assertEquals("substack:https://substack.com/@ann/note/c-1", archive.id)
        val shown = SubstackShared.saved(PluginPostArchive.ofLink(archive.content, SubstackShared.SOURCE)!!)
        assertNull(shown.title, "a note has no headline")
        assertEquals(listOf(TextRun.Plain("A first line\nand a second")), shown.text)
        assertEquals(listOf("https://substackcdn.com/note.jpg"), shown.media.map { it.imageUrl })
        assertNull(SubstackShared.archive(note.copy(url = null)), "a note without its own page has no sheet")
    }
}
