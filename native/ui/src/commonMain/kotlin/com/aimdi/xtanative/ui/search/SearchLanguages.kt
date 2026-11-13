package com.aimdi.xtanative.ui.search

import com.aimdi.xtanative.core.x.search.XAdvancedSearch

/**
 * The languages X tags posts with, for the advanced search's language
 * filter, each named as it calls itself (XTA's search_languages.dart, with
 * its names from CLDR).
 */
object SearchLanguages {
    private val names = mapOf(
        "am" to "አማርኛ", "ar" to "العربية", "bg" to "български", "bn" to "বাংলা", "ca" to "català", "cs" to "čeština",
        "cy" to "Cymraeg", "da" to "dansk", "de" to "Deutsch", "el" to "Ελληνικά", "en" to "English", "es" to "español",
        "et" to "eesti", "eu" to "euskara", "fa" to "فارسی", "fi" to "suomi", "fr" to "français", "ga" to "Gaeilge",
        "gl" to "galego", "gu" to "ગુજરાતી", "he" to "עברית", "hi" to "हिंदी", "hr" to "hrvatski", "hu" to "magyar",
        "hy" to "հայերեն", "id" to "Bahasa Indonesia", "is" to "íslenska", "it" to "italiano", "ja" to "日本語",
        "ka" to "ქართული", "km" to "ខ្មែរ", "kn" to "ಕನ್ನಡ", "ko" to "한국어", "lo" to "ລາວ", "lt" to "lietuvių",
        "lv" to "latviešu", "ml" to "മലയാളം", "mr" to "मराठी", "ms" to "Bahasa Melayu", "my" to "ဗမာ", "ne" to "नेपाली",
        "nl" to "Nederlands", "no" to "norsk", "or" to "ଓଡ଼ିଆ", "pa" to "ਪੰਜਾਬੀ", "pl" to "polski", "ps" to "پښتو",
        "pt" to "português", "ro" to "română", "ru" to "русский", "si" to "සිංහල", "sk" to "slovenčina", "sl" to "slovenščina",
        "sr" to "српски", "sv" to "svenska", "ta" to "தமிழ்", "te" to "తెలుగు", "th" to "ไทย", "tl" to "Tagalog",
        "tr" to "Türkçe", "uk" to "українська", "ur" to "اردو", "vi" to "Tiếng Việt", "zh" to "中文",
    )

    /** What the language of [code] calls itself, or the code when it is not one of X's. */
    fun name(code: String): String = names[code] ?: code

    /** X's languages by their names, with [current] among them when a typed query asked for another code. */
    fun choices(current: String): List<String> =
        (XAdvancedSearch.LANGUAGES + listOfNotNull(current.takeIf { it.isNotEmpty() })).distinct().sortedBy { name(it).lowercase() }
}
