package com.aimdi.xtanative.ui.home

import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavRowLookTest {
    @Test
    fun theSelectedRowIsLitStrongerThanOneUnderThePointer() {
        assertTrue(NavRowLook.fillAlpha(selected = true, hovered = false) > NavRowLook.fillAlpha(selected = false, hovered = true))
        assertTrue(NavRowLook.fillAlpha(selected = true, hovered = true) > NavRowLook.fillAlpha(selected = true, hovered = false))
        assertEquals(0f, NavRowLook.fillAlpha(selected = false, hovered = false))
    }

    @Test
    fun onADarkPageThePointerWashesMoreYetTheSelectedRowStaysStrongest() {
        val darkHover = NavRowLook.fillAlpha(selected = false, hovered = true, dark = true)
        assertTrue(darkHover > NavRowLook.fillAlpha(selected = false, hovered = true, dark = false))
        assertTrue(NavRowLook.fillAlpha(selected = true, hovered = false, dark = true) > darkHover)
        assertEquals(NavRowLook.fillAlpha(selected = true, hovered = true), NavRowLook.fillAlpha(selected = true, hovered = true, dark = true))
    }

    @Test
    fun aFocusIsARingOnlyWhereTheKeyboardMovedIt() {
        assertTrue(NavRowLook.ring(focused = true, keyboard = true))
        assertFalse(NavRowLook.ring(focused = true, keyboard = false), "a click focuses too")
        assertFalse(NavRowLook.ring(focused = false, keyboard = true))
    }

    @Test
    fun theSelectedLabelIsBold() {
        assertEquals(FontWeight.Bold, NavRowLook.weight(selected = true))
        assertEquals(FontWeight.Normal, NavRowLook.weight(selected = false))
    }
}
