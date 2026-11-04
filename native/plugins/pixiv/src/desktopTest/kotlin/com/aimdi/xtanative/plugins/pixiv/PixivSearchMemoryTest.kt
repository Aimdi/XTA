package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivBookmarkRange
import com.aimdi.xtanative.networks.pixiv.PixivDatePreset
import com.aimdi.xtanative.networks.pixiv.PixivDateRange
import com.aimdi.xtanative.networks.pixiv.PixivSearchFilter
import com.aimdi.xtanative.networks.pixiv.PixivSearchKind
import com.aimdi.xtanative.networks.pixiv.PixivSearchSort
import com.aimdi.xtanative.networks.pixiv.PixivSearchTarget
import com.aimdi.xtanative.networks.pixiv.PixivTag
import com.aimdi.xtanative.networks.pixiv.PixivUgoiraFilter
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Settings
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PixivSearchMemoryTest {
    @Test
    fun aKeptFilterComesBackAsItWasWritten() {
        val filter = PixivSearchFilter(
            target = PixivSearchTarget.TITLE_CAPTION,
            sort = PixivSearchSort.POPULAR_MALE,
            usersIri = 1000,
            bookmarks = PixivBookmarkRange(500, 999),
            hideAi = true,
            ugoira = PixivUgoiraFilter.ONLY,
        ).withDates(PixivDatePreset.CUSTOM, PixivDateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1)))
        assertEquals(filter, PixivSearchMemory.parse(PixivSearchMemory.json(filter)))
        assertTrue(PixivSearchMemory.json(PixivSearchFilter().withDates(PixivDatePreset.HALF_YEAR)).contains("\"date\":\"halfYear\""))
    }

    @Test
    fun whatAKeptFilterLacksKeepsTheFallbacks() {
        val parsed = PixivSearchMemory.parse("""{"sort":"date_asc"}""", PixivSearchFilter(hideAi = true))
        assertEquals(PixivSearchSort.OLDEST, parsed?.sort)
        assertEquals(true, parsed?.hideAi)
        assertNull(PixivSearchMemory.parse("not json"))
    }

    @Test
    fun aSearchStartsFromTheKeptFilterAsTheAccountMaySendIt() {
        val kept = PixivSearchMemory.keeping(Settings(), PixivSearchKind.WORKS, PixivSearchFilter(sort = PixivSearchSort.POPULAR_FEMALE, bookmarks = PixivBookmarkRange(100, 299)))
        val free = PixivSearchMemory.starting(kept, PixivSearchKind.WORKS, isPremium = false)
        assertEquals(PixivSearchSort.NEWEST, free.sort)
        assertNull(free.bookmarks)
        assertEquals(PixivSearchSort.POPULAR_FEMALE, PixivSearchMemory.starting(kept, PixivSearchKind.WORKS, isPremium = true).sort)
    }

    @Test
    fun aFreshSearchHidesAiWhenTheFeedsDo() {
        assertTrue(PixivSearchMemory.starting(Settings().with(PixivPrefs.hideAi, true), PixivSearchKind.NOVELS, false).hideAi)
        assertNull(PixivSearchMemory.kept(PixivSearchMemory.keeping(Settings(), PixivSearchKind.NOVELS, null), PixivSearchKind.NOVELS))
    }

    @Test
    fun recentSearchesPutTheNewestFirstOnceWhateverItsCase() {
        val queries = PixivRecentSearches.remembered(listOf("Sky", "sea"), " sky ")
        assertEquals(listOf("sky", "sea"), queries)
        assertEquals(PixivRecentSearches.CAP, (1..30).fold(emptyList<String>()) { list, n -> PixivRecentSearches.remembered(list, "q$n") }.size)
        assertEquals(listOf("sea"), PixivRecentSearches.forgotten(queries, "sky"))
    }

    @Test
    fun theWordBeingTypedIsTheOneAfterTheLastSpace() {
        assertEquals("sk", PixivTyping.typedWord("blue sk"))
        assertTrue(PixivTyping.hasEarlierWords("blue sk"))
        assertFalse(PixivTyping.hasEarlierWords("sk"))
        assertEquals("blue sky ", PixivTyping.replacedTypedWord("blue sk", "sky"))
        assertEquals(123L, PixivTyping.numericId(" 123 "))
        assertNull(PixivTyping.numericId("12a"))
        assertNull(PixivTyping.numericId("0"))
    }

    @Test
    fun favouriteTagsKeepTheReadersOrderOnceEach() {
        val two = PixivFavoriteTags.added(PixivFavoriteTags.added(Settings(), PixivTag("a")), PixivTag("b", "B"))
        assertEquals(listOf("a", "b"), PixivFavoriteTags.of(two).map { it.name })
        assertEquals(listOf("b", "a"), PixivFavoriteTags.of(PixivFavoriteTags.moved(two, 0, 1)).map { it.name })
        assertTrue(PixivFavoriteTags.contains(two, " A "))
        assertEquals(listOf("b"), PixivFavoriteTags.of(PixivFavoriteTags.removed(two, "A")).map { it.name })
        assertEquals("B", PixivFavoriteTags.of(two).last().translatedName)
    }

    @Test
    fun theCopyTemplateIsFilledInOnePass() {
        val illust = work(9, userId = 4, tags = listOf("cat", "sky")).copy(title = "{user_name}")
        assertEquals("{user_name} by Artist 4 #cat #sky 9", PixivCopyInfo.filled("{title} by {user_name} {tags} {illust_id}", illust))
        assertEquals("mine", PixivCopyInfo.template(Settings().with(PixivPrefs.copyTemplate, "mine"), "default"))
        assertEquals("default", PixivCopyInfo.template(Settings(), "default"))
    }
}
