package com.aimdi.xtanative.networks.substack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A post body as XTA cleans it: substack_sanitize, substack_discovery_tts and substack_audit_probes tests. */
class SubstackHtmlTest {
    @Test
    fun eventHandlersDoNotSurviveWhateverElementTheyAreOn() {
        val clean = SubstackHtml.sanitize(
            """<p onclick="steal()">Text</p><img src="x.png" onerror="fetch('https://example.invalid')">
            <div ONLOAD="run()">Body</div><a href="/post" onmouseover="run()">Link</a><iframe srcdoc="<script>x</script>"></iframe>""",
        ).lowercase()
        listOf("onclick", "onerror", "onload", "onmouseover", "srcdoc").forEach { assertFalse(it in clean, it) }
        assertTrue("body" in clean)
    }

    @Test
    fun anAddressThatIsCodeLosesTheAddressNotTheText() {
        val link = SubstackHtml.sanitize("""<a href="javascript:alert(1)">Read this</a>""")
        assertFalse("javascript:" in link)
        assertTrue("Read this" in link, "the writing stays; only the trigger goes")
        val smuggled = SubstackHtml.sanitize("""<iframe src="data:text/html;base64,PHNjcmlwdD4="></iframe><img src="VBScript:msgbox(1)"><a href=" java&#x09;script:x()">x</a>""").lowercase()
        listOf("data:text/html", "vbscript:", "script:x").forEach { assertFalse(it in smuggled, it) }
    }

    @Test
    fun scriptsFormsAndSubstacksChromeAreRemovedOutright() {
        val clean = SubstackHtml.sanitize(
            """<p>Before</p><script>steal()</script><noscript>x</noscript><button>Follow</button><svg></svg>
            <div class="subscription-widget-wrap-editor" data-component-name="SubscribeWidgetToDOM"><div class="preamble"><p class="cta-caption">Thanks for reading!</p></div>
            <form class="subscription-widget-subscribe"><input type="email" name="email"><input type="submit" value="Subscribe"></form></div>
            <form><textarea></textarea><select></select></form><div class="paywall">Upgrade</div><p>After</p>""",
        )
        listOf("steal()", "<button", "<svg", "<form", "<input", "<textarea", "<select", "Upgrade").forEach { assertFalse(it in clean, it) }
        assertTrue("<p>Before</p>" in clean)
        assertTrue("<p>After</p>" in clean)
        assertTrue("Thanks for reading!" in clean, "BUG-07: the widget's caption stays, its form goes")
    }

    @Test
    fun ordinaryWritingAndItsMarkupComeThroughUntouched() {
        val clean = SubstackHtml.sanitize(
            """<p>A <strong>real</strong> paragraph with a <a href="https://example.com/post">link</a> and an <img src="https://example.com/pic.png" alt="a picture">.</p>""",
        )
        assertTrue("<strong>real</strong>" in clean)
        assertTrue("https://example.com/post" in clean)
        assertTrue("alt=\"a picture\"" in clean)
        assertTrue("https://www.youtube.com/embed/abc123" in SubstackHtml.sanitize("""<iframe src="https://www.youtube.com/embed/abc123"></iframe>"""))
    }

    @Test
    fun blocksDrawnOnlyBySubstacksScriptsLeaveAPlaceholderAndOtherEmptiesGo() {
        val placeholder = """<div class="xta-web-only" data-component-name="XtaWebOnly"></div>"""
        val drawn = listOf(
            """<div class="latex-rendered" data-attrs="{&quot;persistentExpression&quot;:&quot;E=mc^2&quot;}" data-component-name="LatexBlockToDOM"></div>""",
            """<div class="image-gallery-embed" data-attrs="{&quot;gallery&quot;:{}}"></div>""",
            """<div class="poll-embed" data-attrs="{&quot;id&quot;:123}" data-component-name="PollToDOM"></div>""",
            """<div class="native-audio-embed" data-component-name="AudioPlaceholder" data-attrs="{&quot;duration&quot;:93.5}"></div>""",
        )
        drawn.forEach { markup ->
            val clean = SubstackHtml.sanitize("<p>Before</p>$markup<p>After</p>")
            assertEquals("<p>Before</p>$placeholder<p>After</p>", clean, "BUG-08: removed without a trace before")
            assertEquals(clean, SubstackHtml.sanitize(clean), "sanitizing twice changes nothing")
        }
        assertEquals("<p>A</p>", SubstackHtml.sanitize("<p>A</p><div></div><span> </span>"), "unmarked empties go")
        assertTrue("<img" in SubstackHtml.sanitize("""<div><img src="https://cdn.example/a.jpg"></div>"""), "a block holding a picture is not empty")
    }

    @Test
    fun plainTextKeepsParagraphBreaksAndSkipsFootnoteNumbers() {
        val plain = SubstackHtml.plainText("<p>One.</p><p>Two.</p>")
        assertEquals("One.\nTwo.", plain)
        val footnoted = SubstackHtml.plainText(
            """<p>A claim<a class="footnote-anchor" id="footnote-anchor-1" href="#footnote-1">1</a>.</p>""" +
                """<div class="footnote"><a id="footnote-1" href="#footnote-anchor-1" class="footnote-number">1</a><div class="footnote-content"><p>The source.</p></div></div>""",
        )
        assertTrue(footnoted.startsWith("A claim."), "\"A claim1.\" before BUG-14 was fixed")
        assertTrue("The source." in footnoted)
        assertFalse('1' in footnoted)
        assertEquals("The sentence.", SubstackHtml.plainText("""<p onclick="steal()">The sentence.</p>"""))
        assertEquals("Hello world.\nSecond paragraph", SubstackHtml.plainText("<p>Hello <strong>world</strong>.</p><button>Follow</button><p>Second paragraph</p>"))
        assertEquals("One\ntwo", SubstackHtml.plainText("<p>One<br>two</p>"))
        assertEquals("a b and c", SubstackHtml.plainText("a&nbsp;b \n and\tc"), "a no-break space is a space, as Dart's \\s has it")
    }

    @Test
    fun speechIsTheTitleWhereItIsFromTheExcerptThenTheBody() {
        val teaser = SubstackHtml.speakText(title = "A paid teaser", subtitle = "The opening line", publicationName = "Example Pub")
        assertEquals("A paid teaser\n\nExample Pub\n\nThe opening line", teaser)
        val both = SubstackHtml.speakText(title = "Title", bodyHtml = "<p>From HTML</p>", bodyPlain = "From the live page")
        assertTrue("From the live page" in both)
        assertFalse("From HTML" in both)
        assertEquals("Title\n\nHello world.", SubstackHtml.speakText(title = "Title", authorName = " ", bodyHtml = "<p>Hello <strong>world</strong>.</p>"))
    }
}
