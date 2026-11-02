package com.aimdi.xtanative.networks.rss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RssHtmlTest {
    @Test
    fun sanitizingRemovesExecutionPaths() {
        val payload = """<p>Readable</p><a href="java&#10;script:alert(1)">link</a>""" +
            """<iframe srcdoc="&lt;script&gt;alert(1)&lt;/script&gt;"></iframe>""" +
            """<meta http-equiv="refresh" content="0;url=https://bad.invalid"><base href="https://bad.invalid">"""

        val document = RssHtml.sanitize(payload)

        assertTrue("Readable" in document)
        assertFalse("alert(1)" in document)
        assertFalse("srcdoc" in document)
        assertFalse("http-equiv" in document)
        assertFalse("<base" in document)
    }

    @Test
    fun sanitizingDropsHandlersAndScriptAddressesButKeepsWhatIsRead() {
        val document = RssHtml.sanitize(
            """<p onclick="steal()">Text <a href="https://example.org/a">a link</a></p>""" +
                """<img src="https://example.org/a.jpg" onerror="steal()"><script>steal()</script>""" +
                """<a href=" VBScript:x">b</a><img src="data:image/png;base64,AAAA"><svg><a xlink:href="javascript:x">c</a></svg>""",
        )

        assertEquals(
            """<p>Text <a href="https://example.org/a">a link</a></p><img src="https://example.org/a.jpg"><a>b</a><img>""",
            document,
        )
    }

    @Test
    fun anExcerptIsTheTextCutTo240Characters() {
        assertEquals("Hello there and more", RssHtml.excerpt("<p>Hello <b>there</b></p>\n\n<p>and   more</p>"))
        assertEquals("a b", RssHtml.excerpt("a<br>b"))
        val long = "word ".repeat(100)
        val cut = RssHtml.excerpt(long)!!
        assertEquals(238, cut.length, "237 characters and the ellipsis, as XTA cuts")
        assertTrue(cut.endsWith("…"))
        assertEquals(240, RssHtml.excerpt("x".repeat(240))!!.length, "240 characters are kept whole")
        assertNull(RssHtml.excerpt("<p> </p>"))
        assertNull(RssHtml.excerpt(null))
    }
}
