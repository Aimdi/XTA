package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** The reader's own Xy server (XTA's threads_api.dart and threads_test). */
class ThreadsXyTest {
    private val base = "https://xy.example.org"

    @Test
    fun endpointsLieUnderTheConfiguredBaseTrailingSlashesAndAll() {
        assertEquals("https://xy.example.org/health", ThreadsXy.endpoint(base, "/health").toString())
        assertEquals("https://xy.example.org/profile/zuck", ThreadsXy.endpoint("https://xy.example.org//", "/profile/zuck").toString())
    }

    @Test
    fun aProfileReadsEveryDocumentedField() {
        val profile = ThreadsXy.profileOf(J.parse(fixture("xy_profile.json")))
        assertEquals("314216", profile.pk)
        assertEquals("zuck", profile.username)
        assertEquals("Mark Zuckerberg", profile.fullName)
        assertTrue(profile.isVerified)
        assertFalse(profile.isPrivate)
        assertEquals(listOf(4200000L, 812L, 1330L), listOf(profile.followerCount, profile.followingCount, profile.mediaCount))
        assertEquals("https://example.org", profile.externalUrl)
    }

    @Test
    fun aLinkIsOptionalAndABlankOneIsNone() {
        assertNull(ThreadsXy.profileOf(j(mapOf("username" to "zuck", "external_url" to null))).externalUrl)
        assertNull(ThreadsXy.profileOf(j(mapOf("username" to "zuck", "external_url" to "  "))).externalUrl)
    }

    @Test
    fun aFieldThatStoppedBeingSentLeavesABlankNotACrash() {
        val profile = ThreadsXy.profileOf(j(mapOf("username" to "zuck")))
        assertEquals("", profile.fullName)
        assertFalse(profile.isVerified)
        assertEquals(0L, profile.followerCount)
        assertEquals("zuck", profile.displayName, "with no display name the handle stands in for it")
        assertEquals("", ThreadsXy.profileOf(J(null)).username)
        assertEquals("", ThreadsXy.profileOf(j("not an object")).username)
    }

    @Test
    fun aProfileIsAskedWithTheTokenAndAnEncodedName() = runBlocking {
        val meta = FakeMeta { ok(fixture("xy_profile.json")) }
        assertEquals("zuck", ThreadsXy(meta).profile("$base/", " tok ", "zuck").username)
        ThreadsXy(meta).profile(base, "tok", "two words")
        val (first, second) = meta.asked
        assertEquals("https://xy.example.org/profile/zuck", first.url.toString())
        assertEquals(mapOf("Accept" to "application/json", "Authorization" to "Bearer tok"), first.headers)
        assertEquals("/profile/two%20words", second.path)
    }

    @Test
    fun healthNeedsNoToken() = runBlocking {
        val meta = FakeMeta { ok("""{"ok":true}""") }
        assertTrue(ThreadsXy(meta).health(base))
        assertEquals(mapOf("Accept" to "application/json"), meta.asked.single().headers)
        assertFalse(ThreadsXy(FakeMeta { ok("""{"ok":"yes"}""") }).health(base))
    }

    @Test
    fun idsAreReadOffTheirAnswers() = runBlocking {
        val meta = FakeMeta { ok("""{"user_id":"314216","post_id":"3400","thread_id":"3401"}""") }
        val xy = ThreadsXy(meta)
        assertEquals("314216", xy.userId(base, "tok", "zuck"))
        assertEquals("3400", xy.postId(base, "tok", "https://www.threads.com/@zuck/post/C1"))
        assertEquals("3401", xy.threadId(base, "tok", "3400"))
        assertEquals(listOf("/user-id/zuck", "/post-id/https%3A%2F%2Fwww.threads.com%2F%40zuck%2Fpost%2FC1", "/thread-id/3400"), meta.asked.map { it.path })
    }

    @Test
    fun withoutAnAddressOrATokenNothingIsAsked() {
        val meta = FakeMeta { ok("{}") }
        fun kind(read: suspend () -> Any?) = assertFailsWith<ThreadsXyException> { runBlocking { read() } }.kind
        assertEquals(ThreadsXyException.Kind.NOT_CONFIGURED, kind { ThreadsXy(meta).profile(" ", "tok", "zuck") })
        assertEquals(ThreadsXyException.Kind.NOT_CONFIGURED, kind { ThreadsXy(meta).profile(base, " ", "zuck") })
        assertEquals(ThreadsXyException.Kind.NOT_CONFIGURED, kind { ThreadsXy(meta).health("") })
        assertTrue(meta.asked.isEmpty())
    }

    @Test
    fun theServersOwnExplanationIsKept() {
        fun error(answer: XResponse) = assertFailsWith<ThreadsXyException> { runBlocking { ThreadsXy(FakeMeta { answer }).profile(base, "tok", "zuck") } }
        val rotated = error(XResponse(401, """{"error":"unauthorized","message":"The token was rotated.","upstream":"threads"}"""))
        assertEquals(ThreadsXyException.Kind.UNAUTHORIZED, rotated.kind)
        assertEquals("The token was rotated.", rotated.message)
        assertEquals("threads", rotated.upstream)
        assertEquals(401, rotated.code)
        assertEquals(ThreadsXyException.Kind.UNAUTHORIZED, error(XResponse(403, "")).kind)
        assertEquals(ThreadsXyException.Kind.NOT_FOUND, error(XResponse(404, """{"message":"No such profile"}""")).kind)
        val fellOver = error(XResponse(502, "Bad gateway"))
        assertEquals(ThreadsXyException.Kind.UPSTREAM, fellOver.kind)
        assertNull(fellOver.serverMessage)
        assertEquals("https://xy.example.org/profile/zuck: 502", fellOver.message, "a server that fell over without JSON still names its status")
        assertEquals(ThreadsXyException.Kind.UNREACHABLE, error(ok("<html></html>")).kind)
    }
}
