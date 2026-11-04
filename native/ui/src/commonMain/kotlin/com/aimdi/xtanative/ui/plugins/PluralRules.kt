package com.aimdi.xtanative.ui.plugins

/**
 * Which plural form a whole number takes in a language: CLDR's cardinal
 * categories ("zero", "one", "two", "few", "many", "other") for the
 * languages XTA speaks, as Android's `<plurals>` pick them. A language this
 * does not know says "one" for 1 and "other" for the rest.
 */
internal object PluralRules {
    fun category(language: String, count: Int): String {
        val tag = language.trim().replace('_', '-').lowercase()
        val n = if (count < 0) -count.toLong() else count.toLong()
        return when (tag.substringBefore('-')) {
            "ar" -> arabic(n)
            "be", "ru", "uk" -> eastSlavic(n)
            "pl" -> polish(n)
            "cs", "sk" -> if (n == 1L) ONE else if (n in 2..4) FEW else OTHER
            "ro" -> if (n == 1L) ONE else if (n == 0L || n % 100 in 1..19) FEW else OTHER
            "fr", "hi" -> if (n <= 1) ONE else OTHER
            "pt" -> if (tag == "pt" || tag == "pt-pt") oneForOne(n) else if (n <= 1) ONE else OTHER
            "id", "ja", "ko", "vi", "zh" -> OTHER
            else -> oneForOne(n)
        }
    }

    private fun oneForOne(n: Long) = if (n == 1L) ONE else OTHER

    private fun arabic(n: Long): String = when {
        n == 0L -> "zero"
        n == 1L -> ONE
        n == 2L -> "two"
        n % 100 in 3..10 -> FEW
        n % 100 in 11..99 -> MANY
        else -> OTHER
    }

    private fun eastSlavic(n: Long): String = when {
        n % 10 == 1L && n % 100 != 11L -> ONE
        n % 10 in 2..4 && n % 100 !in 12..14 -> FEW
        else -> MANY
    }

    private fun polish(n: Long): String = when {
        n == 1L -> ONE
        n % 10 in 2..4 && n % 100 !in 12..14 -> FEW
        else -> MANY
    }

    private const val ONE = "one"
    private const val FEW = "few"
    private const val MANY = "many"
    private const val OTHER = "other"
}
