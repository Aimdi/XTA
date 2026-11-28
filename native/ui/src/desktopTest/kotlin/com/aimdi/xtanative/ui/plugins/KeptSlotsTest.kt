package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class KeptSlotsTest {
    @Test
    fun aScreensOnePlaceKeepsItsSlotAndEachOtherPlaceHasItsOwn() {
        assertEquals("pixiv-home@7", KeptSlots.of(null, "pixiv-home", 7), "as before, for every screen with one place")
        assertNotEquals(KeptSlots.of(null, "pixiv-home", 7), KeptSlots.of("page:pixiv", "pixiv-home", 7), "Home's pinned timeline and the plugin's page apart")
        assertNotEquals(KeptSlots.of("page:pixiv", "pixiv-home", 7), KeptSlots.of("page:pixiv", "pixiv-home", 8), "a plugin updated starts anew")
    }
}
