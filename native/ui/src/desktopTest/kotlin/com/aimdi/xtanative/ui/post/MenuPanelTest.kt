package com.aimdi.xtanative.ui.post

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** The desktop's menu panels share one colour: the reply order menu's is the post menu's. */
class MenuPanelTest {
    @Test
    fun aContainerThatIsThePageSurfaceIsLiftedAsThePostMenusSurfaceLiftsIt() {
        val light = lightColorScheme(surface = Color.White, surfaceContainer = Color.White, primary = Color(0xFF1D9BF0))
        assertEquals(light.surfaceColorAtElevation(MENU_ELEVATION), light.menuPanel())
        assertNotEquals(Color.White, light.menuPanel())
    }

    @Test
    fun anInsetContainerIsKeptAsItIs() {
        val black = lightColorScheme(surface = Color.Black, surfaceContainer = Color(0xFF16181C))
        assertEquals(Color(0xFF16181C), black.menuPanel())
    }
}
