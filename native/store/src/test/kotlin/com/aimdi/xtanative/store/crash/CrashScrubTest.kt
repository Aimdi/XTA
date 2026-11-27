package com.aimdi.xtanative.store.crash

import com.aimdi.xtanative.core.x.XAnswerException
import com.aimdi.xtanative.core.x.XRequestException
import com.aimdi.xtanative.networks.FakeNetworkRequestException
import com.aimdi.xtanative.store.settings.CrashReportPrefs
import com.aimdi.xtanative.store.settings.Settings
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class CrashScrubTest {
    private fun scrubbed(text: String) = CrashScrub.scrubbed(text, home = "/home/alice", user = "alice")

    @Test
    fun theHomeFolderAndTheUsersNameGo() {
        assertEquals(
            "java.io.FileNotFoundException: ~/.local/share/xta-native/feed.json (owned by [user])",
            scrubbed("java.io.FileNotFoundException: /home/alice/.local/share/xta-native/feed.json (owned by alice)"),
        )
        assertEquals("/run/media/[user]/disk and /home/alicea", scrubbed("/run/media/alice/disk and /home/alicea"))
        assertEquals("\tat com.example.Malice.run(Malice.kt:3)", scrubbed("\tat com.example.Malice.run(Malice.kt:3)"), "stack frames stay whole")
    }

    @Test
    fun handlesGoButObjectNamesStay() {
        assertEquals("X answered UserTweets for @[handle] with 403", scrubbed("X answered UserTweets for @reader_1 with 403"))
        assertEquals("no @[handle] and @[handle]", scrubbed("no @bob.bsky.social and @carol@mastodon.social"))
        assertEquals("com.example.Thing@6d06d69c", scrubbed("com.example.Thing@6d06d69c"))
    }

    @Test
    fun annotationsAndFramesInCrashTextStay() {
        val compose = "java.lang.IllegalStateException: @Composable invocations can only happen from the context of a @Composable function"
        assertEquals(compose, scrubbed(compose))
        val optIn = "needs @OptIn(ExperimentalFoundationApi::class) or @ExperimentalFoundationApi"
        assertEquals(optIn, scrubbed(optIn))
        val qualified = "@kotlin.Metadata(mv={2, 1}) on @androidx.compose.runtime.Composable"
        assertEquals(qualified, scrubbed(qualified))
        val frames = "\tat androidx.compose.runtime.ComposerImpl.start(Composer.kt:1)\n\tat java.base@17.0.21/java.lang.Thread.run(Thread.java:840)\n" +
            "\tat com.aimdi.xtanative.ui.ComposableSingletons\$HomeKt\$lambda-1\$1.invoke(Home.kt:9)"
        assertEquals(frames, scrubbed(frames))
        assertEquals("for @[handle] and @[handle]", scrubbed("for @Composer and @dave.bsky.social"), "a handle that is no annotation still goes")
    }

    @Test
    fun addressesKeepOnlyAKnownHost() {
        assertEquals(
            "failed: https://x.com/[path] and http://[host]",
            scrubbed("failed: https://me:pw@x.com/alice/status/1?ct0=abc&q=secret and http://example.org"),
        )
        assertEquals(
            "PROPFIND https://[host]/[path] failed; then https://pbs.twimg.com/[path] and https://api.github.com/[path]",
            scrubbed("PROPFIND https://cloud.alice-home.de:8443/remote.php/dav/files/alice/ failed; then https://pbs.twimg.com/media/a.jpg and https://api.github.com/repos/a/b"),
        )
        assertEquals("https://[host]/[path]", scrubbed("https://x.com.evil.example/a"), "a known name inside another host is no known host")
        assertEquals("wss://[host]", scrubbed("wss://192.168.178.68:2283"))
        assertEquals("(see https://x.com:443) and (https://[host])", scrubbed("(see https://x.com:443) and (https://immich.alice.lan)"))
    }

    @Test
    fun eMailAddressesGo() {
        assertEquals("login as [email] refused", scrubbed("login as alice.smith+xta@mail.example.org refused"))
        assertEquals("([email])", scrubbed("(bob@posteo.de)"))
        assertEquals("no @[handle] and @[handle]", scrubbed("no @bob.bsky.social and @carol@mastodon.social"), "a fediverse handle stays a handle")
        assertEquals("\tat java.base@17.0.21/java.lang.Thread.run(Thread.java:840)", scrubbed("\tat java.base@17.0.21/java.lang.Thread.run(Thread.java:840)"))
    }

    @Test
    fun credentialsAndQuotedInputGo() {
        val text = scrubbed("Unexpected JSON token at offset 4: Expected quotation\nJSON input: {\"screen_name\":\"alice2\"}\ncookie: auth_token=feedface")
        assertFalse("alice2" in text, text)
        assertFalse("feedface" in text, text)
        assertTrue("JSON input: [cut]" in text, text)
    }

    @Test
    fun aKeptReportIsWrittenScrubbed() {
        val home = System.getProperty("user.home")
        val error = IllegalStateException("could not read $home/notes for @someone", RuntimeException("https://x.com/i/api/graphql/abc/UserByScreenName?variables=secret"))
        val text = CrashReports.text(error, "main", "1.2.3", Instant.EPOCH)
        assertFalse("@someone" in text)
        assertFalse("variables=secret" in text)
        if (home.length > 1) assertFalse("$home/notes" in text)
        assertTrue("at com.aimdi.xtanative.store.crash.CrashScrubTest" in text, "frames stay")
    }

    @Test
    fun readFailuresAreNotFiled() {
        assertFalse(CrashIssue.shouldReport(XRequestException(403, "X answered UserTweets for @me with 403")))
        assertFalse(CrashIssue.shouldReport(XAnswerException(null, "Something went wrong")))
        assertFalse(CrashIssue.shouldReport(FakeNetworkRequestException("reddit answered 503")))
        assertTrue(CrashIssue.shouldReport(IllegalArgumentException("boom")))
    }

    @Test
    fun aReportKeptBeforeScrubbingIsFiledScrubbed(): Unit = runBlocking {
        val dir: File = Files.createTempDirectory("xta-crash").toFile()
        val kept = CrashReports(dir)
        val body = CrashIssue.body("java.lang.IllegalStateException: for @alice", "\tat a.B.c(B.kt:1)", "1", "Linux", "main", Instant.EPOCH)
        File(dir, "crash-5.txt").writeText(body)
        File(dir, "crash-5.unsent").writeText("")
        var sent = ""
        val reporter = CrashReporter({ _, _, b -> sent = b; 201 }, { Settings().with(CrashReportPrefs.enabled, true).with(CrashReportPrefs.githubToken, "t") }, kept, "1")
        reporter.sendKept()
        assertTrue("@[handle]" in sent, sent)
        assertFalse("@alice" in sent, sent)
        dir.deleteRecursively()
    }
}
