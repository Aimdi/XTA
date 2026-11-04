package com.aimdi.xtanative.networks.booru

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Searches past a host's tag limit, as XTA's booru_tag_limit.dart shares them out. */
class BooruTagLimitTest {
    private val uses = mapOf("blue_eyes" to 2_524_807, "long_hair" to 6_357_605, "frieren" to 16_526)

    private fun split(vararg tokens: String, limit: Int = 2, uses: Map<String, Int> = this.uses) =
        BooruTagLimit.split(tokens.toList(), limit, uses)

    @Test
    fun tagsExcludedAndEitherTagsAndOrderCountTheMetatagsDanbooruLetsThroughDoNot() {
        val tokens = listOf(
            "blue_eyes", "-smile", "~hat", "order:score", "rating:g", "-rating:e", "rating:g,s", "status:active",
            "-status:deleted", "limit:5", "is:sfw", "parent:none", "filetype:png", "score:>10", "age:<1w",
        )
        assertEquals(4, BooruTagLimit.counted(tokens))
    }

    @Test
    fun aMetatagNotSeenLettingThroughCountsToBeSafe() {
        assertEquals(3, BooruTagLimit.counted(listOf("ordfav:someone", "pool:123", "user:someone")))
    }

    @Test
    fun aSearchThatFitsGoesToTheHostWhole() {
        assertEquals(BooruTagSplit(listOf("blue_eyes", "long_hair", "rating:g"), emptyList()), split("blue_eyes", "long_hair", "rating:g"))
    }

    @Test
    fun theHostGetsTheTagsWithTheFewestPostsInTheOrderTheyWereTyped() {
        assertEquals(BooruTagSplit(listOf("blue_eyes", "frieren"), listOf("long_hair")), split("blue_eyes", "long_hair", "frieren"))
    }

    @Test
    fun withoutCountsTheTagsTypedFirstGoToTheHost() {
        assertEquals(
            BooruTagSplit(listOf("long_hair", "blue_eyes"), listOf("frieren")),
            split("long_hair", "blue_eyes", "frieren", uses = emptyMap()),
        )
    }

    @Test
    fun aTagTheHostDoesNotListAnAliasOrATypoGoesToTheHostFirst() {
        assertEquals(BooruTagSplit(listOf("blue_eyes", "kancolle"), listOf("long_hair")), split("blue_eyes", "long_hair", "kancolle"))
    }

    @Test
    fun metatagsStayWithTheHostAndTakeTheirRoomFreeOnesTakeNone() {
        assertEquals(
            BooruTagSplit(listOf("frieren", "order:score", "rating:g"), listOf("blue_eyes")),
            split("blue_eyes", "frieren", "order:score", "rating:g"),
        )
    }

    @Test
    fun aWildcardStaysWithTheHostTheDeviceCannotMatchItByName() {
        assertEquals(BooruTagSplit(listOf("blue*", "frieren"), listOf("long_hair")), split("blue*", "long_hair", "frieren"))
    }

    @Test
    fun anEitherGroupStaysTogetherOnTheHostWhenItFitsAndOnTheDeviceWhenNot() {
        assertEquals(BooruTagSplit(listOf("blue_eyes"), listOf("~hat", "~cap")), split("blue_eyes", "~hat", "~cap"))
        assertEquals(BooruTagSplit(listOf("~hat", "~cap"), listOf("-smile")), split("~hat", "~cap", "-smile", uses = emptyMap()))
    }

    @Test
    fun excludedTagsGoToTheHostLast() {
        assertEquals(BooruTagSplit(listOf("-smile", "blue_eyes"), listOf("-hat")), split("-smile", "blue_eyes", "-hat"))
    }

    @Test
    fun nothingCanBeSharedOutWhenTheMetatagsAloneAreMoreThanTheHostTakes() {
        assertNull(split("order:score", "ordfav:someone", "blue_eyes", limit = 1, uses = emptyMap()))
    }

    @Test
    fun theLimitIsReadFromWhatDanbooruSays() {
        assertEquals(2, BooruTagLimit.limitIn(Fixtures.text("danbooru_tag_limit.json")))
        assertEquals(6, BooruTagLimit.limitIn("You cannot search for more than 6 tags at a time."))
    }

    @Test
    fun theLimitIsDanboorusLowestWhenItsErrorNamesNoNumberAndUnknownForAnythingElse() {
        assertEquals(2, BooruTagLimit.limitIn("""{"error":"PostQuery::TagLimitError"}"""))
        assertNull(BooruTagLimit.limitIn("""{"error":"Something else"}"""))
    }
}
