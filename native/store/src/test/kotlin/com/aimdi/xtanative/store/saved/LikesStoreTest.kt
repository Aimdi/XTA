package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.backup.FollowGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class LikesStoreTest {
    private val dir = tempStore()
    private var time = 0L

    @Test
    fun likingTwiceKeepsOneEntryAtTheTop() = runBlocking {
        val likes = LikesStore(dir) { time++ }
        likes.like("1", "u1", json("""{"id_str":"1"}"""))
        likes.like("2", "u2", null)
        likes.like("1", "u1", json("""{"id_str":"1","full_text":"newer copy"}"""))
        assertEquals(listOf("1", "2"), likes.likes().map { it.id })
        assertEquals(json("""{"id_str":"1","full_text":"newer copy"}"""), likes.likes().first().content)

        likes.unlike("1")
        assertFalse(likes.isLiked("1"))
        assertTrue(LikesStore(dir).isLiked("2"), "kept for the next launch")
    }

    @Test
    fun aBackupReplacesEveryLike() = runBlocking {
        val likes = LikesStore(dir)
        likes.like("gone", null, null)
        likes.replace(listOf(LikedPost("a", "u1", null, 2), LikedPost("b", null, null, 1), LikedPost("a", "dup", null, 0)))
        assertEquals(listOf("a", "b"), likes.content().map { it.id })
    }

    private data class Like(val id: String, val author: String?)

    private val alice = Like("a", "u1")
    private val bob = Like("b", "u2")
    private val stranger = Like("c", "u9")

    private fun split(likes: List<Like>, groups: List<FollowGroup>) = likesByGroup(likes, { it.author }, groups)

    private fun group(id: String, vararg members: String) = FollowGroup(id, id, members.toList())

    @Test
    fun eachGroupGetsItsOwnSectionInTheOrderGiven() {
        val sections = split(listOf(alice, bob), listOf(group("friends", "u2"), group("news", "u1")))
        assertEquals(listOf("friends", "news"), sections.map { it.groupId })
        assertEquals("b", sections.first().items.single().id)
        assertEquals("a", sections.last().items.single().id)
    }

    @Test
    fun anAuthorInTwoGroupsAppearsUnderBoth() {
        val sections = split(listOf(alice), listOf(group("news", "u1"), group("tech", "u1")))
        assertEquals(2, sections.size)
        assertTrue(sections.all { it.items.single().id == "a" })
    }

    @Test
    fun aLikeFromNobodysGroupLandsInTheUngroupedSectionLast() {
        val sections = split(listOf(alice, stranger), listOf(group("news", "u1")))
        assertEquals(listOf("news", null), sections.map { it.groupId })
        assertTrue(sections.last().isUngrouped)
        assertEquals("c", sections.last().items.single().id)
    }

    @Test
    fun aLikeWithNoAuthorIsUngroupedRatherThanDropped() {
        val sections = split(listOf(Like("d", null)), listOf(group("news", "u1")))
        assertTrue(sections.single().isUngrouped)
        assertEquals("d", sections.single().items.single().id)
    }

    @Test
    fun aGroupNobodyInItHasLikedFromIsLeftOut() {
        assertEquals(listOf<String?>("news"), split(listOf(alice), listOf(group("news", "u1"), group("empty"))).map { it.groupId })
    }

    @Test
    fun noLikesMeansNoSectionsAndNoGroupsPutsEverythingUnderUngrouped() {
        assertTrue(split(emptyList(), listOf(group("news", "u1"))).isEmpty())
        val sections = split(listOf(alice, bob), emptyList())
        assertTrue(sections.single().isUngrouped)
        assertEquals(2, sections.single().items.size)
    }
}
