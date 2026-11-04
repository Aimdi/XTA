package com.aimdi.xtanative.networks.websources

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.websources.WebSourceFixtures.source
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.HttpUrl.Companion.toHttpUrl

class WebSourcesKeptTest {
    private val lobsters = source(WebSourceFixtures.links())
    private val lemmy = source(WebSourceFixtures.posts())

    @Test
    fun installedSourcesAreKeptAsXtaKeepsThemAndReadBack() {
        val installed = WebSourcesInstalled.installed(emptyList(), lobsters, "https://example.org/lobsters.json")
        val stored = WebSourcesInstalled.encode(installed)

        assertEquals(installed, WebSourcesInstalled.decode(stored))
        assertEquals(listOf("source", "origin", "hosts"), J.parse(stored)[0].keys.toList())
        assertEquals("https://example.org/lobsters.json".toHttpUrl(), installed.single().origin)
        assertEquals(lobsters.hosts, installed.single().agreedHosts, "installing agrees to every site it lists")
        assertEquals(emptyList(), WebSourcesInstalled.decode("""[{"source": {"id": "x"}, "hosts": []}, "junk"]"""), "a file that no longer reads is left out")
        assertEquals(emptyList(), WebSourcesInstalled.decode("not json"))
    }

    @Test
    fun installingAgainReplacesRemovingTakesAwayAndAnUpdateIsTakenOnceAgreed() {
        val newer = source(WebSourceFixtures.links(version = 2, hosts = listOf("lobste.rs", "cdn.lobste.rs")))
        val two = WebSourcesInstalled.installed(WebSourcesInstalled.installed(emptyList(), lobsters, null), lemmy, null)

        assertEquals(listOf(lemmy.id, lobsters.id), WebSourcesInstalled.installed(two, lobsters, null).map { it.source.id })
        assertEquals(listOf(lemmy.id), WebSourcesInstalled.removed(two, lobsters.id).map { it.source.id })

        val waiting = WebSourcesInstalled.updated(two, mapOf(lobsters.id to two.first().copy(waiting = newer)))
        val agreed = WebSourcesInstalled.agreed(waiting, lobsters.id).first()
        assertEquals(newer, agreed.source)
        assertEquals(newer.hosts, agreed.agreedHosts)
        assertNull(agreed.waiting)
        assertEquals(waiting, WebSourcesInstalled.decode(WebSourcesInstalled.encode(waiting)), "a waiting update is kept too")
    }

    @Test
    fun theFilesAreReadAgainAtMostOnceIn23Hours() {
        val now = Instant.parse("2026-11-04T12:00:00Z")

        assertTrue(WebSourcesInstalled.dueForUpdate("", now))
        assertTrue(WebSourcesInstalled.dueForUpdate("not a time", now))
        assertFalse(WebSourcesInstalled.dueForUpdate(WebSourcesInstalled.checkedAt(now.minus(Duration.ofHours(22))), now))
        assertTrue(WebSourcesInstalled.dueForUpdate(WebSourcesInstalled.checkedAt(now.minus(Duration.ofHours(23))), now))
        assertFalse(WebSourcesInstalled.dueForUpdate("2026-11-04T01:00:00.000", now, ZoneOffset.UTC), "XTA's time without a zone is the device's")
        assertEquals("2026-11-04T12:00:00.000Z", WebSourcesInstalled.checkedAt(now))
    }

    @Test
    fun aFollowedListIsKeptAsAnXtaSubscriptionRow() {
        val list = lobsters.feeds.first()
        val follows = WebSourceFollows.followed(emptyList(), lobsters, list, Instant.parse("2026-11-04T12:00:00Z"))
        val row = J(follows.single().row)

        assertEquals("${lobsters.id}/${list.id}", row["id"].string)
        assertEquals(lobsters.name, row["name"].string)
        assertEquals(list.name, row["screen_name"].string)
        assertEquals(1L, row["in_feed"].long)
        assertEquals("websources:${lobsters.id}/${list.id}", follows.single().subscriptionId)
        assertTrue(WebSourceFollows.isFollowing(follows, lobsters.id, list.id))
        assertEquals(follows, WebSourceFollows.decode(WebSourceFollows.encode(follows)))
        assertEquals(1, WebSourceFollows.followed(follows, lobsters, list, Instant.now()).size, "a list is followed once")
        assertEquals(emptyList(), WebSourceFollows.unfollowed(follows, follows.single().subscriptionId))
        assertEquals(emptyList(), WebSourceFollows.withoutSource(follows, lobsters.id))
    }

    @Test
    fun aRowThatNamesNoListIsLeftOutAndAnXtaRowKeepsWhatItCarries() {
        val stored = """[{"id":"lobsters/hottest","name":"Lobsters","screen_name":"Hottest","avatar_url":null,"in_feed":1,"created_at":"2026-01-01T00:00:00.000","extra":true},{"id":"nolist"},{"id":"/x"},7]"""

        val follows = WebSourceFollows.decode(stored)

        assertEquals(listOf("lobsters/hottest"), follows.map { it.key })
        assertEquals(true, J(follows.single().row)["extra"].boolean)
        assertEquals("lobsters" to "hottest", WebSourceFollows.parts("lobsters/hottest"))
        assertNull(WebSourceFollows.parts("lobsters/"))
    }

    @Test
    fun aPostReadsInTheRssReaderAndItsStandInUntilItsCopyIsRead() {
        val item = WebSourceItem(sourceId = lobsters.id, id = "abc", text = "Some words", link = "https://lobste.rs/s/abc", author = "ann")
        val reading = WebSourceReading.itemOf(lobsters, item, "<p>Full</p>")

        assertEquals("websources:${lobsters.id}", reading.feedId)
        assertEquals(item.key, reading.id)
        assertEquals("Some words", reading.title, "a post without a title reads under its text")
        assertEquals(lobsters.name, reading.feedTitle)
        assertEquals(reading.copy(excerpt = null, bodyHtml = null, author = null), WebSourceReading.stub(lobsters, lobsters.id, item.key, "Some words", item.link))
        assertEquals(lobsters.id, WebSourceReading.stub(null, lobsters.id, item.key, "Some words", null).feedTitle, "a source removed since goes by its id")
    }

    @Test
    fun aTappedPostOpensItsTextItsSourcesArticleOrItsAddress() {
        val withText = WebSourceItem(sourceId = lemmy.id, id = "1", html = "<p>Full</p>", link = "https://lemmy.example/post/1")
        val bare = WebSourceItem(sourceId = lemmy.id, id = "2", link = "https://lemmy.example/post/2")
        val talk = WebSourceItem(sourceId = lobsters.id, id = "3", discussion = "https://lobste.rs/s/3")

        assertEquals(WebSourceOpening.Reader("<p>Full</p>"), WebSourceReading.opening(lemmy, withText), "the text it carries, as read")
        assertEquals(WebSourceOpening.Article, WebSourceReading.opening(lemmy, bare), "a source with an article rule reads the article first")
        assertEquals(WebSourceOpening.Address("https://lobste.rs/s/abc"), WebSourceReading.opening(lobsters, WebSourceItem(sourceId = lobsters.id, id = "4", link = "https://lobste.rs/s/abc", discussion = "https://lobste.rs/s/4")))
        assertEquals(WebSourceOpening.Address("https://lobste.rs/s/3"), WebSourceReading.opening(lobsters, talk), "without a link, its discussion")
        assertEquals(WebSourceOpening.Address(null), WebSourceReading.opening(lobsters, WebSourceItem(sourceId = lobsters.id, id = "5")))
    }

    @Test
    fun aFailureSaysWhatWentWrongAndAGalleryOpensItsPictures() {
        assertEquals(WebSourceFailureKind.BLOCKED to null, WebSourceReading.failure(WebSourceLimitException(WebSourceLimitException.Kind.BLOCKED, "x")))
        assertEquals(WebSourceFailureKind.TOO_LARGE to null, WebSourceReading.failure(WebSourceLimitException(WebSourceLimitException.Kind.TOO_LARGE, "x")))
        assertEquals(WebSourceFailureKind.STATUS to 503, WebSourceReading.failure(NetworkRequestException("websources", 503, "busy")))
        assertEquals(WebSourceFailureKind.UNREACHABLE to null, WebSourceReading.failure(IOException("down")))

        val bare = WebSourceItem(sourceId = "nasa", id = "1", title = "No picture")
        val one = WebSourceItem(sourceId = "nasa", id = "2", title = "One", image = "https://images.example/1.jpg")
        val two = WebSourceItem(sourceId = "nasa", id = "3", title = "Two", images = listOf("https://images.example/2.jpg"))
        assertEquals(listOf(one, two) to 1, WebSourceReading.pictured(listOf(bare, one, two), two))
        assertNull(WebSourceReading.pictured(listOf(bare, one), bare))
        assertEquals(2, WebSourceReading.columns(360f, 1f))
        assertEquals(1, WebSourceReading.columns(360f, 2f), "large text takes wider pictures")
        assertEquals(4, WebSourceReading.columns(2000f, 1f))
    }
}
