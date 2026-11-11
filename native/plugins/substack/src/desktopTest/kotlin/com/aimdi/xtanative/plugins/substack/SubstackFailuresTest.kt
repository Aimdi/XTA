package com.aimdi.xtanative.plugins.substack

import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.substack.Substack
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A read Substack's Cloudflare turned away is told as such, with the page to open in the browser. */
class SubstackFailuresTest {
    @Test
    fun onlySubstacksOwn403IsTheBotCheck() {
        assertTrue(SubstackFailures.turnedAway(NetworkRequestException(Substack.NETWORK, 403, "Substack refused this connection")))
        assertFalse(SubstackFailures.turnedAway(NetworkRequestException(Substack.NETWORK, 404, "This is not a Substack publication")))
        assertFalse(SubstackFailures.turnedAway(NetworkRequestException("medium", 403, "no")))
        assertFalse(SubstackFailures.turnedAway(IOException("offline")))
        assertFalse(SubstackFailures.turnedAway(null))
    }

    @Test
    fun aTypedLinkOpensItselfAHandleOrNameItsPublication() {
        assertEquals("https://on.substack.com/p/a-post", SubstackFailures.pageOf(" https://on.substack.com/p/a-post "))
        assertEquals("https://platformer.substack.com/", SubstackFailures.pageOf("@platformer"))
        assertNull(SubstackFailures.pageOf("  "))
    }
}
