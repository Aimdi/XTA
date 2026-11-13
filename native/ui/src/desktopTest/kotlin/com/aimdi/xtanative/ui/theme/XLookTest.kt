package com.aimdi.xtanative.ui.theme

import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.ThemeAccent
import com.aimdi.xtanative.store.settings.ThemeBackground
import com.aimdi.xtanative.store.settings.ThemePrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class XLookTest {
    private fun settings(background: ThemeBackground, accent: ThemeAccent = ThemeAccent.BLUE) =
        Settings().with(ThemePrefs.background, background).with(ThemePrefs.accent, accent)

    @Test
    fun systemFollowsTheDeviceAndDarkensToLightsOut() {
        val system = settings(ThemeBackground.SYSTEM)
        assertEquals(XLookTokens.LIGHT.background, XLook.tokens(system, systemDark = false).background)
        assertEquals(XLookTokens.LIGHTS_OUT.background, XLook.tokens(system, systemDark = true).background)
        assertEquals(XLookMode.SYSTEM, XLook.modeFor(ThemeBackground.SYSTEM))
    }

    @Test
    fun aNamedBackgroundIgnoresTheDevice() {
        assertEquals(XLookTokens.DIM.background, XLook.tokens(settings(ThemeBackground.DIM), systemDark = false).background)
        assertEquals(XLookTokens.LIGHTS_OUT.background, XLook.tokens(settings(ThemeBackground.LIGHTS_OUT), systemDark = false).background)
        assertEquals(XLookTokens.LIGHT.background, XLook.tokens(settings(ThemeBackground.LIGHT), systemDark = true).background)
    }

    @Test
    fun theAccentTintsEveryBackground() {
        val tokens = XLook.tokens(settings(ThemeBackground.DIM, ThemeAccent.PINK), systemDark = false)
        assertEquals(ThemeAccent.PINK.argb, tokens.accent)
    }

    @Test
    fun anAccentThatDoesNotReadOnThePageIsCorrected() {
        val scheme = XLook.scheme(XLook.tokens(settings(ThemeBackground.LIGHT, ThemeAccent.YELLOW), systemDark = false))
        assertNotEquals(ThemeAccent.YELLOW.argb, scheme.primary)
        assertTrue(Contrast.ratio(scheme.primary, 0xFFFFFFFF) >= 4.5)
        // A fill of the raw accent keeps it, with a foreground that reads on it.
        assertEquals(ThemeAccent.YELLOW.argb, scheme.accentFill)
        assertEquals(0xFF000000, scheme.onAccentFill)
    }

    @Test
    fun lightsOutKeepsThePageBlackAndLiftsWhatFloats() {
        val scheme = XLook.scheme(XLookTokens.LIGHTS_OUT)
        assertTrue(XLook.isLightsOut(XLookTokens.LIGHTS_OUT))
        assertFalse(scheme.isLight)
        assertEquals(0xFF000000, scheme.tokens.card)
        assertNotEquals(0xFF000000, scheme.floating)
        assertTrue(Contrast.luminance(scheme.floating) > Contrast.luminance(scheme.inset))
        assertEquals(scheme.inset, scheme.surfaceContainer)
    }

    @Test
    fun alphaBlendLaysAColourOverAnother() {
        assertEquals(0xFF808080, Contrast.alphaBlend(0x80FFFFFF, 0xFF000000))
        assertEquals(0x80FFFFFF, Contrast.withAlpha(0xFFFFFFFF, 128 / 255.0))
        assertEquals(0xFF123456, Contrast.alphaBlend(0x00FFFFFF, 0xFF123456))
    }

    @Test
    fun swatchesNameTheirPage() {
        assertEquals(null, XLook.swatch(ThemeBackground.SYSTEM))
        assertEquals(0xFF15202B, XLook.swatch(ThemeBackground.DIM))
    }
}
