package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What a pasted session is, before Meta is asked. Every value here is made up. */
class ThreadsSessionCheckTest {
    private val whole = "sessionid=s1; csrftoken=c1; ds_user_id=7; mid=m1; ig_did=d1"

    @Test
    fun aWholeHeaderKeepsOnlyTheCookiesTheApiSends() {
        val pasted = "Cookie: rur=x; $whole; ps_l=1"
        assertEquals(ThreadsCookiePaste.Complete(whole), ThreadsSessionCheck.cookies(pasted))
        assertEquals(whole, ThreadsSessionCheck.kept(pasted))
        assertTrue(ThreadsSession(ThreadsSessionCheck.kept(pasted)).hasCookies)
    }

    @Test
    fun aCookieTablesRowsReadAsNamesAndValues() {
        val table = "sessionid\ts1\t.threads.com\t/\ncsrftoken\tc1\t.threads.com\nds_user_id\t7\nmid\tm1\nig_did\td1\n"
        assertEquals(ThreadsCookiePaste.Complete(whole), ThreadsSessionCheck.cookies(table))
    }

    @Test
    fun whatIsWrongIsNamed() {
        assertEquals(ThreadsCookiePaste.Empty, ThreadsSessionCheck.cookies("  "))
        assertEquals(ThreadsCookiePaste.Bearer, ThreadsSessionCheck.cookies("Bearer IGT:2:abc"))
        assertEquals(ThreadsCookiePaste.NotCookies, ThreadsSessionCheck.cookies("just some words"))
        assertEquals(ThreadsCookiePaste.Missing(listOf("sessionid", "ds_user_id")), ThreadsSessionCheck.cookies("csrftoken=c1; mid=m1; ig_did=d1"))
        assertEquals("csrftoken=c1", ThreadsSessionCheck.kept(" csrftoken=c1 "), "an incomplete paste is kept as typed")
    }

    @Test
    fun aBearerIsAnIgt2Token() {
        assertFalse(ThreadsSessionCheck.badBearer(""))
        assertFalse(ThreadsSessionCheck.badBearer("Bearer IGT:2:abc"))
        assertTrue(ThreadsSessionCheck.badBearer("abc123"))
    }
}
