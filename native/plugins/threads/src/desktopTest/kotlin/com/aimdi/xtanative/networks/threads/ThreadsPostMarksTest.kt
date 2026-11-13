package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a post's marks say beside its words: edited, liked by the author, who may reply, its song; each harmless when gone. */
class ThreadsPostMarksTest {
    private fun read(info: Map<String, Any?>? = null, extra: Map<String, Any?> = emptyMap()): ThreadsPost =
        ThreadsParser.post(j(postJson("1", info = info, extra = extra)))!!

    @Test
    fun anEditedPostAndAReplyTheAuthorLikedAreMarkedAsCiSawThem() {
        val post = read(info = mapOf("is_liked_by_root_author" to true), extra = mapOf("caption_is_edited" to true))
        assertTrue(post.facts.edited)
        assertTrue(post.facts.likedByAuthor)
        val plain = read(info = mapOf("is_liked_by_root_author" to false), extra = mapOf("caption_is_edited" to "yes"))
        assertFalse(plain.facts.edited, "only a true flag marks a post")
        assertFalse(plain.facts.likedByAuthor)
        assertTrue(read().facts.isEmpty, "a post without the keys has no marks")
    }

    @Test
    fun whoMayReplyIsReadFromTheWordsMetaUsesAndNothingElse() {
        assertEquals(ThreadsReplyAudience.FOLLOWED, ThreadsReplyAudience.of("accounts_you_follow"))
        assertEquals(ThreadsReplyAudience.FOLLOWERS, ThreadsReplyAudience.of("FOLLOWERS_ONLY"))
        assertEquals(ThreadsReplyAudience.MENTIONED, ThreadsReplyAudience.of("mentioned_only"))
        assertEquals(ThreadsReplyAudience.AUTHOR, ThreadsReplyAudience.of("parent_post_author_only"))
        assertNull(ThreadsReplyAudience.of("everyone"))
        assertNull(ThreadsReplyAudience.of("0"), "a number names nothing known")
        assertNull(ThreadsReplyAudience.of("something_new"))
        assertNull(ThreadsReplyAudience.of(null))
        assertEquals(ThreadsReplyAudience.MENTIONED, read(info = mapOf("reply_control" to "mentioned_only")).facts.replyAudience)
        assertEquals(ThreadsReplyAudience.FOLLOWED, read(info = mapOf("reply_control" to mapOf("mode" to "accounts_you_follow"))).facts.replyAudience)
        assertNull(read(info = mapOf("reply_control" to 1)).facts.replyAudience)
    }

    @Test
    fun aPostsSongIsNamedFromTheShapeCiSawOrInstagramsAndNeverWithoutATitle() {
        val asset = mapOf(
            "display_title" to mapOf("text" to "Made-up Song"),
            "display_artist" to mapOf("text" to "Made-up Band"),
            "display_image" to mapOf("downloadable_uri" to "https://cdn.example/cover.jpg"),
            "dash_manifest" to "<MPD/>",
        )
        val seen = read(extra = mapOf("text_app_music_info" to mapOf("is_available_for_consumption" to true, "music_consumable_video" to mapOf("audio_metadata" to mapOf("audio_asset" to asset)))))
        assertEquals(ThreadsMusic("Made-up Song", "Made-up Band", "https://cdn.example/cover.jpg"), seen.facts.music)
        assertEquals("Made-up Song · Made-up Band", seen.facts.music?.line)

        val instagram = read(extra = mapOf("text_app_music_info" to mapOf("music_asset_info" to mapOf("title" to "Tune", "display_artist" to "Someone", "cover_artwork_thumbnail_uri" to "http://cdn.example/x.jpg"))))
        assertEquals(ThreadsMusic("Tune", "Someone", null), instagram.facts.music, "a picture over plain http is not loaded")

        assertNull(read(extra = mapOf("text_app_music_info" to mapOf("music_consumable_video" to mapOf("audio_metadata" to mapOf("audio_asset" to mapOf("display_artist" to mapOf("text" to "No title"))))))).facts.music)
        assertNull(read(extra = mapOf("text_app_music_info" to "muted")).facts.music)
        assertEquals("Tune", ThreadsMusic("Tune").line)
    }
}
