package com.aimdi.xtanative.ui.home

/**
 * The round mark of something with no picture (XTA's fallback avatar): one
 * letter on a colour chosen by its id, so a rename keeps the colour. The
 * account menu draws the reader's X account this way, as XTA's chrome does.
 */
object Monogram {
    /** XTA's palette for missing pictures, as ARGB: saturated, never grey. */
    val palette: List<Long> = listOf(
        0xFF1D9BF0, 0xFFF91880, 0xFF7856FF, 0xFFFF7A00, 0xFF00BA7C, 0xFFFFD400, 0xFFE0245E, 0xFF17BF63,
        0xFF794BC4, 0xFFFF6F00, 0xFF1B95E0, 0xFFF45D22, 0xFF2EC7C2, 0xFFE0457B, 0xFF5C6BC0, 0xFF26A69A,
    )

    private val letterOrDigit = Regex("[\\p{L}\\p{N}]")

    /** The colour of [seed]: XTA's hash over its UTF-16 units, in Dart's 64-bit arithmetic. */
    fun colour(seed: String): Long = palette[index(seed, palette.size)]

    fun index(seed: String, size: Int): Int {
        var hash = 0L
        seed.forEach { unit -> hash = unit.code + ((hash shl 5) - hash) }
        return (((hash % size) + size) % size).toInt()
    }

    /** The first letter or digit of [name], else of [seed], else `#`, in capitals. */
    fun initial(name: String, seed: String): String =
        (letterOrDigit.find(name) ?: letterOrDigit.find(seed))?.value?.uppercase() ?: "#"
}
