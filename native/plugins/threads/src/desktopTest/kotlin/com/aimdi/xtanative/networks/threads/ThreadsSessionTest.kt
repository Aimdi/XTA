package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A pasted session and the values Meta may change (XTA's threads_direct_test and plugin_repairs_test). */
class ThreadsSessionTest {
    @Test
    fun aPastedCookieHeaderSplitsIntoItsCookies() {
        val session = ThreadsSession("sessionid=abc%3A1; csrftoken=tok; ds_user_id=9; mid=m; ig_did=uuid; other=x")
        assertEquals("abc%3A1", session.cookies["sessionid"])
        assertEquals("tok", session.cookies["csrftoken"])
        assertEquals("9", session.cookies["ds_user_id"])
        assertTrue(session.hasCookies)
        assertTrue(session.hasDirectAuth)
    }

    @Test
    fun incompleteCookiesAreNoSession() {
        assertFalse(ThreadsSession("sessionid=x").hasCookies)
        assertFalse(ThreadsSession("sessionid=x; csrftoken=; ds_user_id=1; mid=m; ig_did=g").hasCookies)
        assertEquals(mapOf("spaced" to "out"), ThreadsSession.parseCookieHeader("=nameless; novalue; ; spaced = out "))
    }

    @Test
    fun aBearerIsAnIgt2TokenWithOrWithoutItsPrefix() {
        assertEquals("IGT:2:abc", ThreadsSession.normaliseBearer("IGT:2:abc"))
        assertEquals("IGT:2:abc", ThreadsSession.normaliseBearer(" bearer IGT:2:abc "))
        assertNull(ThreadsSession.normaliseBearer("not-a-token"))
        assertNull(ThreadsSession.normaliseBearer("   "))
        assertTrue(ThreadsSession(bearer = "Bearer IGT:2:abc").hasDirectAuth)
        assertFalse(ThreadsSession().hasDirectAuth)
    }

    @Test
    fun theShippedValuesAreXtas() {
        val shipped = ThreadsRepairs()
        assertEquals("6232751443445612", shipped.guestDocId)
        assertEquals("BarcelonaProfileThreadsTabQuery", shipped.guestQueryName)
        assertEquals("238260118697367", shipped.appId)
        assertEquals("129477", shipped.asbdId)
        assertEquals("Barcelona 289.0.0.77.109 Android", shipped.appUserAgent)
        assertTrue(shipped.webUserAgent.startsWith("Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X)"))
        assertTrue(shipped.webUserAgent.endsWith("Safari/604.1"))
    }

    @Test
    fun aPublishedRepairIsTakenOnlyWhenItFitsItsSlot() {
        val repaired = ThreadsRepairs.published(
            mapOf(
                "threads.guest_doc_id" to "1111111111111111",
                "threads.guest_query_name" to "Barcelona Query; drop",
                "threads.web_user_agent" to "Repaired/2.0",
                "threads.app_user_agent" to "XTA\r\nCookie: stolen=1",
                "threads.host" to "https://evil.example",
            ),
        )
        assertEquals("1111111111111111", repaired.guestDocId)
        assertEquals("Repaired/2.0", repaired.webUserAgent)
        assertEquals(ThreadsRepairs().guestQueryName, repaired.guestQueryName)
        assertEquals(ThreadsRepairs().appUserAgent, repaired.appUserAgent, "a header is one line of printable ASCII")
        assertEquals(ThreadsRepairs(), ThreadsRepairs.published(emptyMap()), "a withdrawn repair goes back to the shipped value")
    }
}
