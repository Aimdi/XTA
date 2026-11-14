package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonObject

/**
 * The stickers a caption or bio draws among its words. CI's guest probe saw
 * them on bios as `text_fragments.fragments[].inline_sticker_fragment` with
 * `sticker_id` and `sticker_url` (`community_inline_sticker_id` null); a
 * post's fragments are read the same way, and under the other names Meta
 * may use until the probe has seen a post's: a `fragment_type` naming a
 * sticker, a `sticker_fragment` or `sticker` object, a `url`, an
 * `image_versions2`, and a list of `inline_stickers` beside the words. A
 * sticker without a picture is left out; nothing missing is an error.
 */
object ThreadsStickers {
    private val TYPES = setOf("inline_sticker", "sticker", "community_inline_sticker", "inline_sticker_fragment", "sticker_fragment")
    private val HOLDERS = listOf("inline_sticker_fragment", "sticker_fragment", "community_inline_sticker_fragment", "inline_sticker", "sticker")
    private val URLS = listOf("sticker_url", "url", "image_url", "static_url", "uri", "src")
    private val LABELS = listOf("accessibility_label", "accessibility_caption", "alt_text", "alt", "label", "sticker_name", "name", "description", "title")
    private val WIDTHS = listOf("width", "sticker_width", "original_width")
    private val HEIGHTS = listOf("height", "sticker_height", "original_height")
    private val blank = Regex("\\s+")

    /** The characters Meta may hold a sticker's place with in the caption itself. */
    private const val PLACEHOLDERS = "￼�"

    /** [fragment] stands for a sticker, whether or not it names a picture. */
    fun isSticker(fragment: J): Boolean = fragment["fragment_type"].string?.lowercase() in TYPES || holder(fragment) != null

    /** The sticker [fragment] draws: null when it names no picture. */
    fun of(fragment: J): ThreadsSticker? {
        val holder = holder(fragment) ?: fragment
        return sticker(holder) ?: sticker(holder["sticker"])
    }

    /** One sticker object: its picture (an address, else the largest of `image_versions2`), size and label. */
    fun sticker(node: J): ThreadsSticker? {
        if (node.raw !is JsonObject) return null
        val images = ThreadsParser.images(node["image_versions2"])
        val url = URLS.firstNotNullOfOrNull { node[it].string?.trim()?.takeIf(::isAddress) } ?: images.firstOrNull()?.url ?: return null
        val width = firstSize(node, WIDTHS) ?: images.firstOrNull()?.width
        val height = firstSize(node, HEIGHTS) ?: images.firstOrNull()?.height
        val label = LABELS.firstNotNullOfOrNull { node[it].string?.trim()?.takeIf(::isWords) }
        return ThreadsSticker(url, width, height, label)
    }

    /** A sticker fragment's run, labelled by its own words where Meta gave some and the sticker itself named none. */
    fun run(fragment: J): ThreadsTextFragment? {
        val sticker = of(fragment) ?: return null
        val named = if (sticker.label == null) plaintextLabel(fragment)?.let { sticker.copy(label = it) } ?: sticker else sticker
        return ThreadsTextFragment.sticker(named, spoiler = fragment["styling_info"]["is_spoiler"].boolean == true)
    }

    /** Stickers a post lists beside its fragments rather than among them (`inline_stickers`): drawn after its words. */
    fun listed(post: J): List<ThreadsSticker> {
        val info = post["text_post_app_info"]
        val list = listOf(info["inline_stickers"], post["inline_stickers"], info["text_fragments"]["inline_stickers"]).firstOrNull { it.list.isNotEmpty() }
        return list?.list.orEmpty().mapNotNull { sticker(it) ?: of(it) }
    }

    /** [caption] as its words alone: without the characters and [placeholders] standing for stickers, its spaces collapsed. */
    fun words(caption: String, placeholders: List<String>): String =
        collapse(placeholders.filter { it.isNotBlank() }.fold(caption) { left, held -> left.replaceFirst(held, " ") }.filterNot { it in PLACEHOLDERS })

    /** [text] with each run of spaces as one, trimmed: how a caption and its fragments are compared once stickers are in them. */
    fun collapse(text: String): String = text.replace(blank, " ").trim()

    /** A sticker fragment's own words, when they are words and not a stand-in character. */
    private fun plaintextLabel(fragment: J): String? = fragment["plaintext"].string?.trim()?.takeIf(::isWords)

    private fun holder(fragment: J): J? = HOLDERS.map { fragment[it] }.firstOrNull { it.raw is JsonObject }

    private fun firstSize(node: J, keys: List<String>): Int? = keys.firstNotNullOfOrNull { key -> node[key].integer?.takeIf { it in 1..Int.MAX_VALUE }?.toInt() }

    private fun isAddress(value: String): Boolean = value.startsWith("https://") || value.startsWith("http://")

    private fun isWords(value: String): Boolean = value.any(Char::isLetterOrDigit)
}
