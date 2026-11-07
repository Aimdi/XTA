package com.aimdi.xtanative.plugins.substack

import com.aimdi.xtanative.networks.substack.SubstackHtml
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.networks.substack.SubstackWebPage
import com.aimdi.xtanative.store.reading.ArticleReadPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's article_reading_test, the parts that are Substack's own. */
class SubstackArticleReadingTest {
    @Test
    fun aDeepLinkAndTheFetchedArticleShareOneReadingPlace() {
        val stub = SubstackPost("a-long-read", "A long read", "a-long-read", "https://studio.substack.com", "Studio")
        val full = stub.copy(id = "128492")
        assertEquals(ReaderIds.readingIdOf(stub), ReaderIds.readingIdOf(full))
    }

    @Test
    fun theSanitizerRemovesEveryWayToRunCodeBeforeTheBridgeIsAdded() {
        val payload = """<p>Readable</p><a href="java&#10;script:alert(1)">link</a>""" +
            """<iframe srcdoc="&lt;script&gt;alert(1)&lt;/script&gt;"></iframe>""" +
            """<meta http-equiv="refresh" content="0;url=https://bad.invalid"><base href="https://bad.invalid">"""
        val document = SubstackHtml.sanitize(payload)
        assertTrue("Readable" in document)
        listOf("alert(1)", "srcdoc", "http-equiv", "<base").forEach { assertFalse(it in document, it) }
    }

    @Test
    fun theBridgeRestoresTheParagraphBeforeTheProportionalPlace() {
        val script = SubstackWebPage.readingBridge(ArticleReadPoint(fraction = 0.4, paragraph = 7, leading = -18.0), 20.0 * 1.5, 1.7)
        assertTrue("30.0" in script)
        assertTrue("\"paragraph\":7" in script)
        assertTrue("p.fraction * maxScroll()" in script)
        assertTrue("observer.disconnect()" in script)
    }
}
