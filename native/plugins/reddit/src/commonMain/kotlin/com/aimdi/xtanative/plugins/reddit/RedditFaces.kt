package com.aimdi.xtanative.plugins.reddit

/** One generated avatar: an 8 × 8 face ('#' the ink, 'o' the accent) on a background, as ARGB. */
internal data class RedditFace(val rows: List<String>, val background: Int, val accent: Int) {
    /** Black on a light background, white on a dark one, so the face is what is seen first. */
    val ink: Int get() = if (RedditContrast.luminance(background) > 0.55) 0xFF15202B.toInt() else 0xFFFFFFFF.toInt()
}

/**
 * The pixel faces XTA draws for Reddit accounts and communities without a
 * picture (`reddit_avatar.dart`): derived from the name, so the same account
 * always has the same face and colour, and a thread can be followed by shape.
 */
internal object RedditFaces {
    val backgrounds = longArrayOf(
        0xFFB25400, 0xFFE84462, 0xFFE9E9E9, 0xFFFF2D20, 0xFF1D9BF0, 0xFF00BA7C,
        0xFF7856FF, 0xFFFFB300, 0xFF00838F, 0xFFD81B60, 0xFF5D4037, 0xFF37474F,
    )
    private val accents = longArrayOf(0xFF00E5D0, 0xFFFFD400, 0xFFFF4081, 0xFF448AFF, 0xFF69F0AE, 0xFFFF6E40)

    /** Hand-drawn rather than generated: random pixels read as noise, a few deliberate faces as characters. */
    val faces: List<List<String>> = listOf(
        listOf("........", ".##..##.", ".#o..o#.", "........", "..####..", ".#....#.", "..####..", "........"),
        listOf("........", "..#..#..", ".o#..#o.", "........", ".######.", ".#.##.#.", ".######.", "........"),
        listOf("..####..", ".######.", ".#o..o#.", ".######.", ".#.##.#.", ".######.", "..#..#..", "........"),
        listOf("........", ".#....#.", "..o..o..", "........", "...##...", "..####..", "........", "........"),
        listOf(".##..##.", ".##..##.", "........", "..o..o..", "........", ".######.", ".#....#.", "........"),
        listOf("........", ".######.", ".#o..o#.", ".#....#.", ".#.##.#.", ".######.", "..####..", "........"),
        listOf("...##...", "..####..", ".#o##o#.", ".######.", "..#..#..", ".##..##.", "........", "........"),
        listOf("........", ".o#..#o.", ".######.", ".#....#.", ".#....#.", ".######.", ".o#..#o.", "........"),
        listOf("........", "..#..#..", "..#..#..", "........", ".o####o.", "..####..", "...##...", "........"),
        listOf("..#..#..", ".######.", ".#o..o#.", ".######.", ".#####.#", "..####..", ".#....#.", "........"),
        listOf("........", ".##..##.", ".#o..o#.", ".##..##.", "........", "..o..o..", ".######.", "........"),
        listOf("...##...", "..#..#..", ".#o..o#.", ".#....#.", ".##..##.", "..####..", "...##...", "........"),
    )

    /** A stable FNV-1a hash of the name, lowercased: not [String.hashCode], so the face is XTA's for the same name. */
    fun seed(name: String): Int = hash(name).toInt()

    /** The hash as XTA's 64-bit int holds it: an empty name keeps the offset basis, above [Int.MAX_VALUE]. */
    private fun hash(name: String): Long {
        var hash = 0x811c9dc5L
        for (unit in name.lowercase()) {
            hash = hash xor unit.code.toLong()
            hash = (hash * 0x01000193L) and 0x7fffffffL
        }
        return hash
    }

    /** Each part from another part of the hash, so two names sharing a face rarely share a colour too. */
    fun of(name: String): RedditFace {
        val seed = hash(name)
        return RedditFace(
            rows = faces[(seed % faces.size).toInt()],
            background = backgrounds[((seed / 7) % backgrounds.size).toInt()].toInt(),
            accent = accents[((seed / 101) % accents.size).toInt()].toInt(),
        )
    }
}
