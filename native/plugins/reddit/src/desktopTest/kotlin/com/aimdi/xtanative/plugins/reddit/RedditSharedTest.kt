package com.aimdi.xtanative.plugins.reddit

import com.aimdi.xtanative.networks.reddit.RedditPost
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.store.settings.RedditNsfwMode
import com.aimdi.xtanative.store.settings.RedditSort
import com.aimdi.xtanative.store.settings.RedditTimeFilter
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.TextRun
import java.time.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RedditSharedTest {
    private val choices = RedditChoices(RedditSort.HOT, RedditTimeFilter.DAY, RedditNsfwMode.TAP, showSpoilers = false, preferPublic = false)

    private fun post(id: String = "abc", vararg extra: Pair<String, Any?>): RedditPost {
        val base = RedditPost(id = id, title = "A title", subreddit = "Kotlin", permalink = "/r/Kotlin/comments/$id/a_title/", author = "jb", createdAt = Instant.ofEpochSecond(1_700_000_000))
        return extra.fold(base) { post, (key, value) ->
            when (key) {
                "self" -> post.copy(isSelf = true, selfText = value as String)
                "image" -> post.copy(url = value as String)
                "video" -> post.copy(videoManifestUrl = value as String, previewImage = "https://preview.redd.it/p.jpg")
                "link" -> post.copy(url = value as String, domain = "example.com", previewImage = "https://preview.redd.it/l.jpg")
                "nsfw" -> post.copy(over18 = true)
                "spoiler" -> post.copy(spoiler = true)
                else -> error(key)
            }
        }
    }

    @Test
    fun aPostIsFiledInXtasRecordAndReadBack() {
        val original = post("q1", "self" to "Hello")
        val archive = RedditArchive.of(original)
        assertEquals("reddit:q1", archive.id)
        assertEquals("Kotlin", archive.authorId, "groups and profiles match a post on its community")
        assertEquals(original.title, RedditArchive.post(archive.content)?.title)
        assertEquals(original.permalink, RedditArchive.post(archive.content)?.permalink)
        val kept = SavedContent.of(archive.content)
        assertEquals("reddit", kept.network)
        assertTrue(kept.matches("hello"))
    }

    @Test
    fun anotherNetworksRecordIsNoneOfItsAndADamagedOneIsNothing() {
        assertNull(RedditArchive.post(PluginPostArchive.link("bluesky", "https://bsky.app/x", "a", "b").content))
        assertNull(RedditArchive.post(JsonPrimitive("nope")))
    }

    @Test
    fun aDiscussionPostShowsItsTitleAndTheStartOfItsWordsWithTheirLinks() {
        val long = "See https://kotlinlang.org for more. " + "word ".repeat(100)
        val shared = RedditShared.post(post("s", "self" to long), "https://icon", choices, "en")
        assertEquals("A title", shared.title)
        assertEquals("r/Kotlin", shared.authorName)
        assertEquals("jb", shared.authorHandle)
        assertEquals("https://icon", shared.avatarUrl)
        assertTrue(shared.text.any { it is TextRun.Link && it.url == "https://kotlinlang.org" })
        assertTrue(shared.text.joinToString("") { it.text }.endsWith("…"))
        assertTrue(shared.text.joinToString("") { it.text }.length <= RedditShared.WORDS + 1)
        assertEquals("https://www.reddit.com/r/Kotlin/comments/s/a_title/", shared.url)
    }

    @Test
    fun aPictureAVideoAndALinkAreTheSharedRowsMediaAndCard() {
        assertEquals(listOf("https://i.redd.it/x.jpg"), RedditShared.post(post("i", "image" to "https://i.redd.it/x.jpg"), null, choices, "en").media.map { it.imageUrl })
        val video = RedditShared.post(post("v", "video" to "https://v.redd.it/abc/DASHPlaylist.mpd"), null, choices, "en").media.single()
        assertEquals(MediaItem.Kind.VIDEO, video.kind)
        assertEquals("https://v.redd.it/abc/HLSPlaylist.m3u8", video.videoUrl)
        val link = RedditShared.post(post("l", "link" to "https://example.com/story"), null, choices, "en")
        assertEquals("https://example.com/story", link.card?.url)
        assertEquals("example.com", link.card?.title)
        assertTrue(link.media.isEmpty())
    }

    @Test
    fun theCoverTheReaderChoseHidesTheMediaBehindItsWord() {
        assertEquals(Strings.nsfw.get("en"), RedditShared.post(post("n", "nsfw" to true), null, choices, "en").warning)
        assertEquals(Strings.spoiler.get("en"), RedditShared.post(post("p", "spoiler" to true), null, choices, "en").warning)
        assertNull(RedditShared.post(post("p", "spoiler" to true), null, choices.copy(showSpoilers = true), "en").warning)
        assertNull(RedditShared.post(post("n", "nsfw" to true), null, choices.copy(nsfw = RedditNsfwMode.SHOW), "en").warning)
    }

    @Test
    fun thePluginsSavedListFollowsWhatIsFiledInTheArchive() {
        val a = post("a")
        assertEquals(listOf("a"), RedditSaved.mirrored(emptyList(), a, filed = true).map { it.id })
        assertEquals(listOf("a"), RedditSaved.mirrored(listOf(a), a, filed = true).map { it.id }, "already there: once")
        assertTrue(RedditSaved.mirrored(listOf(a), a, filed = false).isEmpty())
        assertTrue(RedditSaved.mirrored(emptyList(), a, filed = false).isEmpty())
    }
}
