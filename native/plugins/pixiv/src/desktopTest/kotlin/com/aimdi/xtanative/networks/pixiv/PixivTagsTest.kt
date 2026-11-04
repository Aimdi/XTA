package com.aimdi.xtanative.networks.pixiv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rules about tags and ranking boards XTA's own tests encode. */
class PixivTagsTest {
    private fun kinds(vararg names: String): Map<String, PixivTagKind> =
        PixivTagKinds.kinded(names.map { PixivTag(it) }).associate { it.tag.name to it.kind }

    @Test
    fun aCharacterTagNamesItsSeriesInAsciiOrFullWidthParentheses() {
        assertEquals("勝利の女神:NIKKE", PixivTagKinds.characterSeries("マルチャーナ(勝利の女神:NIKKE)"))
        assertEquals("ブルーアーカイブ", PixivTagKinds.characterSeries("ホシノ（ブルーアーカイブ）"))
        assertEquals("Series", PixivTagKinds.characterSeries("Name (Series)"))
    }

    @Test
    fun aCharacterTagNeedsBothANameAndASeriesAndMatchingParentheses() {
        for (tag in listOf("(勝利の女神:NIKKE)", "マルチャーナ()", "（）", " (Series)", "Name( )", "マルチャーナ", "(・ω・)", "Name(Series）", "Name（Series)")) {
            assertNull(PixivTagKinds.characterSeries(tag), tag)
        }
    }

    @Test
    fun aNameSeriesTagIsACharacterAndItsSeriesACopyright() {
        assertEquals(
            mapOf("マルチャーナ(勝利の女神:NIKKE)" to PixivTagKind.CHARACTER, "勝利の女神:NIKKE" to PixivTagKind.COPYRIGHT, "水着" to PixivTagKind.GENERAL),
            kinds("マルチャーナ(勝利の女神:NIKKE)", "勝利の女神:NIKKE", "水着"),
        )
        assertEquals(
            mapOf("ホシノ（ブルーアーカイブ）" to PixivTagKind.CHARACTER, "ブルーアーカイブ" to PixivTagKind.COPYRIGHT),
            kinds("ホシノ（ブルーアーカイブ）", "ブルーアーカイブ"),
        )
        assertEquals(mapOf("アリス(ブルーアーカイブ)" to PixivTagKind.CHARACTER, "ブルアカ" to PixivTagKind.GENERAL), kinds("アリス(ブルーアーカイブ)", "ブルアカ"))
    }

    @Test
    fun originalIsACopyrightAndRatingsAiAndMilestonesAreMeta() {
        assertEquals(PixivTagKind.COPYRIGHT, PixivTagKinds.kindOf("オリジナル", emptySet()))
        for (tag in listOf("R-18", "R-18G", "AI生成", "1000users入り", "50users入り", "10000users入り")) {
            assertEquals(PixivTagKind.META, PixivTagKinds.kindOf(tag, emptySet()), tag)
        }
        for (tag in listOf("女の子", "r-18", "R-18G生成", "users入り", "1000users", "AIイラスト", "1000users入り!")) {
            assertEquals(PixivTagKind.GENERAL, PixivTagKinds.kindOf(tag, emptySet()), tag)
        }
    }

    @Test
    fun tagsAreOrderedByKindKeepingPixivsOrderWithinOne() {
        val names = listOf("R-18", "女の子", "マルチャーナ(勝利の女神:NIKKE)", "1000users入り", "オリジナル", "水着", "ホシノ（ブルーアーカイブ）", "勝利の女神:NIKKE")
        assertEquals(
            listOf("オリジナル", "勝利の女神:NIKKE", "マルチャーナ(勝利の女神:NIKKE)", "ホシノ（ブルーアーカイブ）", "女の子", "水着", "R-18", "1000users入り"),
            PixivTagKinds.kinded(names.map { PixivTag(it) }).map { it.tag.name },
        )
        assertTrue(PixivTagKinds.kinded(emptyList()).isEmpty())
    }

    @Test
    fun r18BoardsAreOfferedOnlyWhileShowR18IsOn() {
        val r18 = listOf("day_r18", "day_r18_ai", "week_r18", "week_r18g")
        val hidden = PixivRanking.offered(PixivRanking.ILLUST_MODES, showR18 = false).map { it.id }
        val shown = PixivRanking.offered(PixivRanking.ILLUST_MODES, showR18 = true).map { it.id }
        assertTrue(hidden.none { it in r18 })
        assertTrue(shown.containsAll(r18))
        assertTrue(hidden.containsAll(listOf("day", "week", "month", "day_manga", "day_ai")))
    }

    @Test
    fun theAiBoardsAreMarkedForWorksAndNovelsAlike() {
        assertTrue(PixivRanking.isAi("day_ai"))
        assertTrue(PixivRanking.isAi("day_r18_ai"))
        assertFalse(PixivRanking.isAi("day"))
        assertFalse(PixivRanking.isAi("unknown"))
        assertTrue(PixivRanking.isAi("week_ai", PixivRanking.NOVEL_MODES))
        assertFalse(PixivRanking.isAi("week_ai"), "not an illustration board")
        assertEquals("plugin_pixiv_ranking_rookie", PixivRanking.ILLUST_MODES.single { it.id == "week_rookie" }.labelKey)
    }
}
