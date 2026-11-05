package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.networks.NetworkRequestException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** XTA's eight writes: each a signed-in form POST to the path, with the fields, XTA sends. */
class PixivWritesTest {
    private val oauthRefusal = """{"error": {"message": "Error occurred at the OAuth process. Please check your Access Token to fix this."}}"""

    private fun write(send: suspend (PixivClient) -> Unit): Asked {
        val http = FakePixivHttp { answer("{}") }
        runBlocking { send(pixiv(http)) }
        return http.asked.single()
    }

    private fun assertForm(asked: Asked, path: String, fields: Map<String, String>) {
        assertEquals("POST", asked.method)
        assertEquals("https://app-api.pixiv.net$path", asked.url.toString())
        assertEquals(fields, asked.form)
        assertEquals("application/x-www-form-urlencoded", asked.contentType)
        assertEquals("Bearer access-1", asked.headers["Authorization"])
        assertEquals("android", asked.headers["App-OS"])
        assertEquals(pixivClientTime(NOW), asked.headers["X-Client-Time"])
        assertEquals(pixivClientHash(pixivClientTime(NOW)), asked.headers["X-Client-Hash"])
    }

    @Test
    fun aWorkIsBookmarkedWithItsTagsSpaceJoinedInOneField() {
        val asked = write { PixivBookmarkApi(it).add(5, "private", listOf("blue sky", "猫", "blue")) }
        assertForm(asked, "/v2/illust/bookmark/add", mapOf("illust_id" to "5", "restrict" to "private", "tags[]" to "blue sky 猫"))
    }

    @Test
    fun aBookmarkWithoutTagsSendsNoTagField() {
        val asked = write { PixivBookmarkApi(it).add(5, "public") }
        assertForm(asked, "/v2/illust/bookmark/add", mapOf("illust_id" to "5", "restrict" to "public"))
    }

    @Test
    fun aWorksBookmarkIsDeleted() {
        assertForm(write { PixivBookmarkApi(it).delete(5) }, "/v1/illust/bookmark/delete", mapOf("illust_id" to "5"))
    }

    @Test
    fun aNovelsBookmarkIsAddedAndDeleted() {
        assertForm(write { PixivNovelApi(it).addBookmark(8, "public") }, "/v2/novel/bookmark/add", mapOf("novel_id" to "8", "restrict" to "public"))
        assertForm(write { PixivNovelApi(it).deleteBookmark(8) }, "/v1/novel/bookmark/delete", mapOf("novel_id" to "8"))
    }

    @Test
    fun aCreatorIsFollowedAndUnfollowed() {
        assertForm(write { PixivSocialApi(it).follow(9) }, "/v1/user/follow/add", mapOf("user_id" to "9", "restrict" to "public"))
        assertForm(write { PixivSocialApi(it).follow(9, "private") }, "/v1/user/follow/add", mapOf("user_id" to "9", "restrict" to "private"))
        assertForm(write { PixivSocialApi(it).unfollow(9) }, "/v1/user/follow/delete", mapOf("user_id" to "9"))
    }

    @Test
    fun mangaAndNovelSeriesJoinAndLeaveTheWatchlist() {
        assertForm(write { PixivDiscoveryApi(it).addToWatchlist(3) }, "/v1/watchlist/manga/add", mapOf("series_id" to "3"))
        assertForm(write { PixivDiscoveryApi(it).removeFromWatchlist(3) }, "/v1/watchlist/manga/delete", mapOf("series_id" to "3"))
        assertForm(write { PixivNovelApi(it).addToWatchlist(4) }, "/v1/watchlist/novel/add", mapOf("series_id" to "4"))
        assertForm(write { PixivNovelApi(it).removeFromWatchlist(4) }, "/v1/watchlist/novel/delete", mapOf("series_id" to "4"))
    }

    @Test
    fun aWriteRefusedForItsTokenIsSentAgainWithAFreshOne() = runBlocking {
        val http = FakePixivHttp { request ->
            when {
                request.isTokenCall -> tokenAnswer("access-2")
                request.headers["Authorization"] == "Bearer access-1" -> answer(oauthRefusal, 400)
                else -> answer("")
            }
        }
        PixivSocialApi(pixiv(http)).follow(9)

        assertEquals(listOf("Bearer access-1", "Bearer access-2"), http.reads.map { it.headers["Authorization"] })
        assertTrue(http.reads.all { it.method == "POST" && it.form == mapOf("user_id" to "9", "restrict" to "public") })
    }

    @Test
    fun aWriteWithoutAnAccountSendsNothing() = runBlocking {
        val http = FakePixivHttp { answer("{}") }
        assertFailsWith<PixivNoAccountException> { PixivBookmarkApi(pixiv(http, MemoryPixivCredentials())).delete(5) }
        assertTrue(http.asked.isEmpty())
    }

    @Test
    fun aRefusedOrUnreadableWriteFails() = runBlocking {
        val refused = assertFailsWith<NetworkRequestException> { PixivBookmarkApi(pixiv(FakePixivHttp { answer("{}", 404) })).delete(5) }
        assertEquals(404, refused.code)
        assertFailsWith<NetworkRequestException> { PixivBookmarkApi(pixiv(FakePixivHttp { answer("<html>maintenance</html>") })).delete(5) }
        Unit
    }
}
