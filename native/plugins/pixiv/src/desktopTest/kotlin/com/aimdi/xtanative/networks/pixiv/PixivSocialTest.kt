package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.NetworkRequestException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class PixivSocialTest {
    private val detail = Fixtures.text("user_detail.json")

    /** The fixture's profile with [profile]'s fields put over its own, and [publicity] in place of its own. */
    private fun profile(profile: String = "{}", publicity: String? = null): PixivUserProfile {
        val root = J.parse(detail).raw as JsonObject
        val fields = root.getValue("profile") as JsonObject + (J.parse(profile).raw as JsonObject)
        val reshaped = root + ("profile" to JsonObject(fields)) + listOfNotNull(publicity?.let { "profile_publicity" to J.parse(it).raw!! })
        return PixivUserProfile.parse(J(JsonObject(reshaped)))
    }

    private fun api(ownId: Long = 7, filters: PixivFilters = PixivFilters(), body: (Asked) -> String): Pair<PixivSocialApi, FakePixivHttp> {
        val http = FakePixivHttp { answer(body(it)) }
        return PixivSocialApi(pixiv(http, signedIn(userId = ownId), filters)) to http
    }

    @Test
    fun aProfileReadsTheUserTheCountsTheLinksAndTheDetails() {
        val read = PixivUserProfile.parse(J.parse(detail))
        assertEquals(listOf<Any?>("Mika", true, 1500L, 2L, 43L), listOf(read.user.name, read.user.isFollowed, read.user.followingCount, read.user.mypixivCount, read.user.worksCount))
        assertEquals("https://i.pximg.net/background/9.jpg", read.backgroundUrl)
        assertEquals(listOf(40L, 3L, 1L, 812L), listOf(read.totalIllusts, read.totalManga, read.totalNovels, read.publicBookmarks))
        assertEquals("https://example.com/mika" to "https://twitter.com/mika_draws", read.webpage to read.twitterLink)
        assertTrue(read.pawooUrl.startsWith("https://pawoo.net/"))
        assertEquals(listOf<Any?>("female", "Tokyo, Japan", "Illustrator", "07-14", 1990), listOf(read.gender, read.region, read.job, read.birthDay, read.birthYear))
        assertTrue(read.isPremium)
        assertEquals("https://www.pixiv.net/users/9", read.url)
    }

    @Test
    fun whatTheCreatorKeepsPrivateIsLeftEmpty() {
        val read = profile(publicity = """{"gender": "private", "region": "private", "birth_day": "public", "birth_year": "private", "job": "mypixiv", "pawoo": false}""")
        assertEquals(listOf<Any?>("", "", "07-14", null, "Illustrator", ""), listOf(read.gender, read.region, read.birthDay, read.birthYear, read.job, read.pawooUrl))
    }

    @Test
    fun aMissingProfileReadsAsEmpty() {
        val bare = PixivUserProfile.parse(J.parse("""{"user": {"id": 9, "name": "Mika", "account": "mika"}}"""))
        assertEquals(9L, bare.id)
        assertNull(bare.backgroundUrl)
        assertEquals(listOf(0L, 0L, 0L), listOf(bare.totalIllusts, bare.totalManga, bare.publicBookmarks))
        assertEquals("" to "", bare.webpage to bare.twitterLink)
        assertNull(bare.birthYear)
        assertEquals(PixivWorkType.ILLUST, bare.defaultWorkType)
        assertEquals(0L, PixivUserProfile.parse(J(null)).id)
        assertEquals(0L, PixivUserProfile.parse(J.parse("\"reshaped\"")).id)
    }

    @Test
    fun aReshapedProfileKeepsWhatStillParsesAndDropsWhatIsNoLink() {
        val read = profile(
            profile = """{"total_illusts": "7", "total_manga": "12", "background_image_url": "", "webpage": "javascript:alert(1)", "twitter_url": null, "birth_year": 0, "gender": 42}""",
            publicity = "{}",
        )
        assertEquals(7L to 12L, read.totalIllusts to read.totalManga)
        assertEquals(PixivWorkType.MANGA, read.defaultWorkType)
        assertTrue(read.hasBothWorkTypes)
        assertNull(read.backgroundUrl)
        assertEquals("", read.webpage)
        assertEquals("https://x.com/mika_draws", read.twitterLink, "built from the account when no address came")
        assertNull(read.birthYear)
        assertEquals("", read.gender)
    }

    @Test
    fun aProfileIsAskedByUserIdAndOneWithoutAUserRefused() = runBlocking {
        val (api, http) = api { detail }
        val read = api.userProfile(9)
        assertEquals("/v1/user/detail" to mapOf("user_id" to "9", "filter" to "for_android"), http.asked.single().url.encodedPath to http.asked.single().query)
        assertEquals("Mika", read.user.name)

        val error = assertFailsWith<NetworkRequestException> { api { """{"user": null}""" }.first.userProfile(9) }
        assertEquals(PixivFailure.BAD_RESPONSE, PixivFailure.of(error))
    }

    @Test
    fun theFollowDetailSaysWhetherAndHowTheReaderFollows() = runBlocking {
        val (api, http) = api { """{"follow_detail": {"is_followed": true, "restrict": "private"}}""" }
        val read = api.followDetail(9)
        assertEquals("/v1/user/follow/detail" to mapOf("user_id" to "9"), http.asked.single().url.encodedPath to http.asked.single().query)
        assertTrue(read.isFollowed && read.isPrivate)
        val reshaped = PixivFollowDetail.parse(J.parse("""{"follow_detail": "nope"}"""))
        assertEquals(false to "", reshaped.isFollowed to reshaped.restrict)
    }

    @Test
    fun aCreatorsWorksComeByKindAndLeaveOutR18ForSomeoneElse() = runBlocking {
        val (api, http) = api {
            illustsJson(workJson(1), workJson(2, r18 = true), next = "https://app-api.pixiv.net/v1/user/illusts?user_id=9&type=manga&offset=30")
        }
        val page = api.userWorks(9, PixivWorkType.MANGA)
        api.userWorks(9, PixivWorkType.MANGA, page.next)
        assertEquals("/v1/user/illusts" to mapOf("user_id" to "9", "type" to "manga", "filter" to "for_android"), http.asked.first().url.encodedPath to http.asked.first().query)
        assertEquals(listOf(1L), page.items.map { it.id })
        assertEquals("30", http.asked.last().query["offset"])
    }

    @Test
    fun theReadersOwnProfileKeepsEveryWorkTheyPosted() = runBlocking {
        val (api, _) = api(ownId = 9) { illustsJson(workJson(1), workJson(2, r18 = true)) }
        assertEquals(listOf(1L, 2L), api.userWorks(9, PixivWorkType.ILLUST).items.map { it.id })
    }

    @Test
    fun aCreatorsBookmarksSendTheUserTheVisibilityAndTheTag() = runBlocking {
        val (api, http) = api { illustsJson(workJson(1), workJson(2, r18 = true)) }
        val page = api.userBookmarks(9, tag = PIXIV_UNCLASSIFIED_TAG)
        api.userBookmarks(9)
        assertEquals("/v1/user/bookmarks/illust", http.asked.first().url.encodedPath)
        assertEquals(mapOf("user_id" to "9", "restrict" to "public", "tag" to "未分類", "filter" to "for_android"), http.asked.first().query)
        assertEquals(listOf(1L), page.items.map { it.id })
        assertFalse("tag" in http.asked.last().query)
    }

    @Test
    fun followListsSendTheirVisibilityAndReadPreviewCards() = runBlocking {
        val (api, http) = api(filters = PixivFilters(showR18 = true)) { Fixtures.text("user_previews.json") }
        val following = api.userFollowing(7, restrict = "private")
        api.userList(PixivUserListKind.FOLLOWERS, 9, restrict = "private")

        assertEquals("/v1/user/following" to mapOf("user_id" to "7", "restrict" to "private", "filter" to "for_android"), http.asked.first().url.encodedPath to http.asked.first().query)
        assertEquals("/v1/user/follower" to mapOf("user_id" to "9", "restrict" to "public", "filter" to "for_android"), http.asked.last().url.encodedPath to http.asked.last().query)
        assertEquals(listOf(1L, 2L), following.items.map { it.user.id })
        assertEquals(listOf(10L, 11L, 12L), following.items.first().illusts.map { it.id })
        assertTrue(following.next.orEmpty().endsWith("offset=30"))
    }

    @Test
    fun aListWithoutPreviewsIsAnEmptyLastPage() = runBlocking {
        val (api, _) = api { """{"user_previews": "gone"}""" }
        val page = api.userList(PixivUserListKind.FOLLOWING, 9)
        assertTrue(page.items.isEmpty())
        assertNull(page.next)
    }

    @Test
    fun theReadersAiDisplaySettingIsRead() = runBlocking {
        val (api, http) = api { """{"show_ai": false}""" }
        assertFalse(api.showsAiWorks())
        assertEquals("/v1/user/ai-show-settings", http.asked.single().url.encodedPath)
        val error = assertFailsWith<NetworkRequestException> { api { "{}" }.first.showsAiWorks() }
        assertEquals(PixivFailure.BAD_RESPONSE, PixivFailure.of(error))
    }

    @Test
    fun theReadersOwnIdComesFromTheStoredAccount() = runBlocking {
        val (api, http) = api(ownId = 77) { "{}" }
        assertEquals(77L, api.ownUserId())
        assertTrue(http.asked.isEmpty())
    }
}
