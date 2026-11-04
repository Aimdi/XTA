package com.aimdi.xtanative.networks.websources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import org.junit.jupiter.api.Timeout

class WebSourcePathsTest {
    private val page = Json.parseToJsonElement(
        """{"data": {"children": [{"title": "First", "score": 12, "tags": ["a", "b"]}, {"title": "Second", "nsfw": false, "gone": null}]}}""",
    )

    private fun texts(values: List<JsonElement>) = values.map { WebSourcePaths.jsonText(it) }

    @Test
    fun jsonPathsReadKeysIndexesAndEveryElement() {
        assertEquals(listOf("First"), texts(WebSourcePaths.jsonValuesAt(page, "data.children[0].title")))
        assertEquals(listOf("Second"), texts(WebSourcePaths.jsonValuesAt(page, "\$.data.children[1].title")))
        assertEquals(listOf("First", "Second"), texts(WebSourcePaths.jsonValuesAt(page, "data.children[*].title")))
        assertEquals(listOf("a", "b"), texts(WebSourcePaths.jsonValuesAt(page, "data.children[0].tags[*]")))
        assertEquals(listOf(page), WebSourcePaths.jsonValuesAt(page, "$"))
    }

    @Test
    fun aJsonPathThatLeadsNowhereGivesNothing() {
        assertTrue(WebSourcePaths.jsonValuesAt(page, "data.missing.title").isEmpty())
        assertTrue(WebSourcePaths.jsonValuesAt(page, "data.children[5].title").isEmpty())
        assertTrue(WebSourcePaths.jsonValuesAt(page, "data.children.title").isEmpty())
        assertTrue(WebSourcePaths.jsonValuesAt(JsonPrimitive("text"), "a").isEmpty())
        assertTrue(WebSourcePaths.jsonValuesAt(page, "data.children[1].gone").isEmpty(), "a JSON null is no value")
        assertTrue(WebSourcePaths.jsonValuesAt(page, "data.children[99999999999999999999]").isEmpty(), "an index too long to be one")
    }

    @Test
    fun numbersAndBooleansAreTextMapsAndListsAreNot() {
        assertEquals("12", WebSourcePaths.jsonText(JsonPrimitive(12)))
        assertEquals("false", WebSourcePaths.jsonText(JsonPrimitive(false)))
        assertNull(WebSourcePaths.jsonText(buildJsonObject { put("a", 1) }))
        assertNull(WebSourcePaths.jsonText(null))
        assertNull(WebSourcePaths.jsonText(Json.parseToJsonElement("null")))
    }

    @Test
    fun onlyWellFormedPathsArePaths() {
        assertTrue(WebSourcePaths.isJsonPath("data.children[*].title"))
        assertTrue(WebSourcePaths.isJsonPath("$"))
        assertFalse(WebSourcePaths.isJsonPath("a..b"))
        assertFalse(WebSourcePaths.isJsonPath("a[x]"))
        assertFalse(WebSourcePaths.isJsonPath("a b"))
        assertFalse(WebSourcePaths.isJsonPath("a\u00A0b"), "a no-break space is a space, as in JavaScript")
        assertFalse(WebSourcePaths.isJsonPath(""))
        assertFalse(WebSourcePaths.isJsonPath("a."))
        assertFalse(WebSourcePaths.isJsonPath("a[1"))
    }

    @Test
    fun keysMayFollowAnIndexWithoutADot() {
        assertTrue(WebSourcePaths.isJsonPath("images[0]url"))
        val images = Json.parseToJsonElement("""{"images": [{"url": "a.jpg"}]}""")
        assertEquals(listOf("a.jpg"), texts(WebSourcePaths.jsonValuesAt(images, "images[0]url")))
    }

    @Test
    @Timeout(10)
    fun aLongPathThatIsNotOneIsRefusedAtOnce() {
        assertFalse(WebSourcePaths.isJsonPath("a".repeat(4000) + "{"))
        assertFalse(WebSourcePaths.isJsonPath("a[0]b".repeat(4000) + "@"))
    }

    @Test
    fun templatesFillEveryPlaceholderOrGiveNothing() {
        val values = mapOf("post.id" to "42", "name" to "a b")

        assertEquals("https://lemmy.world/post/42", WebSourcePaths.fillTemplate("https://lemmy.world/post/{post.id}") { values[it] })
        assertNull(WebSourcePaths.fillTemplate("{post.id}-{missing}") { values[it] })
        assertEquals(listOf("a", "b.c"), WebSourcePaths.templatePlaceholders("{a}/{b.c}"))
        assertFalse(WebSourcePaths.isTemplate("plain.path"))
    }

    @Test
    fun templatesEscapeValuesInsideAnAddressButNotAWholeAddress() {
        assertEquals("https://x.org/s?q=a%20b%26c", WebSourcePaths.fillTemplate("https://x.org/s?q={name}", encode = true) { "a b&c" })
        assertEquals("https://x.org/a b", WebSourcePaths.fillTemplate("{link}", encode = true) { "https://x.org/a b" })
        assertEquals("https://x.org/?after=", WebSourcePaths.fillTemplate("https://x.org/?after={cursor}", encode = true) { "" })
        assertEquals("caf%C3%A9-!~*'()", WebSourcePaths.encodeComponent("café-!~*'()"), "as Dart's Uri.encodeComponent")
    }

    private val document = Jsoup.parse(
        """
        <ol class="list">
          <li class="story" data-id="s1">
            <a class="title" href="/s/s1">  A   story </a>
            <span class="by">ann</span>
            <div class="body"><p>Hello <b>there</b></p><p>again</p></div>
          </li>
        </ol>
        """.trimIndent(),
    )
    private val item = document.selectFirst("li.story")!!

    @Test
    fun htmlRulesReadTextAttributesTheItemItselfAndMarkup() {
        assertEquals(listOf("A story"), WebSourceSelectors.valuesAt(item, "a.title"))
        assertEquals(listOf("/s/s1"), WebSourceSelectors.valuesAt(item, "a.title@href"))
        assertEquals(listOf("s1"), WebSourceSelectors.valuesAt(item, "@data-id"))
        assertEquals(listOf("<p>Hello <b>there</b></p><p>again</p>"), WebSourceSelectors.valuesAt(item, ".body@html"))
        assertTrue(WebSourceSelectors.valuesAt(item, ".missing").isEmpty())
        assertTrue(WebSourceSelectors.valuesAt(item, "a.title@title").isEmpty(), "a missing attribute is no value")
    }

    @Test
    fun textKeepsASpaceBetweenParagraphsWhichXtaRanTogether() {
        assertEquals(listOf("Hello there again"), WebSourceSelectors.valuesAt(item, ".body"))
    }

    @Test
    fun aRuleIsSplitAtItsAttributeOnly() {
        assertEquals(HtmlRule("a.title", "href"), WebSourceSelectors.htmlRule("a.title@href"))
        assertEquals(HtmlRule("", "data-id"), WebSourceSelectors.htmlRule("@data-id"))
        assertEquals(HtmlRule("a[href^='mailto:x@y.org']", null), WebSourceSelectors.htmlRule("a[href^='mailto:x@y.org']"))
    }

    @Test
    fun aSelectorThisReaderCannotReadMatchesNothing() {
        assertTrue(WebSourceSelectors.isCssSelector("li.story > a.title"))
        assertTrue(WebSourceSelectors.isCssSelector(""))
        assertFalse(WebSourceSelectors.isCssSelector("li[[["))
        assertTrue(WebSourceSelectors.selectAll(document.children().first()!!, "li[[[").isEmpty())
        assertTrue(WebSourceSelectors.selectAll(document.children().first()!!, "li:has(a)").isEmpty(), "jsoup could, XTA could not")
    }

    @Test
    fun selectorsSeeAncestorsOutsideThePostAsXtaDoesButNeverThePostItself() {
        assertEquals(listOf("A story"), WebSourceSelectors.valuesAt(item, "ol.list a"))
        assertTrue(WebSourceSelectors.selectAll(item, "li").isEmpty())
        assertEquals(listOf("li"), WebSourceSelectors.selectAll(document.body(), "li.story").map { it.normalName() })
    }
}
