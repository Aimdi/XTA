package com.aimdi.xtanative.core.format

/**
 * Languages compared by what they are, not by how a source spells them. X
 * marks posts with the old ISO 639 codes for Indonesian (`in`), Hebrew (`iw`)
 * and Yiddish (`ji`), as Android's Locale still does, while Java 17 and
 * BCP 47 say `id`, `he` and `yi`.
 */
object Languages {
    private val OLD_CODES = mapOf("in" to "id", "iw" to "he", "ji" to "yi")

    /** The language of [tag] (`pt-BR`, `de_AT`, `in`), lowercase and in today's code. */
    fun of(tag: String): String {
        val code = tag.trim().replace('_', '-').substringBefore('-').lowercase()
        return OLD_CODES[code] ?: code
    }

    /** Whether [a] and [b] name the same language, whatever their region or spelling. */
    fun same(a: String, b: String): Boolean = of(a) == of(b)
}
