package com.aimdi.xtanative.ui.theme

import androidx.compose.ui.graphics.Color
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
    fun theSystemAccentFollowsTheDesktopAndFallsBackToBlue() {
        val system = settings(ThemeBackground.DIM, ThemeAccent.SYSTEM)
        assertEquals(0xFF3584E4, XLook.tokens(system, systemDark = false, systemAccent = 0xFF3584E4).accent)
        assertEquals(0xFF3584E4, XLook.tokens(settings(ThemeBackground.SYSTEM, ThemeAccent.SYSTEM), systemDark = false, systemAccent = 0xFF3584E4).accent, "light half too")
        assertEquals(ThemeAccent.BLUE.argb, XLook.tokens(system, systemDark = false, systemAccent = null).accent)
        // A named accent ignores the desktop's.
        assertEquals(ThemeAccent.PINK.argb, XLook.tokens(settings(ThemeBackground.DIM, ThemeAccent.PINK), systemDark = false, systemAccent = 0xFF3584E4).accent)
        // A desktop accent that does not read on the page is corrected like any other.
        val scheme = XLook.scheme(XLook.tokens(settings(ThemeBackground.LIGHT, ThemeAccent.SYSTEM), systemDark = false, systemAccent = 0xFFFFFF80))
        assertTrue(Contrast.ratio(scheme.primary, 0xFFFFFFFF) >= 4.5)
        assertEquals(0xFFFFFF80, scheme.accentFill)
    }

    @Test
    fun systemIsOfferedOnTheDesktopOnly() {
        assertEquals(ThemeAccent.SYSTEM, XLook.offeredAccents(desktop = true, selected = ThemeAccent.BLUE).first())
        assertEquals(ThemeAccent.entries - ThemeAccent.SYSTEM, XLook.offeredAccents(desktop = false, selected = ThemeAccent.BLUE))
        assertEquals(ThemeAccent.entries, XLook.offeredAccents(desktop = false, selected = ThemeAccent.SYSTEM), "kept while chosen")
    }

    @Test
    fun systemsDiscShowsEveryAccentWhereTheDesktopGivesNone() {
        assertEquals((ThemeAccent.entries - ThemeAccent.SYSTEM).map { it.argb }, XLook.accentSwatch(ThemeAccent.SYSTEM, systemAccent = null))
        assertEquals(listOf(0xFF3584E4), XLook.accentSwatch(ThemeAccent.SYSTEM, systemAccent = 0xFF3584E4), "the desktop's own accent")
        assertEquals(listOf(ThemeAccent.BLUE.argb), XLook.accentSwatch(ThemeAccent.BLUE, systemAccent = null))
        assertEquals(listOf(ThemeAccent.PINK.argb), XLook.accentSwatch(ThemeAccent.PINK, systemAccent = 0xFF3584E4))
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

    @Test
    fun aSheetsScrimDarkensThePageInEveryThemeAsADialogsDoes() {
        for (background in listOf(ThemeBackground.LIGHT, ThemeBackground.DIM, ThemeBackground.LIGHTS_OUT)) {
            val scheme = colorSchemeOf(XLook.scheme(XLook.tokens(settings(background), systemDark = true)))
            assertEquals(Color.Black, scheme.scrim, background.name)
        }
    }

    @Test
    fun theOutlineOfAControlReadsOnEveryBackgroundAndAccent() {
        for (background in ThemeBackground.entries) for (accent in ThemeAccent.entries) for (dark in listOf(false, true)) {
            val scheme = XLook.scheme(XLook.tokens(settings(background, accent), systemDark = dark))
            val where = "$background $accent dark=$dark"
            assertTrue(Contrast.ratio(scheme.outline, scheme.tokens.background) >= XLook.NON_TEXT, "outline on the page, $where")
            assertTrue(Contrast.ratio(scheme.outline, scheme.floating) >= XLook.NON_TEXT, "outline on a dialog, $where")
            assertTrue(Contrast.ratio(scheme.onSwitch, scheme.primary) >= XLook.NON_TEXT, "on knob on the accent, $where")
        }
    }

    @Test
    fun theOutlineIsNotTheFaintDivider() {
        for (tokens in listOf(XLookTokens.LIGHT, XLookTokens.DIM, XLookTokens.LIGHTS_OUT)) {
            assertNotEquals(tokens.divider, XLook.scheme(tokens).outline)
            assertEquals(tokens.outline, XLook.scheme(tokens).outline, "each background's own outline already reads")
        }
    }

    @Test
    fun anOnSwitchShowsAWhiteKnobOnXsBlue() {
        assertEquals(0xFFFFFFFF, XLook.scheme(XLookTokens.DIM).onSwitch)
        assertEquals(0xFFFFFFFF, XLook.scheme(XLookTokens.LIGHTS_OUT).onSwitch)
        assertEquals(0xFF000000, XLook.scheme(XLook.tokens(settings(ThemeBackground.DIM, ThemeAccent.YELLOW), systemDark = true)).onSwitch)
    }

    @Test
    fun aLoadingPlaceholderIsSeenOnEveryPage() {
        for (tokens in listOf(XLookTokens.LIGHT, XLookTokens.DIM, XLookTokens.LIGHTS_OUT)) {
            val scheme = XLook.scheme(tokens)
            assertTrue(Contrast.ratio(scheme.bone, tokens.background) >= 1.25, "bone on ${tokens.background}")
            assertTrue(Contrast.ratio(scheme.boneHighlight, tokens.background) >= 1.1, "highlight on ${tokens.background}")
            assertTrue(Contrast.ratio(scheme.bone, tokens.background) > Contrast.ratio(scheme.boneHighlight, tokens.background), "the pulse moves")
        }
    }

    @Test
    fun aCardsAndAFieldsHairlineReadsOnThePageAndUnderALitRow() {
        for (background in ThemeBackground.entries) for (accent in ThemeAccent.entries) for (dark in listOf(false, true)) {
            val scheme = XLook.scheme(XLook.tokens(settings(background, accent), systemDark = dark))
            val page = scheme.tokens.background
            val lit = Contrast.alphaBlend(Contrast.withAlpha(scheme.tokens.onBackground, 0.10), page)
            val where = "$background $accent dark=$dark"
            assertTrue(Contrast.ratio(scheme.edge, page) >= XLook.EDGE, "edge on the page, $where")
            assertTrue(Contrast.ratio(scheme.edge, lit) >= XLook.EDGE_WASHED, "edge under a hovered post, $where")
        }
        // Light's own border (#EFF3F4) was 1.1:1 on white.
        assertNotEquals(XLookTokens.LIGHT.border, XLook.scheme(XLookTokens.LIGHT).edge)
    }

    @Test
    fun aSearchFieldsFillAndARaisedControlStandApartFromThePageInEveryTheme() {
        for (tokens in listOf(XLookTokens.LIGHT, XLookTokens.DIM, XLookTokens.LIGHTS_OUT)) {
            val scheme = XLook.scheme(tokens)
            assertTrue(Contrast.ratio(scheme.field, tokens.background) >= 1.1, "field on ${tokens.background}")
            val colors = colorSchemeOf(scheme)
            assertNotEquals(colors.background, colors.surfaceContainerHigh, "raised control on ${tokens.background}")
        }
    }

    @Test
    fun theEdgeIsNotTheDividerOnLight() {
        // Dividers keep X's faint line; only what frames a card or a control is darker.
        val colors = colorSchemeOf(XLook.scheme(XLookTokens.LIGHT))
        assertEquals(Color(XLookTokens.LIGHT.divider), colors.outlineVariant)
    }
}
