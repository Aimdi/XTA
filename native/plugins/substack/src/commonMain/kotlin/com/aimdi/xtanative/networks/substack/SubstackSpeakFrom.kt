package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.services.speech.textFromHere

/** "Speak from here" in the native reader: the speech text from a block on. */
object SubstackSpeakFrom {
    private const val NEEDLE = 40

    /**
     * The rest of [full], the article's speech text ([SubstackHtml.speakText]),
     * from block [index] of [blocks] on; a block with nothing to say (a
     * picture without a caption, a rule) starts at the next that has. Blocks
     * are found in order, so a paragraph repeated earlier is not mistaken for
     * the one held. [full] whole when [index] is no block.
     */
    fun fromBlock(full: String, blocks: List<SubstackBlock>, index: Int): String {
        if (index !in blocks.indices) return full
        val target = (index until blocks.size).firstOrNull { spoken(blocks[it]) != null } ?: return full
        var cursor = 0
        for (i in 0..target) {
            val needle = spoken(blocks[i])?.take(NEEDLE) ?: continue
            val at = full.indexOf(needle, cursor)
            if (i == target) return if (at >= 0) full.substring(at) else textFromHere(full, needle)
            if (at >= 0) cursor = at + needle.length
        }
        return full
    }

    /** What a block says aloud, as the speech text has it: footnote numbers left out, white space as one. */
    internal fun spoken(block: SubstackBlock): String? {
        val text = when (block) {
            is SubstackTextBlock -> if (block.kind == SubstackTextBlock.Kind.CODE) block.text else words(block.runs)
            is SubstackImageBlock -> words(block.caption)
            is SubstackTableBlock -> block.rows.flatten().firstOrNull { it.isNotBlank() }
            else -> null
        }
        return text?.let(SubstackText::collapse)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun words(runs: List<SubstackRun>): String = runs.filter { it.footnote == null }.joinToString("") { it.text }
}
