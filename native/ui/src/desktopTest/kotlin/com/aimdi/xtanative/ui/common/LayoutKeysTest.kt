package com.aimdi.xtanative.ui.common

import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What AWT reported on Xvfb with setxkbmap us, fr, de and ru: key code, extended code, X key code. */
class LayoutKeysTest {
    private fun unicode(c: Char) = 0x01000000 + c.code

    @Test
    fun usKeysStayThemselves() {
        assertEquals(KeyEvent.VK_1, LayoutKeys.awtCode(KeyEvent.VK_1, KeyEvent.VK_1, 10))
        assertEquals(KeyEvent.VK_J, LayoutKeys.awtCode(KeyEvent.VK_J, KeyEvent.VK_J, 44))
        assertEquals(KeyEvent.VK_COMMA, LayoutKeys.awtCode(KeyEvent.VK_COMMA, KeyEvent.VK_COMMA, 59))
        assertEquals(KeyEvent.VK_MINUS, LayoutKeys.awtCode(KeyEvent.VK_MINUS, KeyEvent.VK_MINUS, 20))
    }

    @Test
    fun frenchDigitRowIsTheDigits() {
        assertEquals(KeyEvent.VK_1, LayoutKeys.awtCode(KeyEvent.VK_AMPERSAND, 0x96, 10))
        assertEquals(KeyEvent.VK_2, LayoutKeys.awtCode(KeyEvent.VK_UNDEFINED, unicode('é'), 11))
        assertEquals(KeyEvent.VK_3, LayoutKeys.awtCode(KeyEvent.VK_QUOTEDBL, KeyEvent.VK_QUOTEDBL, 12))
        assertEquals(KeyEvent.VK_4, LayoutKeys.awtCode(KeyEvent.VK_QUOTE, KeyEvent.VK_QUOTE, 13))
        assertEquals(KeyEvent.VK_5, LayoutKeys.awtCode(KeyEvent.VK_LEFT_PARENTHESIS, KeyEvent.VK_LEFT_PARENTHESIS, 14))
        assertEquals(KeyEvent.VK_8, LayoutKeys.awtCode(KeyEvent.VK_UNDERSCORE, KeyEvent.VK_UNDERSCORE, 17))
        assertEquals(KeyEvent.VK_0, LayoutKeys.awtCode(KeyEvent.VK_UNDEFINED, unicode('à'), 19))
    }

    @Test
    fun frenchLabelledKeysKeepTheirLabel() {
        // The 6 key types -, which is the text size's minus there, as in GTK; the a and w keys are A and W.
        assertEquals(KeyEvent.VK_MINUS, LayoutKeys.awtCode(KeyEvent.VK_MINUS, KeyEvent.VK_MINUS, 15))
        assertEquals(KeyEvent.VK_A, LayoutKeys.awtCode(KeyEvent.VK_A, KeyEvent.VK_A, 24))
        assertEquals(KeyEvent.VK_W, LayoutKeys.awtCode(KeyEvent.VK_W, KeyEvent.VK_W, 52))
        // The comma sits on the US m.
        assertEquals(KeyEvent.VK_COMMA, LayoutKeys.awtCode(KeyEvent.VK_COMMA, KeyEvent.VK_COMMA, 58))
        // ) on the US minus key stays ), and ; on the US comma stays ;.
        assertEquals(KeyEvent.VK_RIGHT_PARENTHESIS, LayoutKeys.awtCode(KeyEvent.VK_RIGHT_PARENTHESIS, 0x20a, 20))
        assertEquals(KeyEvent.VK_SEMICOLON, LayoutKeys.awtCode(KeyEvent.VK_SEMICOLON, KeyEvent.VK_SEMICOLON, 59))
    }

    @Test
    fun germanKeysGoByLabel() {
        assertEquals(KeyEvent.VK_Y, LayoutKeys.awtCode(KeyEvent.VK_Y, KeyEvent.VK_Y, 52))
        assertEquals(KeyEvent.VK_Z, LayoutKeys.awtCode(KeyEvent.VK_Z, KeyEvent.VK_Z, 29))
        // ß is a Latin letter on the US minus key: not taken for minus. Umlauts sit beside the letters, not on them.
        assertEquals(KeyEvent.VK_UNDEFINED, LayoutKeys.awtCode(KeyEvent.VK_UNDEFINED, unicode('ß'), 20))
        assertEquals(KeyEvent.VK_UNDEFINED, LayoutKeys.awtCode(KeyEvent.VK_UNDEFINED, unicode('ö'), 47))
    }

    @Test
    fun cyrillicLettersAreTheUsKeysThere() {
        assertEquals(KeyEvent.VK_J, LayoutKeys.awtCode(KeyEvent.VK_UNDEFINED, unicode('о'), 44))
        assertEquals(KeyEvent.VK_Z, LayoutKeys.awtCode(KeyEvent.VK_UNDEFINED, unicode('я'), 52))
        assertEquals(KeyEvent.VK_W, LayoutKeys.awtCode(KeyEvent.VK_UNDEFINED, unicode('ц'), 25))
        assertEquals(KeyEvent.VK_COMMA, LayoutKeys.awtCode(KeyEvent.VK_UNDEFINED, unicode('б'), 59))
        assertEquals(KeyEvent.VK_PERIOD, LayoutKeys.awtCode(KeyEvent.VK_UNDEFINED, unicode('ю'), 60))
        // Russian digits are digits; its . on the US slash key stays a period.
        assertEquals(KeyEvent.VK_1, LayoutKeys.awtCode(KeyEvent.VK_1, KeyEvent.VK_1, 10))
        assertEquals(KeyEvent.VK_PERIOD, LayoutKeys.awtCode(KeyEvent.VK_PERIOD, KeyEvent.VK_PERIOD, 61))
    }

    @Test
    fun dvorakPunctuationOnLetterPlacesStays() {
        // ' on the US q and ; on the US z: never Ctrl+Q (quit) nor Ctrl+Z.
        assertEquals(KeyEvent.VK_QUOTE, LayoutKeys.awtCode(KeyEvent.VK_QUOTE, KeyEvent.VK_QUOTE, 24))
        assertEquals(KeyEvent.VK_SEMICOLON, LayoutKeys.awtCode(KeyEvent.VK_SEMICOLON, KeyEvent.VK_SEMICOLON, 52))
        assertEquals(KeyEvent.VK_UNDEFINED, LayoutKeys.awtCode(KeyEvent.VK_UNDEFINED, unicode('ı'), 31))
    }

    @Test
    fun otherKeysAndUnknownPlacesStay() {
        assertEquals(KeyEvent.VK_UNDEFINED, LayoutKeys.awtCode(KeyEvent.VK_UNDEFINED, unicode('о'), null))
        assertEquals(KeyEvent.VK_F5, LayoutKeys.awtCode(KeyEvent.VK_F5, KeyEvent.VK_F5, 71))
        assertEquals(KeyEvent.VK_NUMPAD1, LayoutKeys.awtCode(KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD1, 87))
        assertEquals(KeyEvent.VK_ESCAPE, LayoutKeys.awtCode(KeyEvent.VK_ESCAPE, KeyEvent.VK_ESCAPE, 9))
    }

    @Test
    fun byLabelIsLatinLettersDigitsAndShortcutPunctuation() {
        assertTrue(LayoutKeys.byLabel(KeyEvent.VK_Q))
        assertTrue(LayoutKeys.byLabel(KeyEvent.VK_7))
        assertTrue(LayoutKeys.byLabel(KeyEvent.VK_EQUALS))
        assertFalse(LayoutKeys.byLabel(KeyEvent.VK_AMPERSAND))
        assertFalse(LayoutKeys.byLabel(KeyEvent.VK_UNDEFINED))
    }

    @Test
    fun ctrlWithANonLatinLetterTypesNothing() {
        assertTrue(LayoutKeys.ctrlTypesNothing('ц'.code, ctrl = true, alt = false))
        assertFalse(LayoutKeys.ctrlTypesNothing('ц'.code, ctrl = false, alt = false))
        assertTrue(LayoutKeys.ctrlTypesNothing('é'.code, ctrl = true, alt = false))
        // AltGr comes as Ctrl+Alt on some desktops; ASCII typed with Ctrl is left to the field as before.
        assertFalse(LayoutKeys.ctrlTypesNothing('ц'.code, ctrl = true, alt = true))
        assertFalse(LayoutKeys.ctrlTypesNothing('1'.code, ctrl = true, alt = false))
    }
}
