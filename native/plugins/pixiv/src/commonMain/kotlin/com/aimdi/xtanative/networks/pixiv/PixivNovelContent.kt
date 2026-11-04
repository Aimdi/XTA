package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J

/** The chapter before or after a novel in its series, as the novel's page links it. */
data class PixivNovelNeighbour(
    val id: Long,
    /** False when the reader may not open it: R-18 off, My pixiv only, or deleted. */
    val viewable: Boolean = false,
    val title: String = "",
    /** Its place in the series, from 1; 0 when Pixiv did not say. */
    val order: Int = 0,
)

/** A picture a novel shows: the file drawn, and the full one a save fetches. */
data class PixivNovelPicture(val url: String, val originalUrl: String? = null) {
    val saveUrl: String get() = originalUrl ?: url
}

/**
 * A novel's text and what it refers to, read from the page Pixiv's app shows
 * it in (`/webview/v2/novel`): the chapters beside it in its series, and the
 * pictures its tags name. [PixivNovelMarkup.parse] reads [text] into blocks.
 */
data class PixivNovelContent(
    val id: Long,
    /** The text, Pixiv's markup in it. */
    val text: String,
    val title: String = "",
    val previous: PixivNovelNeighbour? = null,
    val next: PixivNovelNeighbour? = null,
    /** `[uploadedimage:]` pictures by their id. */
    val uploads: Map<String, PixivNovelPicture> = emptyMap(),
    /** `[pixivimage:]` pictures by `ID` or `ID-N`, as the tag writes them. */
    val illusts: Map<String, PixivNovelPicture> = emptyMap(),
) {
    fun upload(block: PixivNovelBlock.Upload): PixivNovelPicture? = uploads[block.imageId]

    /** The page files a work's picture under the tag's own key; a first page may be filed under the bare id. */
    fun illust(block: PixivNovelBlock.Illust): PixivNovelPicture? =
        illusts[block.key] ?: if (block.page == 1) illusts["${block.illustId}"] else null

    companion object {
        /** The page's decoded `novel` object; null when it carries no text. */
        fun parse(data: J): PixivNovelContent? {
            val text = data["text"].string ?: return null
            val navigation = data["seriesNavigation"]
            return PixivNovelContent(
                id = data["id"].long ?: 0,
                text = text,
                title = data["title"].string?.trim().orEmpty(),
                previous = neighbour(navigation["prevNovel"]),
                next = neighbour(navigation["nextNovel"]),
                uploads = pictures(data["images"], ::uploadPicture),
                illusts = pictures(data["illusts"], ::illustPicture),
            )
        }

        /** A novel's webview page read into its content; null when the page carries none. */
        fun fromHtml(html: String): PixivNovelContent? = PixivNovelPage.json(html)?.let { parse(J(it)) }

        private fun neighbour(json: J): PixivNovelNeighbour? {
            val id = json["id"].long?.takeIf { it > 0 } ?: return null
            return PixivNovelNeighbour(
                id = id,
                viewable = json["viewable"].boolean == true,
                title = json["title"].string?.trim().orEmpty(),
                order = json["contentOrder"].int ?: 0,
            )
        }

        /** A JSON object of pictures by key; entries without a usable address are left out. */
        private fun pictures(map: J, read: (J) -> PixivNovelPicture?): Map<String, PixivNovelPicture> =
            map.keys.mapNotNull { key -> read(map[key])?.let { key to it } }.toMap()

        private fun firstUrl(candidates: List<J>): String? = candidates.firstNotNullOfOrNull { PixivText.nonEmpty(it.string) }

        private fun uploadPicture(image: J): PixivNovelPicture? {
            val urls = image["urls"]
            val shown = firstUrl(listOf(urls["1200x1200"], urls["480mw"], urls["original"], urls["240mw"], urls["128x128"])) ?: return null
            return PixivNovelPicture(shown, PixivText.nonEmpty(urls["original"].string))
        }

        private fun illustPicture(entry: J): PixivNovelPicture? {
            val images = entry["illust"]["images"]
            val shown = firstUrl(listOf(images["medium"], images["original"], images["small"])) ?: return null
            return PixivNovelPicture(shown, PixivText.nonEmpty(images["original"].string))
        }
    }
}
