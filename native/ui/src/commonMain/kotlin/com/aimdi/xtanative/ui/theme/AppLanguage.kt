package com.aimdi.xtanative.ui.theme

import androidx.compose.runtime.Composable

/**
 * The reader's language as the app and its resources take it: a stored code
 * ("de", "zh_Hans", "be_Latn" or [SYSTEM]) as the `language` and `region`
 * the platform's locale is made of. Here, beside the theme that applies it,
 * rather than with the settings that store it, so what draws a post does not
 * reach into the settings screens.
 */
object AppLocale {
    /** The stored code that follows the system's language. */
    const val SYSTEM = "system"

    /** The region Latin Belarusian is kept under, as its resources are (`values-be-rXL`). */
    const val LATIN_BELARUSIAN_REGION = "XL"

    /**
     * [code] as `language` to `region`, or null for the system's: the scripts
     * of Chinese are kept under China and Taiwan, Latin Belarusian under
     * [LATIN_BELARUSIAN_REGION].
     */
    /** The languages written right to left, by their ISO 639 code. */
    val RIGHT_TO_LEFT = setOf("ar", "fa", "he", "iw", "ur", "ps", "yi", "dv", "ckb", "ug", "sd")

    /**
     * The right-to-left languages the app is written in. A system set to
     * another (Hebrew) shows the app in English, left to right.
     */
    val SHOWN_RIGHT_TO_LEFT = setOf("ar")

    /** Whether the screens are laid out right to left for [language] (an ISO 639 code), the one the app shows. */
    fun rightToLeft(language: String?): Boolean = language in SHOWN_RIGHT_TO_LEFT

    fun of(code: String?): Pair<String, String>? {
        val tag = code?.takeIf { it.isNotBlank() && it != SYSTEM } ?: return null
        return when (tag) {
            "zh_Hans" -> "zh" to "CN"
            "zh_Hant" -> "zh" to "TW"
            "be_Latn" -> "be" to LATIN_BELARUSIAN_REGION
            else -> tag.substringBefore('_') to tag.substringAfter('_', "")
        }
    }
}

/** The screens below in [language] (`language` and `region`, as [AppLocale.of] gives them), or the system's for null. */
@Composable
expect fun ProvideAppLanguage(language: Pair<String, String>?, content: @Composable () -> Unit)
