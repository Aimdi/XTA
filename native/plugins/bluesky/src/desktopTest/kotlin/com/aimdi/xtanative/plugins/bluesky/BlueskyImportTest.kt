package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.networks.bluesky.ok
import com.aimdi.xtanative.ui.plugins.FeedKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

/** XTA's bluesky_import_test.dart and bluesky_interleaved_test.dart: what the import of an account's follows takes, and what it refuses out loud. */
class BlueskyImportTest {
    @Test
    fun aHandleItCannotReadIsRefusedAndAsksNothing() = runBlocking {
        val host = FakeHost { ok("""{"follows":[{"did":"did:plc:b","handle":"b.bsky.social"}]}""") }
        val app = BlueskyApp(host)
        val scope = CoroutineScope(coroutineContext + Job())
        val run = ImportRun(app, scope)
        assertFalse(run.start(ImportKind.FOLLOWING, "elios", "en"), "the screen says the handle is not one")
        assertFalse(run.start(ImportKind.FOLLOWING, "   ", "en"))
        assertTrue(host.asked.isEmpty())
        assertEquals(ImportRun.State.Idle, run.state.value)
        assertTrue(run.start(ImportKind.FOLLOWING, "https://bsky.app/profile/alice.bsky.social", "en"))
        eventually { run.state.value is ImportRun.State.Done }
        assertEquals("alice.bsky.social", host.asked.first().queryParameter("actor"))
        scope.cancel()
        app.close()
    }

    private fun view(id: String, date: String?, reply: Boolean = false): String {
        val created = date?.let { ""","createdAt":"$it"""" } ?: ""
        val parent = if (reply) ""","reply":{"parent":{"uri":"at://did:plc:a/app.bsky.feed.post/p"},"root":{"uri":"at://did:plc:a/app.bsky.feed.post/p"}}""" else ""
        return """{"post":{"uri":"at://did:plc:a/app.bsky.feed.post/$id","cid":"c$id","author":{"did":"did:plc:a","handle":"alice.bsky.social"},"record":{"text":"$id"$created$parent}}}"""
    }

    @Test
    fun homeTakesTenDatedPostsAndLeavesRepliesOutWhereTheyAreOff() = runBlocking {
        val rows = (1..12).map { view("p$it", "2026-08-%02dT00:00:00Z".format(it)) } + view("undated", null) + view("reply", "2026-08-20T00:00:00Z", reply = true)
        val host = FakeHost { ok("""{"feed":[${rows.joinToString(",")}]}""") }
        val app = BlueskyApp(host)
        val all = app.feed.posts(listOf("did:plc:a"), FeedKinds.ALL, force = false)
        assertEquals(10, all.size)
        assertTrue(all.none { it.text.joinToString { run -> run.text }.contains("undated") }, "a post without a date has no place among the others")
        assertTrue(all.any { it.replyingTo != null || it.id.endsWith("/reply") })
        val noReplies = app.feed.posts(listOf("did:plc:a"), FeedKinds(replies = false), force = false)
        assertTrue(noReplies.none { it.id.endsWith("/reply") })
        app.close()
    }
}
