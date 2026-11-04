package com.aimdi.xtanative.networks.immich

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reading the address someone pasted, as XTA's immich_client_test does. */
class ImmichServerTest {
    private fun segments(raw: String) = immichBaseUrl(raw)!!.pathSegments.filter { it.isNotEmpty() }

    @Test
    fun aBareLanAddressIsHttpBecauseThatIsWhatThoseServe() {
        val url = immichBaseUrl("192.168.1.10:2283")!!
        assertEquals("http", url.scheme)
        assertEquals("192.168.1.10", url.host)
        assertEquals(2283, url.port)
    }

    @Test
    fun aBareNameIsAssumedToBeReachableProperly() {
        assertEquals("https", immichBaseUrl("photos.example.org")!!.scheme)
    }

    @Test
    fun aSchemeThatWasTypedIsKept() {
        assertEquals("http", immichBaseUrl("http://photos.example.org")!!.scheme)
        assertEquals("https", immichBaseUrl("https://192.168.1.10:2283")!!.scheme)
    }

    @Test
    fun anApiCopiedFromImmichIsDroppedRatherThanDoubled() {
        assertTrue(segments("https://photos.example.org/api").isEmpty())
        assertEquals("https://photos.example.org/api/assets", server("https://photos.example.org/api").endpoint("assets").toString())
    }

    @Test
    fun aReverseProxyPrefixIsKept() {
        assertEquals(listOf("immich"), segments("https://home.example.org/immich/api"))
        assertEquals("https://home.example.org/immich/api/users/me", server("https://home.example.org/immich/api").endpoint("users", "me").toString())
    }

    @Test
    fun aTrailingSlashChangesNothing() {
        assertEquals(immichBaseUrl("https://photos.example.org"), immichBaseUrl("https://photos.example.org/"))
    }

    @Test
    fun aBareLanAddressWithAPathOrATrailingSlashIsStillHttpWhereXtaGuessedHttps() {
        for (raw in listOf("192.168.1.10:2283/", "192.168.1.10:2283/api", "192.168.1.10/immich")) {
            assertEquals("http", immichBaseUrl(raw)!!.scheme, raw)
        }
        assertEquals(immichBaseUrl("192.168.1.10:2283"), immichBaseUrl("192.168.1.10:2283/api/"))
    }

    @Test
    fun nothingUsableIsNullRatherThanAGuess() {
        for (raw in listOf("", "   ", "not a url", "ftp://photos.example.org", "https://[::1]:2283")) {
            assertNull(immichBaseUrl(raw), raw)
        }
    }

    @Test
    fun noAddressOrNoKeyIsNoServerSoNothingCanBeSent() {
        assertNull(ImmichServer.of("", ""))
        assertNull(ImmichServer.of("http://immich.local:2283", "   "))
        assertNull(ImmichServer.of("not a url", "key"))
        assertNotNull(ImmichServer.of(" http://immich.local:2283 ", "key"))
    }
}
