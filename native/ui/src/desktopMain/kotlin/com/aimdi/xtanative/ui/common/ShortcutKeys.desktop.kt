package com.aimdi.xtanative.ui.common

import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import java.awt.Toolkit
import java.awt.event.KeyEvent as AwtKeyEvent

internal actual fun shortcutKey(event: KeyEvent): Key {
    val awt = event.awtEventOrNull ?: return event.key
    if (LayoutKeys.byLabel(awt.keyCode)) return event.key
    val code = LayoutKeys.awtCode(awt.keyCode, awt.extendedKeyCode, XKeycodes.of(awt))
    return if (code == awt.keyCode) event.key else Key(code)
}

/**
 * Shortcuts on any keyboard layout, as GTK and the browsers find them. A key
 * whose layout gives a Latin letter or digit, or one of the punctuation keys
 * shortcuts name (+ - = , . /), is that key, wherever it sits (Ctrl+Z on a
 * German keyboard is the key marked Z). Any other key on the digit row is the
 * digit there: the French row (& é " ' ( ..., shifted for the digits) is 1 to 0.
 * A non-Latin letter (Russian, Greek...) is the US keyboard's key where it sits:
 * о is j, я is z, б is the comma. Latin letters and other punctuation stay
 * themselves (German ß, a Dvorak keyboard's ' and ;).
 */
internal object LayoutKeys {
    /** X11's key codes (evdev's plus 8, on Xorg and XWayland alike) of the US keyboard's digit row, to AWT's codes for the digits. */
    private val DIGITS: Map<Int, Int> = "1234567890".withIndex().associate { (i, c) -> 10 + i to c.code }

    /** The same of its letters and the punctuation keys beside them. */
    private val LETTERS: Map<Int, Int> = buildMap {
        "QWERTYUIOP".forEachIndexed { i, c -> put(24 + i, c.code) }
        "ASDFGHJKL".forEachIndexed { i, c -> put(38 + i, c.code) }
        "ZXCVBNM".forEachIndexed { i, c -> put(52 + i, c.code) }
        put(20, AwtKeyEvent.VK_MINUS)
        put(21, AwtKeyEvent.VK_EQUALS)
        put(59, AwtKeyEvent.VK_COMMA)
        put(60, AwtKeyEvent.VK_PERIOD)
    }

    /** The punctuation shortcuts read by what the key types, not where it sits. */
    private val LABELLED = setOf(
        AwtKeyEvent.VK_MINUS, AwtKeyEvent.VK_EQUALS, AwtKeyEvent.VK_PLUS, AwtKeyEvent.VK_COMMA, AwtKeyEvent.VK_PERIOD, AwtKeyEvent.VK_SLASH,
    )

    /** The last ASCII character. */
    private const val ASCII_END = 0x7F

    /** Where the first non-Latin script starts (Greek). */
    private const val NON_LATIN = 0x370

    /** AWT's code for a key with extended code above this is the character's, plus it. */
    private const val UNICODE_BASE = 0x01000000

    /**
     * Whether a [char] typed with Ctrl (and not Alt, which with Ctrl is AltGr's)
     * is the shortcut's and not text: a letter beyond ASCII, which a field
     * would otherwise type (Ctrl+ц on a Russian keyboard is Ctrl+W and types
     * no ц, as Ctrl+W types no w; Ctrl+é on a French one is Ctrl+2).
     */
    fun ctrlTypesNothing(char: Int, ctrl: Boolean, alt: Boolean): Boolean =
        ctrl && !alt && char > ASCII_END && Character.isLetter(char)

    /** Whether AWT's [keyCode] is a key shortcuts take as the layout names it, needing no look at where it sits. */
    fun byLabel(keyCode: Int): Boolean =
        keyCode in AwtKeyEvent.VK_A..AwtKeyEvent.VK_Z || keyCode in AwtKeyEvent.VK_0..AwtKeyEvent.VK_9 || keyCode in LABELLED

    /**
     * The AWT key code a shortcut reads for a key AWT named [keyCode] (with
     * [extendedKeyCode]) on X11's key [xKeycode] (null where unknown): [keyCode]
     * itself unless the rules above put another key in its place.
     */
    fun awtCode(keyCode: Int, extendedKeyCode: Int, xKeycode: Int?): Int {
        if (xKeycode == null || byLabel(keyCode)) return keyCode
        DIGITS[xKeycode]?.let { return it }
        val char = if (extendedKeyCode > UNICODE_BASE) extendedKeyCode - UNICODE_BASE else return keyCode
        val nonLatinLetter = char >= NON_LATIN && Character.isLetter(char)
        return LETTERS[xKeycode]?.takeIf { nonLatinLetter } ?: keyCode
    }
}

/** X11's key code of an AWT key event: what key was pressed, whichever layout is on. */
private object XKeycodes {
    private val onX11 = runCatching { Toolkit.getDefaultToolkit().javaClass.name == "sun.awt.X11.XToolkit" }.getOrDefault(false)
    private val RAW_CODE = Regex("""rawCode=(\d+)""")

    /**
     * AWT keeps it as the event's raw code, which only its description
     * shows ([AwtKeyEvent.paramString]); reading the field itself would need
     * java.awt.event opened to the app.
     */
    fun of(event: AwtKeyEvent): Int? {
        if (!onX11) return null
        return RAW_CODE.find(event.paramString())?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
    }
}
