package com.aimdi.xtanative.core.json

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** XTA's json_test.dart: a step into JSON never throws, and each getter answers only for its own kind. */
class JTest {
    private val doc = J.parse(
        """{"data":{"legacy":{"favorite_count":42,"id_str":"169","price":"110.5","flag":true},"items":[{"name":"first"},{"name":"second"}]}}""",
    )

    @Test
    fun aDeepPathNeedsNoChecksAlongTheWay() {
        assertEquals(42, doc["data"]["legacy"]["favorite_count"].int)
    }

    @Test
    fun aMissingKeyAnywhereOnThePathIsNullAtTheEnd() {
        assertNull(doc["data"]["nope"]["deeper"]["still"].int)
        assertNull(doc["wrong"][3]["shape"].string)
    }

    @Test
    fun listsAreSteppedIntoByIndexAndPastTheEndIsNull() {
        assertEquals("second", doc["data"]["items"][1]["name"].string)
        assertNull(doc["data"]["items"][9]["name"].string)
        assertNull(doc["data"]["items"][-1]["name"].string)
    }

    @Test
    fun indexingAMapOrKeyingAListIsAShapeMismatch() {
        assertFalse(doc["data"][0].exists)
        assertFalse(doc["data"]["items"]["name"].exists)
    }

    @Test
    fun aDocumentThatIsNotJsonAnswersEveryQuestionWithNull() {
        assertFalse(J.parse("nonsense")["chart"]["result"][0].exists)
        assertFalse(J(null)["anything"].exists)
        assertFalse(J.parse("{broken")["data"].exists)
    }

    @Test
    fun eachGetterAnswersOnlyForItsOwnKind() {
        val legacy = doc["data"]["legacy"]
        assertEquals(true, legacy["flag"].boolean)
        assertNull(legacy["favorite_count"].string, "a number is not a string")
        assertNull(legacy["id_str"].boolean)
    }

    @Test
    fun numbersTolerateTheStringWrappedDigitsXIsFondOf() {
        val legacy = doc["data"]["legacy"]
        assertEquals(110.5, legacy["price"].double)
        assertEquals(169L, legacy["id_str"].long)
        assertEquals(42.0, legacy["favorite_count"].double)
    }

    @Test
    fun wordsAreNotNumbers() {
        assertNull(J(JsonPrimitive("threeish")).double)
        assertNull(J(JsonPrimitive("threeish")).long)
    }

    @Test
    fun aListWrapsItsElementsAndAnythingElseIsAnEmptyOne() {
        assertEquals(2, doc["data"]["items"].list.size)
        assertTrue(doc["data"]["legacy"].list.isEmpty())
        assertTrue(doc["absent"].list.isEmpty())
    }

    @Test
    fun existsTellsPresenceApartFromAReadableValue() {
        assertTrue(doc["data"].exists)
        assertNull(doc["data"].string, "present, but not a string")
        assertFalse(doc["gone"].exists)
    }
}
