package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.feed.Posts.post
import com.aimdi.xtanative.core.feed.Posts.thread
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MuteListTest {
    private val now = Instant.parse("2026-11-27T12:00:00Z")

    @Test
    fun `a word matches in any case and on word boundaries only`() {
        val mutes = MuteList().withWord("Cat")
        assertEquals("Cat", mutes.reason(thread(post(1, text = "my CAT sleeps")), now))
        assertNull(mutes.reason(thread(post(2, text = "a category of things")), now))
        assertNull(mutes.reason(thread(post(3, text = "concatenate")), now))
    }

    @Test
    fun `a phrase or a cashtag matches as written`() {
        val mutes = MuteList().withWord("black friday").withWord("\$TSLA")
        assertEquals("black friday", mutes.reason(thread(post(1, text = "Black Friday deals")), now))
        assertEquals("\$TSLA", mutes.reason(thread(post(2, text = "buy \$tsla now")), now))
    }

    @Test
    fun `words are read in what a post reposts and quotes`() {
        val mutes = MuteList().withWord("spoiler")
        val quoting = post(1, text = "look", quoted = post(2, text = "big spoiler here"))
        assertEquals("spoiler", mutes.reason(thread(quoting), now))
        val repost = post(3, by = "b", text = "RT", repostOf = post(4, text = "spoiler"))
        assertEquals("spoiler", mutes.reason(thread(repost), now))
    }

    @Test
    fun `an account matches by handle in any case, with or without the at`() {
        val mutes = MuteList().withAccount("@Noisy")
        assertEquals("@Noisy", mutes.reason(thread(post(1, by = "9", screenName = "noisy")), now))
        assertNull(mutes.reason(thread(post(2, by = "8", screenName = "noisy2")), now))
        assertTrue(mutes.mutes("NOISY", null, now))
    }

    @Test
    fun `an account matches by id after a rename`() {
        val mutes = MuteList().withAccount("old", id = "42")
        assertEquals("@old", mutes.reason(thread(post(1, by = "42", screenName = "renamed")), now))
    }

    @Test
    fun `a muted account's repost and quote are muted too`() {
        val mutes = MuteList().withAccount("noisy")
        val repost = post(1, by = "friend", repostOf = post(2, by = "n", screenName = "noisy"))
        assertEquals("@noisy", mutes.reason(thread(repost), now))
        val quote = post(3, by = "friend", quoted = post(4, by = "n", screenName = "noisy"))
        assertEquals("@noisy", mutes.reason(thread(quote), now))
    }

    @Test
    fun `the profile on screen sets aside its own mute but not words`() {
        val mutes = MuteList().withAccount("noisy").withWord("cat")
        val own = thread(post(1, by = "n", screenName = "noisy", text = "hello"))
        assertNull(mutes.reason(own, now, exceptHandle = "Noisy"))
        assertEquals("cat", mutes.reason(thread(post(2, by = "n", screenName = "noisy", text = "cat")), now, exceptHandle = "noisy"))
    }

    @Test
    fun `an expired entry mutes nothing and drops from the active list`() {
        val mutes = MuteList()
            .withWord("cat", until = now.minus(Duration.ofMinutes(1)))
            .withAccount("noisy", until = now.plus(Duration.ofDays(1)))
        assertNull(mutes.reason(thread(post(1, text = "cat")), now))
        assertEquals("@noisy", mutes.reason(thread(post(2, by = "n", screenName = "noisy")), now))
        assertNull(mutes.reason(thread(post(3, by = "n", screenName = "noisy")), now.plus(Duration.ofDays(2))))
        assertEquals(1, mutes.activeAt(now).size)
    }

    @Test
    fun `adding again replaces, whatever the case`() {
        val mutes = MuteList().withWord("cat").withWord("CAT", until = now).withAccount("a").withAccount("@A", id = "7")
        assertEquals(listOf(MutedKeyword("CAT", now, KeywordAction.FOLD)), mutes.words)
        assertEquals(listOf(MutedAccount("A", "7")), mutes.accounts)
    }

    @Test
    fun `typed text is an account when it is an at handle, else a word`() {
        val mutes = MuteList().withTyped("@someone").withTyped("@ not a handle").withTyped("  ")
        assertEquals(listOf("someone"), mutes.accounts.map { it.handle })
        assertEquals(listOf("@ not a handle"), mutes.words.map { it.term })
    }

    @Test
    fun `other networks match their own accounts and every word`() {
        val mutes = MuteList().withAccount("noisy").withAccount("loud", network = "bluesky").withWord("cat")
        assertNull(mutes.reason("bluesky", "noisy", "did:1", "hello", now))
        assertEquals("@loud", mutes.reason("bluesky", "Loud", "did:2", "hello", now))
        assertEquals("cat", mutes.reason("mastodon", "x", "1", "a cat", now))
    }

    @Test
    fun `the stored form reads back`() {
        val mutes = MuteList()
            .withWord("cat", until = now)
            .withAccount("noisy", id = "9")
            .withAccount("loud", network = "bluesky", until = now)
        assertEquals(mutes, MuteList.parse(mutes.encode()))
        assertEquals("", MuteList().encode())
        assertTrue(MuteList.parse("not json").isEmpty)
        assertTrue(MuteList.parse(null).isEmpty)
    }

    @Test
    fun `removing takes out only that entry`() {
        val mutes = MuteList().withWord("a").withWord("b").withAccount("x")
        assertEquals(listOf("b"), mutes.withoutWord("a").words.map { it.term })
        assertFalse(mutes.withoutAccount(mutes.accounts.single()).mutes("x", null, now))
        assertFalse(mutes.unmuting("@X", null).mutes("x", null, now))
    }
}
