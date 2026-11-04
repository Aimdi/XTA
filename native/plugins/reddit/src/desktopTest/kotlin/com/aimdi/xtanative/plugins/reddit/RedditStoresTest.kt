package com.aimdi.xtanative.plugins.reddit

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.reddit.RedditComment
import com.aimdi.xtanative.networks.reddit.RedditPost
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.settings.RedditNsfwMode
import com.aimdi.xtanative.store.settings.RedditPrefs
import com.aimdi.xtanative.store.settings.RedditSort
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class RedditStoresTest {
    private val folder: File = Files.createTempDirectory("reddit-plugin").toFile()

    private fun file(name: String) = StoreFile(File(folder, name))

    private val at = Instant.parse("2026-11-04T09:30:00Z")

    @Test
    fun thePreferencesAreKeptUnderXtasKeys(): Unit = runBlocking {
        RedditPrefsStore(file("settings.json")).update { it.with(RedditPrefs.sort, RedditSort.TOP).with(RedditPrefs.nsfwMode, RedditNsfwMode.HIDE) }
        val read = RedditPrefsStore(file("settings.json")).load()
        assertEquals(RedditSort.TOP, read[RedditPrefs.sort])
        assertEquals(RedditNsfwMode.HIDE, read[RedditPrefs.nsfwMode])
        assertEquals("top", J.parse(File(folder, "settings.json").readText())["preferences"]["plugin.reddit.sort"].string)
    }

    @Test
    fun theBackupCarriesThePreferencesButNeverTheSignInOrTheClientId(): Unit = runBlocking {
        val prefs = RedditPrefsStore(file("settings.json"))
        prefs.update { it.with(RedditPrefs.sort, RedditSort.RISING).with(RedditPrefs.refreshToken, "secret").with(RedditPrefs.clientId, "app") }
        val exported = J.parse(prefs.export())
        assertEquals("rising", exported["plugin.reddit.sort"].string)
        assertFalse(exported["plugin.reddit.refresh_token"].exists)
        assertFalse(exported["plugin.reddit.client_id"].exists)

        val other = RedditPrefsStore(file("other.json"))
        other.update { it.with(RedditPrefs.refreshToken, "mine").with(RedditPrefs.nsfwMode, RedditNsfwMode.SHOW) }
        other.import("""{"plugin.reddit.sort": "newest", "plugin.reddit.refresh_token": "theirs", "plugin.unknown": 1}""")
        val restored = other.load()
        assertEquals(RedditSort.NEWEST, restored[RedditPrefs.sort])
        assertEquals("mine", restored[RedditPrefs.refreshToken])
        assertEquals(RedditNsfwMode.TAP, restored[RedditPrefs.nsfwMode], "a restore replaces what the backup holds")
    }

    @Test
    fun aCommunityIsFollowedByNameWhateverWasPastedListedAsXtaListsThem(): Unit = runBlocking {
        val subs = RedditSubscriptions(file("subscriptions.json"), RedditPrefsStore(file("settings.json"))) { at }
        assertTrue(subs.add("https://www.reddit.com/r/dartlang/"))
        assertTrue(subs.add("r/FlutterDev"))
        assertTrue(subs.add("androiddev"))
        assertTrue(subs.add("DARTLANG"))
        assertFalse(subs.add("not a name!"))
        assertEquals(listOf("androiddev", "DARTLANG", "FlutterDev"), RedditSubscriptions.names(subs.load()))
        subs.remove("dartlang")
        assertEquals(listOf("androiddev", "FlutterDev"), RedditSubscriptions.names(subs.load()))
        assertTrue(RedditSubscriptions.isFollowed(subs.load(), "flutterdev"))
    }

    @Test
    fun theFollowedCommunitiesAreXtasRowsInTheBackup(): Unit = runBlocking {
        val subs = RedditSubscriptions(file("subscriptions.json"), RedditPrefsStore(file("settings.json"))) { at }
        subs.add("dartlang")
        val row = J.parse(subs.export())[0]
        assertEquals("dartlang", row["id"].string)
        assertEquals("dartlang", row["name"].string)
        assertEquals(1L, row["in_feed"].long)
        assertTrue(row["created_at"].string.orEmpty().matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))

        val other = RedditSubscriptions(file("other.json"), RedditPrefsStore(file("other-settings.json"))) { at }
        other.add("pics")
        other.import("""[{"id": "flutterdev", "name": "FlutterDev", "in_feed": 1, "created_at": "2024-01-02 03:04:05"}, {"name": "no id"}]""")
        assertEquals(listOf("FlutterDev"), RedditSubscriptions.names(other.load()))
    }

    @Test
    fun theListXtaKeptInPreferencesComesInOnceAndLeavesThem(): Unit = runBlocking {
        val prefs = RedditPrefsStore(file("settings.json"))
        prefs.set(RedditPrefs.subreddits, """["dartlang", "r/pics", "???"]""")
        val subs = RedditSubscriptions(file("subscriptions.json"), prefs) { at }
        assertEquals(listOf("dartlang", "pics"), RedditSubscriptions.names(subs.load()))
        assertEquals("", prefs.load()[RedditPrefs.subreddits])
        assertEquals(listOf("dartlang", "pics"), RedditSubscriptions.names(RedditSubscriptions(file("subscriptions.json"), prefs) { at }.load()))
    }

    @Test
    fun anUpvoteIsKeptOnTheDeviceAndGoesInTheBackupAsXtasRows(): Unit = runBlocking {
        val votes = RedditVotes(file("votes.json"))
        votes.toggle("abc123")
        votes.toggle(RedditVotes.commentId("def456"))
        votes.toggle("gone")
        votes.toggle("gone")
        assertEquals(setOf("abc123", "t1_def456"), RedditVotes(file("votes.json")).load())
        assertEquals(listOf("abc123", "t1_def456"), J.parse(votes.export()).list.map { it["id"].string })

        val other = RedditVotes(file("other.json"))
        other.import("""[{"id": "xyz"}, {"id": ""}, {"nothing": 1}]""")
        assertEquals(setOf("xyz"), other.load())
    }

    private fun post(id: String) = RedditPost(id = id, title = "Post $id", subreddit = "dartlang", permalink = "/r/dartlang/comments/$id/x/")

    @Test
    fun aSavedPostGoesFirstAndOnlyTheNewestAreKept() {
        val saved = (1..RedditSaved.CAP).fold(emptyList<RedditPost>()) { list, n -> RedditSaved.toggled(list, post("p$n")) }
        assertEquals("p${RedditSaved.CAP}", saved.first().id)
        val more = RedditSaved.toggled(saved, post("new"))
        assertEquals(RedditSaved.CAP, more.size)
        assertEquals("new", more.first().id)
        assertFalse(more.any { it.id == "p1" })
        assertEquals(RedditSaved.CAP - 1, RedditSaved.toggled(more, post("new")).size)
    }

    @Test
    fun theSavedPostsAreKeptInTheirPreference() {
        val settings = RedditSaved.with(com.aimdi.xtanative.store.settings.Settings(), listOf(post("a"), post("b")))
        assertEquals(listOf("a", "b"), RedditSaved.posts(settings).map { it.id })
    }

    @Test
    fun aVisitIsKeptPerThreadForTheNewestTwoHundred() {
        val first = RedditVisits.recorded("{}", "/r/a/comments/x/", at)
        assertEquals(at, RedditVisits.last(first, "/r/a/comments/x"))
        assertNull(RedditVisits.last(first, "/r/a/comments/y/"))
        val many = (1..250).fold("{}") { text, n -> RedditVisits.recorded(text, "/r/a/comments/t$n/", Instant.ofEpochMilli(n * 1000L)) }
        assertEquals(RedditVisits.KEPT, RedditVisits.of(many).size)
        assertNull(RedditVisits.last(many, "/r/a/comments/t1/"))
        assertEquals(Instant.ofEpochMilli(250_000), RedditVisits.last(many, "/r/a/comments/t250/"))
        assertEquals(emptyMap(), RedditVisits.of("not json"))
    }

    @Test
    fun aCommentIsNewOnlyWhenWrittenAfterTheLastVisit() {
        val comment = RedditComment(id = "c", body = "hi", createdAt = at)
        assertFalse(RedditVisits.isNew(comment, null))
        assertTrue(RedditVisits.isNew(comment, at.minusSeconds(60)))
        assertFalse(RedditVisits.isNew(comment, at.plusSeconds(60)))
        assertFalse(RedditVisits.isNew(comment.copy(createdAt = null), at.minusSeconds(60)))
    }

    @Test
    fun aCommunitysPictureIsKeptAWeek(): Unit = runBlocking {
        var now = 0L
        val cache = RedditIconCache(file("icons.json")) { now }
        cache.put("DartLang", "https://styles.redditmedia.com/dart.png")
        assertEquals("https://styles.redditmedia.com/dart.png", RedditIconCache(file("icons.json")) { now }.get("dartlang"))
        now += 8 * 24 * 60 * 60 * 1000L
        assertNull(cache.get("dartlang"))
    }
}
