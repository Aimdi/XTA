package com.aimdi.xtanative.networks.hackernews

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Hearts, saved stories and follows, kept on the device as XTA's hn_store.dart keeps them. */
class HnDeviceTest {
    private fun story(id: Long, at: String? = null) = HnStory(id = id, title = "Story $id", url = "https://example.com/$id", author = "pg", score = 5, commentCount = 2, createdAt = at?.let(Instant::parse))

    @Test
    fun aHeartIsKeptByTheStorysIdAndTakenBack() {
        val liked = HnDevice.toggleLike(emptyList(), 8863)
        assertEquals(listOf("8863"), liked)
        assertEquals(emptyList(), HnDevice.toggleLike(liked, 8863))
        assertEquals(liked, HnDevice.strings(HnDevice.stringsText(liked)))
    }

    @Test
    fun pastTheCapTheNewestHeartDoesNotStay() {
        val full = (1..HnDevice.LIKES_CAP).map { "$it" }
        assertEquals(full, HnDevice.toggleLike(full, 9999))
    }

    @Test
    fun followsAreListedByNameAndFollowedWhateverTheirCase() {
        val follows = HnDevice.follows("""["zed","Alice","bob"]""")
        assertEquals(listOf("Alice", "bob", "zed"), follows)
        assertTrue(HnDevice.isFollowing(follows, "ALICE"))
        assertEquals(listOf("bob", "zed"), HnDevice.toggleFollow(follows, "alice"))
        assertEquals(listOf("pg", "Alice", "bob", "zed"), HnDevice.toggleFollow(follows, " pg "))
        assertEquals(follows, HnDevice.toggleFollow(follows, "  "))
    }

    @Test
    fun aSavedStoryComesFirstAndAtMostTwoHundredAreKept() {
        val saved = HnDevice.toggleSaved(listOf(story(1)), story(2))
        assertEquals(listOf(2L, 1L), saved.map { it.id })
        assertEquals(listOf(1L), HnDevice.toggleSaved(saved, story(2)).map { it.id })
        val many = (1L..HnDevice.SAVED_CAP).map { story(it) }
        assertEquals(HnDevice.SAVED_CAP, HnDevice.toggleSaved(many, story(999)).size)
    }

    @Test
    fun savedStoriesReadAsXtaWroteThem() {
        val xta = """[{"id":42,"title":"Hello","url":null,"text":"Ask HN body","author":"dang","score":12,"commentCount":3,"createdAt":"2024-01-02T03:04:05.000Z","type":"ask"},{"title":"no id"}]"""
        val read = HnDevice.saved(xta).single()
        assertEquals(HnStory(42, "Hello", null, "Ask HN body", "dang", 12, 3, Instant.parse("2024-01-02T03:04:05Z"), "ask"), read)
        assertEquals(read, HnDevice.saved(HnDevice.savedText(listOf(read))).single())
        assertEquals("2024-01-02T03:04:05Z", J(HnDevice.json(read))["createdAt"].string)
        assertEquals(emptyList(), HnDevice.saved("not json"))
    }

    @Test
    fun followingMergesTheAccountsStoriesNewestFirst() {
        val merged = HnDevice.following(listOf(listOf(story(1, "2024-01-01T00:00:00Z"), story(3)), listOf(story(2, "2024-02-01T00:00:00Z"), story(1, "2024-01-01T00:00:00Z"))))
        assertEquals(listOf(2L, 1L, 3L), merged.map { it.id })
        assertFalse(merged.size > HnDevice.FOLLOWING_LIMIT)
    }
}
