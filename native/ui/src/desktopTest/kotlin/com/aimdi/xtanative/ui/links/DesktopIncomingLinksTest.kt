package com.aimdi.xtanative.ui.links

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopIncomingLinksTest {
    @Test
    fun aLinkJustSentToTheBrowserIsNotTakenBack() {
        SentOutside.sent("https://x.com/i/communities/1", now = 1_000)
        assertFalse(SentOutside.cameBack("https://x.com/jack", now = 2_000))
        assertTrue(SentOutside.cameBack("https://x.com/i/communities/1", now = 2_000))
        assertFalse(SentOutside.cameBack("https://x.com/i/communities/1", now = 3_000), "once: the reader may open it again")
        SentOutside.sent("https://x.com/a", now = 10_000)
        assertFalse(SentOutside.cameBack("https://x.com/a", now = 30_000), "long after, it is the reader's again")
    }
}
