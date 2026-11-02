package com.aimdi.xtanative.networks.booru

import com.aimdi.xtanative.networks.booru.BooruEngine.DANBOORU
import com.aimdi.xtanative.networks.booru.BooruEngine.E621
import com.aimdi.xtanative.networks.booru.BooruEngine.GELBOORU_V2
import com.aimdi.xtanative.networks.booru.BooruEngine.MOEBOORU
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Searches as XTA builds and reads them (booru_query.dart, booru_metatags.dart, the blacklist in booru_parse.dart). */
class BooruQueryTest {
    @Test
    fun whatIsTypedIsFoundWhereAWordOfTheNameStarts() {
        assertEquals(0, BooruQuery.matchStart("blue_eyes", "blu"))
        assertEquals(8, BooruQuery.matchStart("hatsune_miku", "miku"))
        assertEquals(9, BooruQuery.matchStart("admiral_(kancolle)", "kancolle"))
        assertEquals(5, BooruQuery.matchStart("fate/zero", "zero"))
        assertEquals(0, BooruQuery.matchStart("初音ミク", "初音"))
        assertNull(BooruQuery.matchStart("mikumiku", "kumi"), "not where a word starts")
        assertNull(BooruQuery.matchStart("blue_eyes", ""))
    }

    @Test
    fun appendingSkipsRepeatsAndLetsATagTakeItsRivalsPlace() {
        assertEquals(listOf("cat", "dog"), BooruQuery.append(listOf("cat"), listOf("cat", "dog")))
        assertEquals(listOf("dog", "-cat"), BooruQuery.append(listOf("cat", "dog"), listOf("-cat")))
        assertEquals(listOf("cat", "rating:e"), BooruQuery.append(listOf("rating:g", "cat"), listOf("rating:e")))
        assertEquals(listOf("sort:random"), BooruQuery.append(listOf("order:score"), listOf("sort:random")))
        assertEquals(listOf("-rating:e", "-rating:q"), BooruQuery.append(listOf("-rating:e"), listOf("-rating:q")))
        assertEquals(listOf("cat"), BooruQuery.append(listOf("cat"), listOf("-", "~", "rating:")))
    }

    @Test
    fun typingSplitsFinishedTagsFromTheOneStillBeingTyped() {
        assertEquals(BooruTypedQuery(emptyList(), "blu"), BooruQuery.splitInput("blu"))
        assertEquals(BooruTypedQuery(listOf("cat_ears"), ""), BooruQuery.splitInput("cat_ears "))
        assertEquals(BooruTypedQuery(listOf("a", "b"), "-c"), BooruQuery.splitInput("a  b -c"))
        assertEquals(listOf("初音ミク", "solo"), BooruQuery.tokens("初音ミク　solo"), "an ideographic space splits too")
    }

    @Test
    fun suggestionsAskForTheTagWithoutItsOperatorAndSkipMetatags() {
        assertEquals("blu", BooruQuery.suggestionPrefix("-Blu"))
        assertEquals("blu", BooruQuery.suggestionPrefix("~blu"))
        assertNull(BooruQuery.suggestionPrefix("b"))
        assertNull(BooruQuery.suggestionPrefix("rating:g"))
        assertEquals("re:ze", BooruQuery.suggestionPrefix("re:ze"), "a colon outside a metatag is part of the tag")
        assertEquals("-blue_eyes", BooruQuery.tokenFor("-blu", "blue_eyes"))
        assertEquals("blue_eyes", BooruQuery.tokenFor("blu", "blue_eyes"))
    }

    @Test
    fun aTagSetIsOneSearchWhateverItsOrder() {
        assertEquals(BooruQuery.tagSetKey("blue_eyes  solo"), BooruQuery.tagSetKey("solo Blue_Eyes"))
        assertEquals("blue_eyes solo", BooruQuery.normalise("  Blue_Eyes   solo solo "))
        assertEquals("1girl rating:g", BooruQuery.normalise("1girl rating:g"))
        assertNull(BooruQuery.normalise("   "))
        assertEquals("blue_sky", BooruHosts.normaliseTag("  Blue Sky "))
        assertNull(BooruHosts.normaliseTag("   "))
    }

    @Test
    fun operatorsToggleOnTheTagBeingTyped() {
        assertEquals("-", BooruQuery.toggleOperator("", BooruTagOperator.EXCLUDE))
        assertEquals("-blue", BooruQuery.toggleOperator("blue", BooruTagOperator.EXCLUDE))
        assertEquals("blue", BooruQuery.toggleOperator("-blue", BooruTagOperator.EXCLUDE))
        assertEquals("~blue", BooruQuery.toggleOperator("-blue", BooruTagOperator.EITHER))
    }

    @Test
    fun relatedTagsAreTheOnesSharedMostLessThoseTheSearchNames() {
        val posts = listOf(
            post("1", tags = listOf("cat", "solo", "smile")),
            post("2", tags = listOf("cat", "solo", "hat")),
            post("3", tags = listOf("cat", "smile", "solo")),
        )
        assertEquals(listOf("solo", "smile"), BooruQuery.relatedTags(posts, listOf("cat")))
        assertEquals(listOf("smile"), BooruQuery.relatedTags(posts, listOf("-solo", "cat")))
        assertEquals(listOf("animal_ears"), BooruQuery.unsearchedTags(listOf("cat_ears", "animal_ears"), listOf("-Cat_Ears")))
        assertEquals(listOf("blue_eyes"), BooruQuery.plainTags(listOf("blue_eyes", "-smile", "~hat", "order:score")))
    }

    @Test
    fun queriesForAPostsFamilyAndAPool() {
        assertEquals("parent:4", BooruQuery.familyQuery(post("5", parentId = "4")))
        assertEquals("parent:5", BooruQuery.familyQuery(post("5")))
        assertEquals("ordpool:7", BooruQuery.poolQuery("7"))
    }

    @Test
    fun ratingMetatagsFollowEachEnginesSpelling() {
        assertEquals("rating:g", BooruRatingRules.metatag(DANBOORU, BooruRating.GENERAL, "https://danbooru.donmai.us"))
        assertEquals("rating:s", BooruRatingRules.metatag(E621, BooruRating.GENERAL, "https://e621.net"))
        assertNull(BooruRatingRules.metatag(MOEBOORU, BooruRating.SENSITIVE, "https://yande.re"))
        assertEquals("rating:general", BooruRatingRules.metatag(GELBOORU_V2, BooruRating.GENERAL, "https://gelbooru.com"))
        assertEquals("rating:safe", BooruRatingRules.metatag(GELBOORU_V2, BooruRating.GENERAL, "https://rule34.xxx"))
        assertEquals("sort:score", BooruQuery.scoreOrderMetatag(GELBOORU_V2))
        assertEquals("order:score", BooruQuery.scoreOrderMetatag(DANBOORU))
        assertFalse(BooruQuery.supportsEither(GELBOORU_V2))
    }

    @Test
    fun twoLettersOfAMetatagOfferItOnItsSitesEngineOnlyAndItsKnownValuesAfterTheColon() {
        fun names(engine: BooruEngine, input: String, cap: BooruRating = BooruRating.GENERAL) =
            BooruMetatags.suggestions(engine, input, "https://example.com", cap).map { it.name }
        assertEquals(listOf("score:"), names(DANBOORU, "sc"))
        assertEquals(listOf("order:", "ordfav:"), names(DANBOORU, "or"))
        assertEquals(emptyList(), names(GELBOORU_V2, "or"), "Gelbooru sorts with sort:")
        assertEquals(listOf("sort:"), names(GELBOORU_V2, "so"))
        assertEquals(emptyList(), names(DANBOORU, "s"), "one letter is too little to go on")
        assertEquals(listOf("order:favcount", "order:filesize"), names(DANBOORU, "order:f"))
        assertEquals(emptyList(), names(DANBOORU, "order:score"), "already complete")
        assertEquals(listOf("rating:g"), names(DANBOORU, "-rating:"), "only up to the rating cap")
        assertEquals(listOf("rating:s", "rating:q", "rating:e"), names(E621, "rating:", BooruRating.EXPLICIT))
        assertEquals(emptyList(), names(DANBOORU, "score:"), "any number goes")
        assertTrue(BooruMetatags.suggestsName(BooruTagSuggestion("order:")))
        assertFalse(BooruMetatags.suggestsName(BooruTagSuggestion("order:score")))
    }

    @Test
    fun oneBlacklistedTagHidesAnyPostThatHasIt() {
        assertTrue(BooruFilter.muted(post(), setOf("solo")))
        assertFalse(BooruFilter.muted(post(), setOf("group")))
    }

    @Test
    fun severalTagsHideAPostOnlyTogether() {
        assertTrue(BooruFilter.muted(post(), setOf("solo smile")))
        assertFalse(BooruFilter.muted(post(), setOf("solo hat")))
    }

    @Test
    fun negationAndEitherOf() {
        assertTrue(BooruFilter.muted(post(), setOf("solo -hat")))
        assertFalse(BooruFilter.muted(post(), setOf("solo -smile")))
        assertTrue(BooruFilter.muted(post(), setOf("~hat ~smile")))
        assertFalse(BooruFilter.muted(post(), setOf("~hat ~cap")))
    }

    @Test
    fun ratingsReadInTheHostsOwnLetters() {
        assertTrue(BooruFilter.muted(post(rating = BooruRating.SENSITIVE), setOf("rating:s")))
        assertTrue(BooruFilter.muted(post(engine = MOEBOORU, rating = BooruRating.GENERAL), setOf("rating:s")), "Moebooru's s is Safe")
        assertFalse(BooruFilter.muted(post(), setOf("rating:e")))
        assertTrue(BooruFilter.muted(post(), setOf("solo -rating:e")))
    }

    @Test
    fun anEntryWithAMetatagThePostCannotAnswerNeverHides() {
        assertFalse(BooruFilter.muted(post(), setOf("solo status:deleted")))
        assertFalse(BooruFilter.muted(post(), setOf("solo -status:deleted")))
        assertFalse(BooruFilter.muted(post(), setOf("solo score:lots")), "not a number")
        assertFalse(BooruFilter.muted(post(), setOf("user:someone")), "the host did not say who uploaded it")
    }

    @Test
    fun scoreIdSizesAndCountsCompareLikeTheHostsCompareThem() {
        val post = post("1500", score = -3, width = 1920, height = 1080, favCount = 7)
        assertTrue(BooruFilter.muted(post, setOf("score:<0")))
        assertFalse(BooruFilter.muted(post, setOf("score:>=0")))
        assertFalse(BooruFilter.muted(post, setOf("solo -score:<0")))
        assertTrue(BooruFilter.muted(post, setOf("score:-5..-1")))
        assertFalse(BooruFilter.muted(post, setOf("score:..-4")))
        assertTrue(BooruFilter.muted(post, setOf("id:>1000")))
        assertTrue(BooruFilter.muted(post, setOf("id:1500")))
        assertTrue(BooruFilter.muted(post, setOf("width:>=1920 height:<1200")))
        assertFalse(BooruFilter.muted(post, setOf("favcount:10..")))
        assertTrue(BooruFilter.muted(post, setOf("tagcount:<3")), "it has two tags")
        assertFalse(BooruFilter.muted(post, setOf("score:99999999999999999999")), "a number too big to read matches nothing")
    }

    @Test
    fun uploaderFileTypeMd5ParentAndSourceMatchWhatThePostSays() {
        val post = post(uploader = "SomeOne", md5 = "abc123", parentId = "9", source = "https://www.pixiv.net/artworks/1")
        assertTrue(BooruFilter.muted(post, setOf("user:someone")))
        assertTrue(BooruFilter.muted(post, setOf("filetype:jpg")))
        assertFalse(BooruFilter.muted(post, setOf("filetype:gif")))
        assertTrue(BooruFilter.muted(post, setOf("md5:abc123")))
        assertTrue(BooruFilter.muted(post, setOf("parent:9")))
        assertFalse(BooruFilter.muted(post, setOf("parent:none")))
        assertTrue(BooruFilter.muted(post(), setOf("parent:none")))
        assertTrue(BooruFilter.muted(post, setOf("source:https://www.pixiv.net")), "a source matches by its start")
        assertTrue(BooruFilter.muted(post, setOf("source:*artworks*")))
        assertFalse(BooruFilter.muted(post, setOf("source:none")))
    }

    @Test
    fun blacklistEntriesKeepTheirSpacesAndLoseTheirDuplicates() {
        assertEquals(setOf("blue_sky", "solo smile"), BooruFilter.blacklist(listOf("Blue_Sky", "solo  Smile", "", "blue_sky")))
        assertFalse(BooruFilter.matches(post(), emptyList()), "no tokens match nothing")
    }
}
