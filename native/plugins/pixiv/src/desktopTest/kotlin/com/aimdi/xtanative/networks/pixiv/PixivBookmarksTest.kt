package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PixivBookmarksTest {
    @Test
    fun aWorksBookmarkSaysItsVisibilityAndWhichTagsItIsFiledUnder() = runBlocking {
        val http = FakePixivHttp {
            answer(
                """{"bookmark_detail": {"is_bookmarked": true, "restrict": "private", "tags": [
                   {"name": "オリジナル", "is_registered": true}, {"name": "風景", "is_registered": false}, {"name": "  ", "is_registered": true}]}}""",
            )
        }
        val detail = PixivBookmarkApi(pixiv(http)).detail(5)
        assertEquals("/v2/illust/bookmark/detail" to mapOf("illust_id" to "5"), http.asked.single().url.encodedPath to http.asked.single().query)
        assertEquals(true to "private", detail.isBookmarked to detail.restrict)
        assertEquals(listOf(PixivBookmarkTagChoice("オリジナル", true), PixivBookmarkTagChoice("風景", false)), detail.tags)
    }

    @Test
    fun aMissingOrReshapedDetailReadsAsAPublicWorkNotBookmarked() {
        for (json in listOf("null", "{}", """{"bookmark_detail": "gone"}""", """{"bookmark_detail": {"is_bookmarked": "yes", "restrict": "secret", "tags": "none"}}""")) {
            val detail = PixivBookmarkDetail.parse(J.parse(json))
            assertEquals(false to "public", detail.isBookmarked to detail.restrict, json)
            assertTrue(detail.tags.isEmpty(), json)
        }
    }

    @Test
    fun theFirstTagPageAsksForTheReadersTagsOfOneVisibilityAndLaterPagesFollowNextUrl() = runBlocking {
        val next = "https://app-api.pixiv.net/v1/user/bookmark-tags/illust?user_id=77&restrict=private&offset=30"
        val http = FakePixivHttp { request ->
            if ("offset" in request.query) {
                answer("""{"bookmark_tags": [{"name": "Later", "count": 1}], "next_url": null}""")
            } else {
                answer("""{"bookmark_tags": [{"name": "Favs", "count": 12}, {"name": "NoCount"}, {"name": ""}, "junk"], "next_url": "$next"}""")
            }
        }
        val api = PixivBookmarkApi(pixiv(http))
        val first = api.tags("private")
        val second = api.tags("private", first.next)

        assertEquals("/v1/user/bookmark-tags/illust" to mapOf("user_id" to "77", "restrict" to "private"), http.asked.first().url.encodedPath to http.asked.first().query)
        assertEquals(listOf(PixivBookmarkTag("Favs", 12), PixivBookmarkTag("NoCount", 0)), first.items)
        assertEquals(next, first.next)
        assertEquals(listOf(PixivBookmarkTag("Later", 1)), second.items)
        assertNull(second.next)
    }

    @Test
    fun allTagsFollowNextUrlPageAfterPageUpToTheLimit() = runBlocking {
        val http = FakePixivHttp { request ->
            val offset = request.query["offset"]?.toInt() ?: 0
            answer(
                """{"bookmark_tags": [{"name": "Tag$offset", "count": 1}],
                   "next_url": "https://app-api.pixiv.net/v1/user/bookmark-tags/illust?user_id=77&restrict=public&offset=${offset + 1}"}""",
            )
        }
        val tags = PixivBookmarkApi(pixiv(http)).allTags("public", maxPages = 3)
        assertEquals(listOf("Tag0", "Tag1", "Tag2"), tags.map { it.name })
        assertEquals(3, http.asked.size)
    }

    @Test
    fun aReshapedTagListIsAnEmptyLastPage() = runBlocking {
        val page = PixivBookmarkApi(pixiv(FakePixivHttp { answer("""{"bookmark_tags": "none", "next_url": 3}""") })).tags("public")
        assertTrue(page.items.isEmpty())
        assertNull(page.next)
    }

    @Test
    fun theReadersFavoritesNarrowToATagUnclassifiedForUntaggedOnes() = runBlocking {
        val http = FakePixivHttp { answer(illustsJson(workJson(1), workJson(2, r18 = true, ai = true))) }
        val client = pixiv(http, filters = PixivFilters(hideAi = true))
        val tagged = client.bookmarks(userId = 77, restrict = "private", tag = PIXIV_UNCLASSIFIED_TAG)
        client.bookmarks(userId = 77, restrict = "public")

        assertEquals("未分類" to "private", http.asked.first().query["tag"] to http.asked.first().query["restrict"])
        assertTrue("tag" !in http.asked.last().query)
        assertEquals(listOf(1L, 2L), tagged.items.map { it.id }, "saved on purpose, R-18 and AI works stay")
    }
}
