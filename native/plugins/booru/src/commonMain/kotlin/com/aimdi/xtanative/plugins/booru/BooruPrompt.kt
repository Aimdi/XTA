package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruTagCategory

/** One kind of tag in the reader's prompt order, and whether it is kept. */
internal data class PromptKind(val category: BooruTagCategory, val included: Boolean)

/**
 * "Copy tags as prompt" (booru_prompt.dart): a post's tags grouped by kind in
 * the reader's order, as an image-generation prompt.
 */
internal object BooruPrompt {
    /** Who and what is drawn, then by whom, then the rest; meta tags (resolution, file details) are left out. */
    private val DEFAULT_ORDER = listOf(
        BooruTagCategory.CHARACTER,
        BooruTagCategory.COPYRIGHT,
        BooruTagCategory.ARTIST,
        BooruTagCategory.SPECIES,
        BooruTagCategory.GENERAL,
    )

    /** The default order as stored, meta left out; what a reset writes. */
    val DEFAULT_STORED: String = stored(DEFAULT_ORDER.map { PromptKind(it, true) } + PromptKind(BooruTagCategory.META, false))

    /**
     * Every kind of tag in the reader's order: kept kinds stored by name,
     * left-out ones with a leading `-`; kinds the stored order does not name
     * follow it, kept unless they are meta.
     */
    fun kinds(stored: String): List<PromptKind> {
        val named = stored.ifBlank { DEFAULT_STORED }.split(',').map(String::trim).mapNotNull { token ->
            category(token.removePrefix("-"))?.let { PromptKind(it, !token.startsWith("-")) }
        }.distinctBy { it.category }
        val rest = BooruTagCategory.entries.filter { kind -> named.none { it.category == kind } }
            .map { PromptKind(it, it != BooruTagCategory.META) }
        return named + rest
    }

    fun stored(kinds: List<PromptKind>): String = kinds.joinToString(",") { (if (it.included) "" else "-") + it.category.name.lowercase() }

    /** [kinds] with the one at [from] moved to [to]. */
    fun moved(kinds: List<PromptKind>, from: Int, to: Int): List<PromptKind> {
        if (from !in kinds.indices || to !in kinds.indices) return kinds
        return kinds.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun toggled(kinds: List<PromptKind>, category: BooruTagCategory, included: Boolean): List<PromptKind> =
        kinds.map { if (it.category == category) it.copy(included = included) else it }

    /** The kinds kept, in their order. */
    fun order(stored: String): List<BooruTagCategory> = kinds(stored).filter { it.included }.map { it.category }

    /**
     * [tags] grouped by kind in [order], kinds not in it left out, underscores
     * read as spaces and parentheses escaped, joined with commas. A tag of three
     * characters or fewer keeps its underscores: it is an emoticon such as
     * `^_^`. A tag of no known kind counts as general.
     */
    fun text(tags: List<String>, kinds: Map<String, BooruTagCategory>, order: List<BooruTagCategory>): String =
        order.flatMap { category -> tags.filter { (kinds[it] ?: BooruTagCategory.GENERAL) == category } }
            .joinToString(", ") { tag -> (if (tag.length <= 3) tag else tag.replace('_', ' ')).replace("(", "\\(").replace(")", "\\)") }

    private fun category(name: String): BooruTagCategory? = BooruTagCategory.entries.firstOrNull { it.name.lowercase() == name }
}
