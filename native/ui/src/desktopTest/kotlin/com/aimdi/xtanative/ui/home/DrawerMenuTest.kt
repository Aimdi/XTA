package com.aimdi.xtanative.ui.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawerMenuTest {
    @Test
    fun aDrawerNeverOpenedHoldsNoMenu() {
        assertFalse(DrawerMenu.makes(made = false, opening = false, open = false, moving = false))
    }

    @Test
    fun theMenuIsMadeAsTheDrawerIsAskedToOpenAndStaysMade() {
        assertTrue(DrawerMenu.makes(made = false, opening = true, open = false, moving = false))
        assertTrue(DrawerMenu.makes(made = false, opening = false, open = false, moving = true))
        assertTrue(DrawerMenu.makes(made = false, opening = false, open = true, moving = false))
        assertTrue(DrawerMenu.makes(made = true, opening = false, open = false, moving = false), "closed again, it is kept")
    }
}
