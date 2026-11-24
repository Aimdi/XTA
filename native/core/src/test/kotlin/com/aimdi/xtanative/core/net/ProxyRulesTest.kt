package com.aimdi.xtanative.core.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProxyRulesTest {
    @Test
    fun noProxyNamesCoverTheirSubdomainsButNotLookalikes() {
        val rules = NoProxyRules.parse("example.com, .corp.test *.wild.test intranet:8080")
        assertTrue(rules.matches("example.com"))
        assertTrue(rules.matches("a.example.com"))
        assertTrue(rules.matches("EXAMPLE.com."))
        assertFalse(rules.matches("badexample.com"))
        assertTrue(rules.matches("corp.test"))
        assertTrue(rules.matches("x.corp.test"))
        assertTrue(rules.matches("y.wild.test"))
        assertTrue(rules.matches("intranet"), "the port after a name is ignored")
        assertFalse(rules.matches("other.test"))
    }

    @Test
    fun noProxyTakesAddressesAndBlocks() {
        val rules = NoProxyRules.parse("10.0.0.0/8,192.168.1.7, fd00::/8, [2001:db8::1]")
        assertTrue(rules.matches("10.200.3.4"))
        assertFalse(rules.matches("11.0.0.1"))
        assertTrue(rules.matches("192.168.1.7"))
        assertFalse(rules.matches("192.168.1.8"))
        assertTrue(rules.matches("[fd12::5]"))
        assertTrue(rules.matches("2001:db8::1"))
        assertFalse(rules.matches("2001:db8::2"))
        assertFalse(rules.matches("10.example"), "a name is never read as an address")
    }

    @Test
    fun aStarAloneTakesEveryHostAndNothingTakesNone() {
        assertTrue(NoProxyRules.parse("*").matches("anything.test"))
        assertFalse(NoProxyRules.parse("").matches("anything.test"))
        assertFalse(NoProxyRules.parse(null).matches("anything.test"))
    }

    @Test
    fun proxyUrlsReadAsCurlReadsThem() {
        assertEquals(ProxyRoute.Via(ProxyKind.HTTP, "proxy.test", 3128), ProxyUrl.parse("http://proxy.test:3128"))
        assertEquals(ProxyRoute.Via(ProxyKind.HTTP, "proxy.test", 1080), ProxyUrl.parse("proxy.test"))
        assertEquals(ProxyRoute.Via(ProxyKind.HTTP, "proxy.test", 8080), ProxyUrl.parse(" proxy.test:8080 "))
        assertEquals(
            ProxyRoute.Via(ProxyKind.SOCKS, "10.0.0.1", 1080, ProxyCredentials("me", "p@ss+:w")),
            ProxyUrl.parse("socks5h://me:p%40ss+%3Aw@10.0.0.1:1080"),
        )
        assertEquals(ProxyRoute.Via(ProxyKind.SOCKS, "::1", 9050), ProxyUrl.parse("socks5://[::1]:9050"))
        assertEquals(ProxyKind.SOCKS, (ProxyUrl.parse("socks://p.test:1") as ProxyRoute.Via).kind)
        assertNull(ProxyUrl.parse("  "))
    }

    @Test
    fun proxiesTheAppCannotSpeakAreUnusableNotSkipped() {
        listOf("https://proxy.test:443", "socks4://proxy.test:1080", "socks4a://p.test", "ftp://p.test", "http://:80", "http://p.test:99999")
            .forEach { assertEquals(ProxyRoute.Unusable, ProxyUrl.parse(it), it) }
    }

    @Test
    fun credentialsNeverPrintTheirPassword() {
        assertFalse("hunter2" in ProxyCredentials("me", "hunter2").toString())
    }

    @Test
    fun theEnvironmentIsReadInCurlsOrder() {
        val env = EnvProxyPolicy(
            mapOf(
                "http_proxy" to "http://plain.test:1",
                "HTTP_PROXY" to "http://cgi-injected.test:2",
                "HTTPS_PROXY" to "http://upper.test:3",
                "https_proxy" to "http://lower.test:4",
                "no_proxy" to "skip.test",
                "NO_PROXY" to "ignored.test",
            ),
        )
        assertEquals(ProxyRoute.Via(ProxyKind.HTTP, "plain.test", 1), env.route("http", "a.test", 80))
        assertEquals(ProxyRoute.Via(ProxyKind.HTTP, "lower.test", 4), env.route("https", "a.test", 443))
        assertEquals(ProxyRoute.Direct, env.route("https", "www.skip.test", 443))
        assertEquals(ProxyRoute.Via(ProxyKind.HTTP, "lower.test", 4), env.route("https", "ignored.test", 443))
        assertTrue(env.isSet)
    }

    @Test
    fun upperCaseHttpProxyIsNeverReadButAllProxyIs() {
        val upperOnly = EnvProxyPolicy(mapOf("HTTP_PROXY" to "http://cgi.test:2"))
        assertEquals(ProxyRoute.Direct, upperOnly.route("http", "a.test", 80))
        assertFalse(upperOnly.isSet)
        val all = EnvProxyPolicy(mapOf("ALL_PROXY" to "socks5://s.test:1080", "https_proxy" to " "))
        assertEquals(ProxyRoute.Via(ProxyKind.SOCKS, "s.test", 1080), all.route("http", "a.test", 80))
        assertEquals(ProxyRoute.Via(ProxyKind.SOCKS, "s.test", 1080), all.route("https", "a.test", 443), "a blank value is no value")
        assertEquals(ProxyRoute.Unusable, EnvProxyPolicy(mapOf("https_proxy" to "https://tls.test")).route("https", "a.test", 443))
    }

    @Test
    fun loopbackIsThisMachine() {
        listOf("localhost", "LOCALHOST.", "app.localhost", "127.0.0.1", "127.4.5.6", "::1", "[::1]").forEach { assertTrue(AppProxy.isLoopback(it), it) }
        listOf("localhost.example", "128.0.0.1", "10.0.0.1", "example.com", "::2").forEach { assertFalse(AppProxy.isLoopback(it), it) }
    }

    @Test
    fun aManualProxySkipsItsBypassAndLoopbackAlwaysGoesStraight() {
        val via = ProxyRoute.Via(ProxyKind.HTTP, "proxy.test", 3128)
        try {
            AppProxy.use(ManualProxyPolicy(via, NoProxyRules.parse("lan.test")))
            assertEquals(via, AppProxy.route("https://x.com/home"))
            assertEquals(ProxyRoute.Direct, AppProxy.route("http://nas.lan.test/"))
            assertEquals(ProxyRoute.Direct, AppProxy.route("http://127.0.0.1:8805/"))
            assertEquals(ProxyRoute.Direct, AppProxy.route("/home/me/video.mp4"), "a file is no request")
            AppProxy.use { _, _, _ -> error("broken") }
            assertEquals(ProxyRoute.Unusable, AppProxy.route("https://x.com/"), "a failing policy fails closed")
        } finally {
            AppProxy.use(AppProxy.PLATFORM)
        }
    }
}
