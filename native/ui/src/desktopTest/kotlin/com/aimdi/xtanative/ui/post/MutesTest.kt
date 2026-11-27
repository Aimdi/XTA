package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.feed.MuteList
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.TextRun
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MutesTest {
    private val now = Instant.parse("2026-11-27T12:00:00Z")
    private val mutes = MuteList().withAccount("noisy").withWord("cat")

    private fun post(id: String, handle: String, text: String = "hi") = XPost(id, XUser(handle + "-id", handle, handle), null, text)

    private fun scope(focal: String? = null, profile: String? = null) = PostScope(PostPrefs(), SensitiveReveal(false), focalId = focal, profileScreenName = profile)

    @Test
    fun `a feed folds a muted account's thread`() {
        assertEquals("@noisy", Mutes.reason(mutes, XThread("1", listOf(post("1", "noisy"))), scope(), now))
    }

    @Test
    fun `the opened post is never folded, its replies are`() {
        val focal = XThread("1", listOf(post("1", "noisy")))
        assertNull(Mutes.reason(mutes, focal, scope(focal = "1"), now))
        assertEquals("cat", Mutes.reason(mutes, XThread("2", listOf(post("2", "b", "a cat"))), scope(focal = "1"), now))
    }

    @Test
    fun `a muted account's own profile shows its posts`() {
        assertNull(Mutes.reason(mutes, XThread("1", listOf(post("1", "noisy"))), scope(profile = "Noisy"), now))
    }

    @Test
    fun `a plugin's post folds on a word in its title or text`() {
        val titled = NetworkPost("r1", "u", "U", "u", title = "My cat", text = listOf(TextRun.Plain("hello")))
        assertEquals("cat", Mutes.reason(mutes, "reddit", titled, now))
        assertNull(Mutes.reason(mutes, "bluesky", NetworkPost("b1", "did", "Noisy", "noisy"), now))
    }
}
