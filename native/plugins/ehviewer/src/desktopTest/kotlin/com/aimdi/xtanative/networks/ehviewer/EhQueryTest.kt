package com.aimdi.xtanative.networks.ehviewer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** XTA's ehBuildSearch test and the query editing of test/eh_search_test.dart. */
class EhQueryTest {
    @Test
    fun aSearchAddsItsLanguageTagAndMinimumRating() {
        val built = EhQuery.build("flan", catMask = 0, minRating = 4, language = "english")
        assertEquals("flan language:english", built.query)
        assertEquals(listOf("f_search", "f_cats", "f_apply", "f_sr", "f_srdd"), built.params.keys.toList())
        assertEquals("flan language:english", built.params["f_search"])
        assertEquals("on", built.params["f_sr"])
        assertEquals("4", built.params["f_srdd"])
        assertEquals("Apply Filter", built.params["f_apply"])
    }

    @Test
    fun aRatingUnderTwoStarsAndNoLanguageAddNothing() {
        val built = EhQuery.build("  flan ", catMask = 1017, minRating = 1)
        assertEquals("flan", built.query)
        assertEquals(mapOf("f_search" to "flan", "f_cats" to "1017", "f_apply" to "Apply Filter"), built.params)
    }

    @Test
    fun theTermBeingTypedStartsAfterTheLastFinishedTerm() {
        assertEquals(22, EhQuery.lastTermStart("female:\"big breasts$\" -mal"))
        assertEquals(12, EhQuery.lastTermStart("artist:bob$ big br"))
        assertEquals(0, EhQuery.lastTermStart("summer big br"))
        assertEquals(0, EhQuery.lastTermStart("female:\"big br"))
    }

    @Test
    fun suggestionsAreAskedForTheWholeTermThenItsLastWords() {
        assertEquals(
            listOf(EhSuggestionTerm(0, "summer big br"), EhSuggestionTerm(7, "big br"), EhSuggestionTerm(11, "br")),
            EhQuery.suggestionTerms("summer big br"),
        )
        assertEquals(listOf(EhSuggestionTerm(0, "bi")), EhQuery.suggestionTerms("-bi"))
        assertEquals(listOf(EhSuggestionTerm(0, "female:big br"), EhSuggestionTerm(12, "br")), EhQuery.suggestionTerms("female:\"big br"))
        assertTrue(EhQuery.suggestionTerms("x").isEmpty())
        assertTrue(EhQuery.suggestionTerms("female:\"big breasts$\"").isEmpty())
    }

    @Test
    fun aSuggestionReplacesItsTermAndKeepsATypedOperator() {
        val tag = EhTag(EhNamespace.FEMALE, "big breasts")
        assertEquals("summer -female:\"big breasts$\" ", EhQuery.insertSuggestion("summer -big b", EhSuggestionTerm(7, "big b"), tag))
        assertEquals("female:\"big breasts$\" ", EhQuery.insertSuggestion("bi", EhSuggestionTerm(0, "bi"), tag))
    }

    @Test
    fun anUploadersGalleriesAreASearch() {
        assertEquals("uploader:\"Alice\"", EhQuery.uploader("Alice"))
    }
}
