package com.aimdi.xtanative.networks.substack

/**
 * XTA's test/fixtures/substack_synthetic.dart: a long post built, not
 * recorded, from the node names Substack's editor emits (captioned images,
 * footnotes, block quotes), so the reader takes the paths a real post takes.
 */
object SubstackSynthetic {
    private val words = listOf(
        "the", "reader", "quiet", "river", "morning", "letter", "harbour", "lantern", "season", "garden",
        "distance", "history", "evidence", "question", "answer", "window", "market", "village", "engine", "pattern",
        "measure", "promise", "shadow", "signal", "summer", "winter", "paper", "bridge", "mountain", "theory",
        "and", "of", "to", "in", "that", "with", "for", "was", "on", "is", "as", "by", "at", "from", "but", "not",
    )

    fun words(count: Int, seed: Int): String = (0 until count).joinToString(" ") { words[(seed * 31 + it * 7) % words.size] }

    fun imageUrl(index: Int): String =
        "https://substackcdn.com/image/fetch/w_1456,c_limit,f_auto,q_auto:good,fl_progressive:steep/" +
            "https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2Fxta-perf-${index}_1456x816.jpeg"

    fun image(index: Int, url: String): String =
        "<div class=\"captioned-image-container\"><figure><a class=\"image-link image2 is-viewable-img\" " +
            "target=\"_blank\" href=\"$url\"><div class=\"image2-inset\"><picture><source type=\"image/webp\" " +
            "srcset=\"$url 424w, $url 848w, $url 1456w\" sizes=\"100vw\"><img src=\"$url\" width=\"1456\" height=\"816\" " +
            "class=\"sizing-normal\" alt=\"\" srcset=\"$url 424w, $url 848w, $url 1456w\" sizes=\"100vw\" loading=\"lazy\">" +
            "</picture><div class=\"image-link-expand\"><button type=\"button\" class=\"restack-image\"><svg></svg></button>" +
            "</div></div></a><figcaption class=\"image-caption\">Figure ${index + 1}. ${words(12, index)}" +
            "</figcaption></figure></div>"

    private fun footnoteAnchor(n: Int) =
        "<a class=\"footnote-anchor\" data-component-name=\"FootnoteAnchorToDOM\" id=\"footnote-anchor-$n\" href=\"#footnote-$n\" target=\"_self\">$n</a>"

    private fun footnote(n: Int) =
        "<div class=\"footnote\" data-component-name=\"FootnoteToDOM\"><a id=\"footnote-$n\" href=\"#footnote-anchor-$n\" " +
            "class=\"footnote-number\" target=\"_self\">$n</a><div class=\"footnote-content\"><p>${words(30, n)}</p></div></div>"

    private fun paragraph(seed: Int, count: Int, footnote: Int?) = "<p>${words(count, seed)}${footnote?.let(::footnoteAnchor).orEmpty()}.</p>"

    /** About [sections] × [paragraphs] × [wordCount] words, [images] figures spread through it, a quote every seventh section, [footnotes] notes at the end. */
    fun body(sections: Int = 40, paragraphs: Int = 5, wordCount: Int = 42, images: Int = 24, footnotes: Int = 12): String = buildString {
        val total = sections * paragraphs
        var image = 0
        for (block in 0 until total) {
            if (block % paragraphs == 0) append("<h2>${words(6, block)}</h2>")
            val note = if (block % (total / footnotes) == 0) block / (total / footnotes) + 1 else null
            append(paragraph(block, wordCount, note?.takeIf { it <= footnotes }))
            if (block % (paragraphs * 7) == 3) append("<blockquote><p>${words(28, block)}</p></blockquote>")
            if (image < images && block % (total / images) == total / images - 1) {
                append(image(image, imageUrl(image)))
                image++
            }
        }
        for (n in 1..footnotes) append(footnote(n))
    }
}
