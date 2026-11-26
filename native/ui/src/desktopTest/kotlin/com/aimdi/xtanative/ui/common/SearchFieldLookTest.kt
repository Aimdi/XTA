package com.aimdi.xtanative.ui.common

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchFieldLookTest {
    @Test
    fun theFieldIsADesktopFieldsHeightThereAndKeepsATouchSizeOnAPhone() {
        assertEquals(40.dp, SearchFieldLook.height(desktop = true))
        assertEquals(48.dp, SearchFieldLook.height(desktop = false))
    }
}
