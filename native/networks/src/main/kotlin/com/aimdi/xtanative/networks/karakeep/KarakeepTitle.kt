package com.aimdi.xtanative.networks.karakeep

private val WHITESPACE = Regex("(?U)\\s+")

/**
 * The title a kept post is saved under: its author by [name] and [handle],
 * then as much of [text] as fits on one line, so the bookmark is recognisable
 * before Karakeep's crawler fills in its own. XTA saves to Deepmarks under the
 * same title.
 */
fun karakeepTitle(name: String?, handle: String?, text: String): String {
    val author = name?.trim().orEmpty()
    val at = handle?.trim().orEmpty()
    val byline = when {
        author.isNotEmpty() && at.isNotEmpty() -> "$author (@$at)"
        author.isNotEmpty() -> author
        at.isNotEmpty() -> "@$at"
        else -> null
    }
    val line = text.replace(WHITESPACE, " ").trim()
    return when {
        byline == null -> line
        line.isEmpty() -> byline
        else -> "$byline: $line"
    }
}
