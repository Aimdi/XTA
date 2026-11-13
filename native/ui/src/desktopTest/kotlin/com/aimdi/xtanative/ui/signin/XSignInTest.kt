package com.aimdi.xtanative.ui.signin

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** XTA's x_session.dart and x_cookie_login.dart (test/x_cookie_login_test.dart), with made-up cookies. */
class XSignInTest {
    @Test
    fun theWebViewsCookiesAreReadInTheirOrder() {
        val cookies = XSignIn.cookiesOf("guest_id=\"v1%3Amade-up\"; lang=en; ct0=csrf-made-up; auth_token=token-made-up")
        assertEquals(listOf("guest_id" to "v1%3Amade-up", "lang" to "en", "ct0" to "csrf-made-up", "auth_token" to "token-made-up"), cookies)
        assertEquals(emptyList(), XSignIn.cookiesOf(null))
        assertEquals(emptyList(), XSignIn.cookiesOf("=nameless; ;"))
    }

    @Test
    fun anAccountKeepsXtasCookiesAndHeaders() {
        val cookies = XSignIn.cookiesOf("guest_id=g; personalization_id=p; gt=1; att=a; auth_token=t; ct0=c")
        val account = XSignIn.account(cookies, "reader")!!
        assertEquals("c", account.id, "the ct0 is the account's id, as in XTA")
        assertEquals("reader", account.screenName)
        assertEquals("guest_id=g;gt=1;att=a;auth_token=t;ct0=c", account.headers["Cookie"])
        assertEquals("c", account.headers["x-csrf-token"])
        assertEquals(XClient.BASE_HEADERS["authorization"], account.headers["authorization"])
        assertEquals("guest_id=g;gt=1;att=a;auth_token=t;ct0=c", account.cookie)
    }

    @Test
    fun nothingIsKeptWithoutACt0(): Unit = assertNull(XSignIn.account(XSignIn.cookiesOf("auth_token=t; ct0="), "reader"))

    @Test
    fun theDesktopTakesACookieHeaderOrTwoLines() {
        val pair = listOf("auth_token" to "t", "ct0" to "c")
        assertEquals(pair, XSignIn.pastedCookies("guest_id=g; auth_token=t; ct0=c; lang=en"))
        assertEquals(pair, XSignIn.pastedCookies("auth_token=t\nct0=c\n"))
        assertEquals(pair, XSignIn.pastedCookies(" ct0 = c ;auth_token= t"))
        assertNull(XSignIn.pastedCookies("auth_token=t"))
        assertNull(XSignIn.pastedCookies("auth_token=; ct0=c"))
        assertNull(XSignIn.pastedCookies(""))
    }

    @Test
    fun aHandleIsTypedWithOrWithoutItsAt() {
        assertEquals("reader", XSignIn.screenNameOf("  @reader "))
        assertEquals("reader", XSignIn.screenNameOf("reader"))
        assertEquals("", XSignIn.screenNameOf(" @ "))
    }

    @Test
    fun theHomePagesHandleComesAsAJsonString() {
        assertEquals("reader", XSignIn.screenNameIn("\"reader\""))
        assertEquals("", XSignIn.screenNameIn("\"\""))
        assertEquals("", XSignIn.screenNameIn("null"))
        assertEquals("", XSignIn.screenNameIn(null))
    }

    @Test
    fun signingInAgainReplacesTheSessionOfTheSameId() {
        val first = XAccount("a", "first", mapOf("Cookie" to "ct0=a"))
        val other = XAccount("b", "other", mapOf("Cookie" to "ct0=b"))
        val again = XAccount("a", "renamed", mapOf("Cookie" to "ct0=a; auth_token=new"))
        val session = XSignIn.signedIn(XSignIn.signedIn(Session(), first), other)
        assertEquals(listOf("a", "b"), session.accounts.map { it.id })
        assertEquals(listOf(again, other), XSignIn.signedIn(session, again).accounts, "in its place, not added twice")
    }
}
