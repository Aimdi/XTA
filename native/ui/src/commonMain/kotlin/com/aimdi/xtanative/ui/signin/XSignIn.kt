package com.aimdi.xtanative.ui.signin

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XClient

/**
 * Signing in to X as XTA does (x_session.dart, login_webview.dart,
 * x_cookie_login.dart): the session X's own login page leaves in the web
 * view's cookies, or, on the desktop, the two cookies copied from a browser,
 * kept as XTA keeps an account. The session is only ever used to read.
 */
object XSignIn {
    /** X's own login page, which the web view opens. */
    const val LOGIN_PAGE = "https://x.com/i/flow/login"

    /** Where the desktop's browser signs in. */
    const val BROWSER_LOGIN = "https://x.com/login"

    /** Reaching it means X has signed the reader in. */
    const val HOME = "https://x.com/home"

    /**
     * The web view's user agent, exactly as XTA sets it: its user-agent
     * headers written out as one string, which X's login page has always
     * taken from XTA.
     */
    const val USER_AGENT =
        "{user-agent: Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.3, " +
            "Pragma: no-cache, Cache-Control: no-cache}"

    /** Reads the signed-in handle off X's home page, which fills it in after the address changed. */
    const val SCREEN_NAME_SCRIPT = "document.documentElement.outerHTML.match(/\"screen_name\":\"([^\"]+)\"/)?.[1] ?? '';"

    /** How often, and how far apart, the home page is asked for the handle before the sign-in waits for the next page. */
    const val SCREEN_NAME_TRIES = 20
    const val SCREEN_NAME_WAIT_MILLIS = 500L

    /** The cookies XTA keeps of a session: the rest are X's tracking and preferences. */
    private val KEPT = setOf("guest_id", "gt", "att", "auth_token", "ct0")

    /**
     * A `Cookie` header's pairs, as Android's cookie manager writes them
     * (`a=b; c=d`), in its order; a quoted value without its quotes, as XTA's
     * cookie manager reads one.
     */
    fun cookiesOf(header: String?): List<Pair<String, String>> = header.orEmpty().split(';').mapNotNull { part ->
        val at = part.indexOf('=')
        if (at <= 0) null else part.substring(0, at).trim() to part.substring(at + 1).trim().removeSurrounding("\"")
    }.filter { it.first.isNotEmpty() }

    /**
     * The `auth_token` and `ct0` cookies in [text]: a browser's `Cookie`
     * header, or the two pasted one per line; null unless both are there.
     */
    fun pastedCookies(text: String): List<Pair<String, String>>? {
        val pairs = text.split(';', '\n').mapNotNull { part ->
            val at = part.indexOf('=')
            if (at < 0) null else part.substring(0, at).trim() to part.substring(at + 1).trim()
        }.toMap()
        val authToken = pairs["auth_token"].orEmpty()
        val ct0 = pairs["ct0"].orEmpty()
        return if (authToken.isEmpty() || ct0.isEmpty()) null else listOf("auth_token" to authToken, "ct0" to ct0)
    }

    /** A handle typed with or without its `@`. */
    fun screenNameOf(typed: String): String = typed.trim().removePrefix("@")

    /** What the web view answered the handle script: a JSON string, its quotes left off; empty for none. */
    fun screenNameIn(scriptResult: String?): String = scriptResult.orEmpty().replace("\"", "").takeUnless { it == "null" }.orEmpty()

    /**
     * The account [cookies] sign in as [screenName], with the headers XTA
     * stores (`Cookie`, the bearer and the CSRF token, which is the `ct0`
     * and the account's id); null without a `ct0`.
     */
    fun account(cookies: List<Pair<String, String>>, screenName: String): XAccount? {
        val ct0 = cookies.firstOrNull { it.first == "ct0" }?.second?.takeIf { it.isNotEmpty() } ?: return null
        val cookie = cookies.filter { it.first in KEPT }.joinToString(";") { (name, value) -> "$name=$value" }
        val headers = linkedMapOf("Cookie" to cookie, "authorization" to XClient.BASE_HEADERS.getValue("authorization"), "x-csrf-token" to ct0)
        return XAccount(ct0, screenName, headers)
    }

    /** [session] holding [account]: in place of the session of the same id, signed in again, else after the others. */
    fun signedIn(session: Session, account: XAccount): Session =
        if (session.accounts.any { it.id == account.id }) {
            session.copy(accounts = session.accounts.map { if (it.id == account.id) account else it })
        } else {
            session.copy(accounts = session.accounts + account)
        }
}
