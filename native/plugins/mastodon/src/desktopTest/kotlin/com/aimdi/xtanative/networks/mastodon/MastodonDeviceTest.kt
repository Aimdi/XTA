package com.aimdi.xtanative.networks.mastodon

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** What the plugin keeps on the device in XTA's own formats: snapshots, follows, timeline options and reading places. */
class MastodonDeviceTest {
    private val post = MastodonPost(
        id = "110",
        acct = "alice@example.social",
        authorName = "Alice",
        text = "Hello #fediverse",
        url = "https://example.social/@alice/110",
        timelineId = "990",
        timelineAt = Instant.parse("2026-05-02T10:00:00Z"),
        avatarUrl = "https://example.social/a.png",
        spoilerText = "spoilers",
        sensitive = true,
        media = listOf(
            MastodonMedia(MastodonMedia.Kind.IMAGE, "https://example.social/1.jpg", "https://example.social/1-full.jpg", "a cat", 1.5),
            MastodonMedia(MastodonMedia.Kind.GIFV, "https://example.social/2.jpg", "https://example.social/2.mp4"),
        ),
        publishedAt = Instant.parse("2026-05-01T09:00:00Z"),
        editedAt = Instant.parse("2026-05-01T09:30:00Z"),
        boosted = true,
        boostedBy = "Bob",
        boostedByAcct = "bob@other.social",
        replyToAcct = "carol@example.social",
        replyToId = "100",
        mentionAccts = listOf("carol@example.social"),
        repliesCount = 3,
        reblogsCount = 4,
        favouritesCount = 5,
        quote = MastodonPost(id = "7", acct = "dan@example.social", authorName = "Dan", text = "quoted", url = "https://example.social/@dan/7"),
        linkCard = MastodonLinkCard("https://news.example/a", "A title", "A line", "https://news.example/a.png", "News", "link"),
        poll = MastodonPoll(listOf(MastodonPoll.Option("Yes", 2), MastodonPoll.Option("No", 1)), votesCount = 3, multiple = true, votersCount = 2, expiresAt = Instant.parse("2026-06-01T00:00:00Z")),
    )

    @Test
    fun aSnapshotKeepsEverythingACardShows() {
        assertEquals(post, MastodonSnapshot.decode(MastodonSnapshot.encode(post)))
    }

    @Test
    fun aSnapshotIsWrittenInXtasParallelLists() {
        val json = J(MastodonSnapshot.encode(post))
        assertEquals(1, json["version"].int)
        assertEquals(listOf("https://example.social/1.jpg", "https://example.social/2.jpg"), json["images"].list.map { it.string })
        assertEquals(listOf(false, true), json["imageIsVideo"].list.map { it.boolean })
        assertEquals(listOf(false, true), json["imageIsGif"].list.map { it.boolean })
        assertEquals("https://example.social/2.mp4", json["imageDownloadUrls"].list[1].string)
        assertEquals("Alice", json["name"].string)
        assertEquals(5, json["favourites"].int)
    }

    @Test
    fun xtasOwnSnapshotReads() {
        val xta = """{"version":1,"id":"5","acct":"eve@x.social","name":"Eve","text":"hi","url":"https://x.social/@eve/5",
            "images":["https://x.social/v.jpg"],"imageAspects":[null],"imageAlts":[null],"imageDownloadUrls":["https://x.social/v.mp4"],
            "imageIsVideo":[true],"imageIsGif":[false],"published":"2025-01-02T03:04:05.000Z","replies":1,"boosts":2,"favourites":3}"""
        val read = MastodonSnapshot.decode(Json.parseToJsonElement(xta))!!
        assertEquals(MastodonMedia.Kind.VIDEO, read.media.single().kind)
        assertEquals("https://x.social/v.mp4", read.media.single().videoUrl)
        assertEquals(Instant.parse("2025-01-02T03:04:05Z"), read.publishedAt)
        assertEquals(3, read.favouritesCount)
    }

    @Test
    fun aSnapshotWithoutAnAuthorOrAWebAddressIsNone() {
        val base = MastodonSnapshot.encode(post)
        assertNull(MastodonSnapshot.decode(JsonObject(base + ("acct" to Json.parseToJsonElement("\"\"")))))
        assertNull(MastodonSnapshot.decode(JsonObject(base + ("url" to Json.parseToJsonElement("\"ftp://example.social/x\"")))))
        assertNull(MastodonSnapshot.decode(JsonObject(base + ("version" to Json.parseToJsonElement("2")))))
    }

    @Test
    fun savedKeepsAPostByItsAddress() {
        assertEquals("mastodon:https://example.social/@alice/110", MastodonSnapshot.archiveId(post))
        val archived = MastodonSnapshot.archive(post)
        assertEquals("mastodon", J(archived)["xtaPlugin"].string)
        assertEquals(post, MastodonSnapshot.fromArchive(archived))
        assertNull(MastodonSnapshot.fromArchive(Json.parseToJsonElement("""{"xtaPlugin":"bluesky","post":{}}""")))
    }

    @Test
    fun followsAreXtasRows() {
        val rows = """[{"id":"alice@example.social","name":"Alice","avatar_url":"https://a/p.png","in_feed":1,"created_at":"2025-01-01 10:00:00"},
            {"id":"bob@other.social","name":"bob","avatar_url":null,"in_feed":0},{"name":"no id"},{"id":"x@y.z"}]"""
        val follows = MastodonFollowRows.decode(J.parse(rows))
        assertEquals(listOf("alice@example.social", "bob@other.social"), follows.map { it.acct })
        assertEquals(false, follows[1].inFeed)
        assertEquals(follows, MastodonFollowRows.decode(J(MastodonFollowRows.encode(follows))))
        assertEquals(1, J(MastodonFollowRows.encode(follows))[0]["in_feed"].int)
    }

    @Test
    fun aRestoredRowReplacesItsAccountAndTheRestStay() {
        val current = listOf(MastodonFollow("a@x.social", "A"), MastodonFollow("b@x.social", "B"))
        val merged = MastodonFollowRows.upsert(current, listOf(MastodonFollow("A@X.social", "A again"), MastodonFollow("c@x.social", "C")))
        assertEquals(listOf("A again", "B", "C"), merged.map { it.name })
        assertEquals(listOf("alpha", "Beta", "gamma"), MastodonFollowRows.ordered(listOf("gamma", "Beta", "alpha").map { MastodonFollow("$it@x.y", it) }).map { it.name })
    }

    @Test
    fun timelineOptionsAreKeptPerTimelineWithoutTheSearch() {
        val options = mapOf(
            1 to MastodonTimelineFilter(query = "cats", content = MastodonTimelineFilter.Content.MEDIA, hideBoosts = true),
            3 to MastodonTimelineFilter(order = MastodonTimelineFilter.Order.OLDEST, hideReplies = true),
        )
        val read = MastodonTimelineOptions.parse(MastodonTimelineOptions.encode(options))
        assertEquals(MastodonTimelineFilter(content = MastodonTimelineFilter.Content.MEDIA, hideBoosts = true), read[1])
        assertEquals(MastodonTimelineFilter(order = MastodonTimelineFilter.Order.OLDEST, hideReplies = true), read[3])
        assertEquals(MastodonTimelineFilter(), read[0])
        assertEquals("""{"1":{"content":"media","order":"feed","hideBoosts":true,"hideReplies":false},"3":{"content":"all","order":"oldest","hideBoosts":false,"hideReplies":true}}""", MastodonTimelineOptions.encode(options.mapValues { it.value.copy(query = "") }))
    }

    @Test
    fun damagedTimelineOptionsLeaveTheTimelinesWhole() {
        assertEquals(emptyMap(), MastodonTimelineOptions.parse("not json"))
        assertEquals(emptyMap(), MastodonTimelineOptions.parse("x".repeat(10_001)))
        assertEquals(MastodonTimelineFilter(), MastodonTimelineOptions.parse("""{"0":{"content":"pictures","order":7}}""")[0])
    }

    @Test
    fun theReadersInstancesAreTheHomeOneThenTheOthers() {
        assertEquals(listOf("https://home.social", "https://b.social"), MastodonInstances.configured(" https://home.social ", listOf("https://b.social")))
        assertEquals(listOf("https://b.social"), MastodonInstances.configured("", listOf("https://b.social")))
    }

    @Test
    fun readingPlacesComeBackForTheirInstancesOnly() {
        val places = MastodonReadingPlaces(
            tab = 3,
            people = true,
            points = mapOf("home:1" to MastodonReadPoint(listOf(post), post.url, -42.5, "https://example.social", listOf(MastodonTag("cats", uses = 9)))),
        )
        val scope = MastodonReadingCodec.scope(listOf("https://example.social"))
        val text = MastodonReadingCodec.encode(places, scope)
        assertEquals(places, MastodonReadingCodec.parse(text, scope))
        assertEquals(MastodonReadingPlaces(), MastodonReadingCodec.parse(text, "https://other.social"))
        assertEquals(MastodonReadingPlaces(), MastodonReadingCodec.parse("[]", scope))
    }

    @Test
    fun aPlaceWithoutPostsIsNone() {
        val scope = MastodonReadingCodec.scope(emptyList())
        val text = """{"version":1,"scope":"","tab":9,"points":{"home:0":{"posts":[],"anchor":"x"},"client:2":{"posts":[${MastodonSnapshot.encode(post)}],"leading":1e9}}}"""
        val read = MastodonReadingCodec.parse(text, scope)
        assertEquals(3, read.tab)
        assertEquals(setOf("client:2"), read.points.keys)
        assertEquals(10_000.0, read.points.getValue("client:2").leading)
        assertEquals(post.url, read.points.getValue("client:2").anchor)
    }
}
