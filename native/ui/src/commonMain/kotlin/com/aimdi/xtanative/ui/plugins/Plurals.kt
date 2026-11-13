package com.aimdi.xtanative.ui.plugins

/**
 * The CLDR plural category a whole [count] takes in [language] (a BCP 47
 * tag): "zero", "one", "two", "few", "many" or "other", as Android and
 * Compose choose a `<plurals>` item, for every language the app is written
 * in; any other language counts as English does.
 */
fun pluralCategory(count: Long, language: String): String {
    val n = if (count < 0) -count else count
    val tag = language.trim().replace('_', '-').lowercase()
    return when (tag.substringBefore('-')) {
        "ar" -> arabic(n)
        "be", "ru", "uk" -> eastSlavic(n)
        "pl" -> polish(n)
        "cs", "sk" -> czech(n)
        "ro" -> romanian(n)
        "fr" -> if (n <= 1) ONE else millions(n)
        "pt" -> if (n == 1L || (n == 0L && tag != "pt-pt")) ONE else millions(n)
        "es", "it", "ca" -> if (n == 1L) ONE else millions(n)
        "hi" -> if (n <= 1) ONE else OTHER
        "ja", "ko", "zh", "id", "vi" -> OTHER
        else -> if (n == 1L) ONE else OTHER
    }
}

private const val ZERO = "zero"
private const val ONE = "one"
private const val TWO = "two"
private const val FEW = "few"
private const val MANY = "many"
private const val OTHER = "other"

private fun arabic(n: Long): String = when {
    n == 0L -> ZERO
    n == 1L -> ONE
    n == 2L -> TWO
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

private fun czech(n: Long): String = when (n) {
    1L -> ONE
    in 2..4 -> FEW
    else -> OTHER
}

private fun romanian(n: Long): String = when {
    n == 1L -> ONE
    n == 0L || n % 100 in 1..19 -> FEW
    else -> OTHER
}

/** French, Spanish, Italian, Catalan and Portuguese say "many" of whole millions. */
private fun millions(n: Long): String = if (n != 0L && n % 1_000_000 == 0L) MANY else OTHER
