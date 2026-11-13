package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivNovelBlock
import com.aimdi.xtanative.networks.pixiv.PixivNovelMarkup
import com.aimdi.xtanative.store.downloads.safeFileStem

/** What a novel exported as text holds (pixiv_novel_export.dart). */
internal enum class PixivNovelExportFormat {
    /** Pixiv's markup removed: paragraphs as plain lines, ruby as `base(ruby)`. */
    PLAIN,

    /** The text exactly as Pixiv stores it, `[newpage]` and the rest included. */
    MARKUP,
}

/** The reader's Export as text: the file's name and what it holds. */
internal object PixivNovelExport {
    const val MIME = "text/plain"

    /** `<title>.txt`, the title stripped of what no file system takes; the novel's id names it when nothing of the title is left. */
    fun fileName(title: String, novelId: Long): String = safeFileStem(title).ifEmpty { "pixiv-novel-$novelId" } + ".txt"

    /** The file's text: [markup] as Pixiv stores it, or its [blocks] as plain lines. */
    fun text(markup: String, blocks: List<PixivNovelBlock>, format: PixivNovelExportFormat): String = when (format) {
        PixivNovelExportFormat.PLAIN -> PixivNovelMarkup.plainText(blocks)
        PixivNovelExportFormat.MARKUP -> markup
    }
}
