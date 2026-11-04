package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivTag
import com.aimdi.xtanative.networks.pixiv.PixivUser
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A work as Pixiv's lists send one, with what the rules look at. */
internal fun work(id: Long, userId: Long = 2, tags: List<String> = emptyList(), type: String = "illust", pages: Int = 1) = PixivIllust(
    id = id,
    title = "Work $id",
    caption = "",
    type = type,
    thumbnailUrl = "https://i.pximg.net/c/250x250/img-master/img/$id.jpg",
    pageCount = pages,
    userId = userId,
    userName = "Artist $userId",
    userAccount = "artist$userId",
    tags = tags.map { PixivTag(it) },
)

internal fun novel(id: Long, userId: Long = 2, tags: List<String> = emptyList()) =
    PixivNovel(id, "Novel $id", PixivUser(userId, "Writer $userId", "writer$userId"), tags = tags.map { PixivTag(it) })

class PixivMutesTest {
    @Test
    fun aTagIsKeptWithoutItsHashAndCaseAPatternAsWritten() {
        assertEquals("cat", PixivMuteTags.normalized("  #Cat "))
        assertEquals("r'\\D+'", PixivMuteTags.normalized("r'\\D+'"))
        assertEquals("\\D+", PixivMuteTags.pattern("r'\\D+'"))
        assertNull(PixivMuteTags.pattern("cat"))
        assertEquals("#cat", PixivMuteTags.label("cat"))
    }

    @Test
    fun onlyNamesAndPatternsThatCompileCanBeMuted() {
        assertTrue(PixivMuteTags.valid("cat"))
        assertFalse(PixivMuteTags.valid("  # "))
        assertFalse(PixivMuteTags.valid("r'(unclosed'"))
        assertTrue(PixivMuteTags.valid("r'^#a.*#b'"))
    }

    @Test
    fun aNameMatchesWhateverItsCaseAPatternTheTagsTogether() {
        val matcher = PixivTagMatcher(listOf("cat", "r'#dog#bird'"))
        assertEquals("cat", matcher.match(listOf(PixivTag("CAT"))))
        assertEquals("r'#dog#bird'", matcher.match(listOf(PixivTag("dog"), PixivTag("bird"))))
        assertNull(matcher.match(listOf(PixivTag("bird"), PixivTag("dog"))))
        assertNull(matcher.match(emptyList()))
    }

    @Test
    fun aMutedWorkCreatorOrTagHidesTheWorkAndSaysWhy() {
        val mutes = PixivMutes(authorIds = setOf(5), authorNames = mapOf(5L to "Five"), tags = setOf("cat"), illustIds = setOf(9))
        val works = listOf(work(1), work(2, userId = 5), work(3, tags = listOf("Cat")), work(9))
        assertEquals(listOf(1L), mutes.filter(works).map { it.id })
        assertEquals(PixivMuteReason.Author("Five"), mutes.reasonFor(works[1]))
        assertEquals(PixivMuteReason.Tag("cat"), mutes.reasonFor(works[2]))
        assertEquals(PixivMuteReason.Work, mutes.reasonFor(works[3]))
        assertNull(mutes.reasonFor(works[0]))
    }

    @Test
    fun aCreatorsOwnScreenShowsThemEvenWhenMuted() {
        val mutes = PixivMutes(authorIds = setOf(5))
        assertEquals(listOf(7L), mutes.showing(5).filter(listOf(work(7, userId = 5))).map { it.id })
    }

    @Test
    fun novelsAreHiddenByTheirCreatorTheirIdOrTheirTags() {
        val mutes = PixivMutes(authorIds = setOf(5), tags = setOf("cat"), novelIds = setOf(4))
        val novels = listOf(novel(1), novel(2, userId = 5), novel(3, tags = listOf("cat")), novel(4))
        assertEquals(listOf(1L), mutes.filterNovels(novels).map { it.id })
        assertEquals(PixivMuteReason.Work, mutes.reasonFor(novels[3]))
    }

    @Test
    fun theMutesComeBackFromXtasPreferences() {
        val mutes = PixivMutes(authorIds = setOf(5, 6), authorNames = mapOf(5L to "Five"), tags = setOf("cat", "r'x'"), illustIds = setOf(1), commentIds = setOf(2), novelIds = setOf(3))
        assertEquals(mutes, PixivMutes.of(mutes.writtenTo(Settings())))
    }

    @Test
    fun xtasOlderListOfBareCreatorIdsStillReads() {
        val settings = Settings().with(PixivPrefs.mutedAuthors, "[12, 13]")
        assertEquals(setOf(12L, 13L), PixivMutes.of(settings).authorIds)
    }
}
