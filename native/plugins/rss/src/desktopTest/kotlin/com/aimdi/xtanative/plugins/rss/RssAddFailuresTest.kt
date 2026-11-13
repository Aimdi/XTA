package com.aimdi.xtanative.plugins.rss

import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.rss.Rss
import com.aimdi.xtanative.networks.rss.RssException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Add feed says why an address was not followed when the reading layer knows. */
class RssAddFailuresTest {
    @Test
    fun anAddressOrAMissingFeedIsNamedAFailedReadIsLeftToTheNetworksWords() {
        assertEquals(Strings.add_not_a_url, RssAddFailures.reasonOf(RssException(RssException.Reason.NOT_A_URL, "x")))
        assertEquals(Strings.add_no_feed, RssAddFailures.reasonOf(RssException(RssException.Reason.NO_FEED, "x")))
        assertNull(RssAddFailures.reasonOf(NetworkRequestException(Rss.NETWORK, 403, "HTTP 403")))
        assertNull(RssAddFailures.reasonOf(IOException("offline")))
    }
}
