package com.aimdi.xtanative.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CoreInfoTest {
    @Test
    fun namesItself() {
        assertEquals("xta-native-core", CoreInfo.NAME)
    }
}
