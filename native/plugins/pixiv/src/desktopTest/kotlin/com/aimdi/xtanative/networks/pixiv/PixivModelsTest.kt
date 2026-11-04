package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PixivModelsTest {
    private val page = Fixtures.json("illusts.json")

    private fun work(extra: String = "", user: String = "") = PixivIllust.parse(
        J.parse(
            """{"id": 3, "title": "Work 3", "type": "illust", "image_urls": {"medium": "https://i.pximg.net/c/540x540_70/img-master/3.jpg"},
               "user": {"id": 7, "name": "Artist", "account": "artist"$user}, "x_restrict": 0, "sanity_level": 2$extra}""",
        ),
    )!!

    @Test
    fun aListPageReadsEachWorkAndLeavesOutR18AndStubsByDefault() {
        val works = PixivIllust.list(page)
        assertEquals(listOf(42L, 1L, 3L), works.map { it.id })

        val cat = works.first()
        assertEquals("Cat" to "meow", cat.title to cat.caption)
        assertEquals("https://i.pximg.net/c/540x540_70/img-master/cat.jpg", cat.thumbnailUrl, "the aspect-preserving medium")
        assertEquals("https://www.pixiv.net/artworks/42", cat.url)
        assertEquals(Triple(7L, "Artist", "artist"), Triple(cat.userId, cat.userName, cat.userAccount))
        assertEquals("https://i.pximg.net/user.jpg", cat.userAvatarUrl)
        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), cat.createdAt)
        assertEquals(listOf(10L, 100L), listOf(cat.totalBookmarks, cat.totalViews))
        assertTrue(cat.isBookmarked)
        assertEquals(listOf("cat", "オリジナル"), cat.tags.map { it.displayName })
        assertEquals(1600.0 / 900, cat.aspectRatio, 0.0001)
        assertEquals(listOf("https://i.pximg.net/c/600x1200_90/img-master/cat.jpg"), cat.viewerUrls)
        assertEquals("https://i.pximg.net/img-original/cat.png", cat.downloadUrlAt(0))
    }

    @Test
    fun aMangaShowsItsLargePagesAndSavesItsOriginals() {
        val manga = PixivIllust.list(page).single { it.id == 1L }
        assertEquals("hi there", manga.caption)
        assertEquals(listOf("https://i.pximg.net/l0.jpg", "https://i.pximg.net/p1.jpg"), manga.pageUrls, "large over original, so the viewer stays light")
        assertEquals(listOf("https://i.pximg.net/p0.jpg", "https://i.pximg.net/p1.jpg"), manga.originalUrls)
        assertEquals(listOf("https://i.pximg.net/m0.jpg", "https://i.pximg.net/p1.jpg"), manga.pageThumbUrls)
        assertEquals("https://i.pximg.net/p1.jpg", manga.downloadUrlAt(5), "a page past the end is the last")
        assertEquals(800.0 / 1200, manga.aspectRatio, 0.0001)
        assertTrue(manga.isManga)
    }

    @Test
    fun theReadersChoicesDecideR18AndAiWorks() {
        assertEquals(listOf(42L, 1L, 99L, 3L), PixivIllust.list(page, includeR18 = true).map { it.id })
        assertEquals(listOf(42L, 1L), PixivIllust.list(page, includeAi = false).map { it.id })
        val ai = PixivIllust.list(page).single { it.id == 3L }
        assertTrue(ai.isAi && ai.isUgoira)
    }

    @Test
    fun aReshapedPageYieldsNothingRatherThanThrowing() {
        assertTrue(PixivIllust.list(J(null)).isEmpty())
        assertTrue(PixivIllust.list(J.parse("""{"illusts": "nope"}""")).isEmpty())
    }

    @Test
    fun aWorksDetailCarriesItsSeriesCommentsFollowAndCaptionMarkup() {
        val illust = PixivIllust.parse(Fixtures.json("illust_detail.json")["illust"])!!
        assertEquals("Summer Sky", illust.title)
        assertEquals(PixivSeriesRef(55, "Seasons"), illust.series)
        assertEquals(12L, illust.totalComments)
        assertTrue(illust.userIsFollowed)
        assertEquals(4, illust.sanityLevel)
        assertTrue("<a href=\"https://www.pixiv.net/users/7\">me</a>" in illust.captionHtml)
        assertEquals("Line onesee me", illust.caption)
        assertEquals(listOf(PixivTag("夏", "summer")), illust.tags, "a nameless tag is skipped")
        assertEquals("https://i.pximg.net/img-original/img/2026/07/01/00/00/00/120_p0.png", illust.downloadUrlAt(0))
    }

    @Test
    fun aWorkWithoutThemFallsBackToQuietDefaults() {
        val illust = work()
        assertNull(illust.series)
        assertEquals(0L, illust.totalComments)
        assertEquals(false, illust.userIsFollowed)
        assertEquals("", illust.captionHtml)
        assertEquals(2, illust.sanityLevel)
        assertEquals(listOf(illust.thumbnailUrl), illust.viewerUrls)
    }

    @Test
    fun aReshapedWorkNeitherThrowsNorInventsValues() {
        val illust = work(extra = """, "series": "Summer", "total_comments": "many", "sanity_level": null, "caption": 7""", user = """, "is_followed": "yes"""")
        assertEquals(listOf<Any?>(null, 0L, 0, false, ""), listOf(illust.series, illust.totalComments, illust.sanityLevel, illust.userIsFollowed, illust.captionHtml))
        assertNull(work(extra = """, "series": {"id": 0, "title": "none"}""").series)
    }

    @Test
    fun aTagShowsItsTranslationOnlyWhenItDiffers() {
        assertEquals("cat", PixivTag("猫", " cat ").displayName)
        assertEquals("猫", PixivTag("猫", "猫").displayName)
        assertNull(PixivTag("猫", "  ").translation)
    }

    @Test
    fun aDetailsUserCarriesTheProfileCounts() {
        val user = PixivUser.detail(Fixtures.json("user_detail.json"))
        assertEquals(Triple(9L, "Mika", "Draws cats."), Triple(user.id, user.name, user.comment))
        assertEquals(listOf(43L, 1500L, 2L), listOf(user.worksCount, user.followingCount, user.mypixivCount))
        assertTrue(user.isFollowed)

        val bare = PixivUser.detail(J.parse("""{"user": {"id": 11, "name": "Name", "account": "acct"}}"""))
        assertEquals(listOf(0L, 0L, 0L), listOf(bare.worksCount, bare.followingCount, bare.mypixivCount))
        val quoted = PixivUser.detail(J.parse("""{"user": {"id": 11}, "profile": {"total_illusts": "4", "total_manga": null, "total_follow_users": "many"}}"""))
        assertEquals(4L to 0L, quoted.worksCount to quoted.followingCount)
    }

    @Test
    fun aCreatorPreviewKeepsFollowMuteAndTheirWorksAndNovels() {
        val previews = PixivUserPreview.list(Fixtures.json("user_previews.json"), includeR18 = true)
        assertEquals(listOf(1L, 2L), previews.map { it.user.id }, "creators without an id are dropped")

        val first = previews.first()
        assertTrue(first.user.isFollowed && first.isMuted)
        assertEquals(listOf(10L, 11L, 12L), first.illusts.map { it.id })
        assertEquals(listOf("A novel", "An R-18 novel"), first.novels.map { it.title })
        val second = previews.last()
        assertEquals(Triple(true, true, false), Triple(second.illusts.isEmpty(), second.novels.isEmpty(), second.isMuted))
    }

    @Test
    fun aCreatorPreviewLeavesOutR18AndAiAsTheFeedsDo() {
        val json = Fixtures.json("user_previews.json")
        val hidden = PixivUserPreview.list(json, includeR18 = false, includeAi = false).first()
        assertEquals(listOf(10L), hidden.illusts.map { it.id })
        assertEquals(listOf(900L), hidden.novels.map { it.id })
        assertEquals(listOf(10L, 12L), PixivUserPreview.list(json).first().illusts.map { it.id })
        assertTrue(PixivUserPreview.list(J.parse("""{"user_previews": "nope"}""")).isEmpty())
    }
}
