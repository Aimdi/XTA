package com.aimdi.xtanative.networks.ehviewer

import java.time.Instant
import kotlin.math.abs
import kotlin.math.min

/** An E-Hentai gallery category. [bit] is the category's place in the site's `f_cats` mask; [label] is how the site names it. */
enum class EhCategory(val bit: Int, val label: String) {
    MISC(1, "Misc"),
    DOUJINSHI(2, "Doujinshi"),
    MANGA(4, "Manga"),
    ARTIST_CG(8, "Artist CG"),
    GAME_CG(16, "Game CG"),
    IMAGE_SET(32, "Image Set"),
    COSPLAY(64, "Cosplay"),
    ASIAN_PORN(128, "Asian Porn"),
    NON_H(256, "Non-H"),
    WESTERN(512, "Western"),
    ;

    companion object {
        private val separators = Regex("[\\s_-]+")

        /** The category the site (or XTA's stored setting) names [raw], in any case and spacing; null when there is none. */
        fun tryParse(raw: String?): EhCategory? = when (raw?.trim()?.lowercase()?.replace(separators, "")) {
            "misc" -> MISC
            "doujinshi" -> DOUJINSHI
            "manga" -> MANGA
            "artistcg", "artistcgsets" -> ARTIST_CG
            "gamecg" -> GAME_CG
            "imageset", "imagesets" -> IMAGE_SET
            "cosplay" -> COSPLAY
            "asianporn" -> ASIAN_PORN
            "nonh" -> NON_H
            "western" -> WESTERN
            else -> null
        }

        /** The site's `f_cats`: the categories to leave out. None chosen, or all, leaves nothing out. */
        fun excludeMask(included: Set<EhCategory>): Int =
            if (included.isEmpty() || included.containsAll(entries)) 0 else entries.filterNot { it in included }.sumOf { it.bit }
    }
}

/** A tag namespace, in the order the site lists them on a gallery. [key] is how the site writes it. */
enum class EhNamespace {
    LANGUAGE,
    PARODY,
    CHARACTER,
    GROUP,
    ARTIST,
    COSPLAYER,
    MALE,
    FEMALE,
    MIXED,
    OTHER,
    RECLASS,
    TEMP,
    ;

    val key: String get() = name.lowercase()

    companion object {
        fun tryParse(raw: String?): EhNamespace? = raw?.trim()?.lowercase()?.let { key -> entries.firstOrNull { it.key == key } }
    }
}

/** One gallery tag, `namespace:name` on the site; [namespace] is null for a tag listed without a known one. */
data class EhTag(val namespace: EhNamespace?, val name: String) {
    val raw: String get() = namespace?.let { "${it.key}:$name" } ?: name

    /** The exact-match search the site runs for this tag: `female:"big breasts$"`. */
    val query: String get() = namespace?.let { "${it.key}:\"$name$\"" } ?: "\"$name$\""

    companion object {
        /** `female:big breasts`; an unknown namespace stays part of the name. */
        fun parse(raw: String): EhTag {
            val colon = raw.indexOf(':')
            val namespace = if (colon > 0) EhNamespace.tryParse(raw.substring(0, colon)) else null
            return EhTag(namespace, (if (namespace == null) raw else raw.substring(colon + 1)).trim())
        }
    }
}

/** A gallery's tags under one namespace, as its page groups them. */
data class EhTagGroup(val namespace: EhNamespace?, val tags: List<EhTag>) {
    companion object {
        /** [tags] under their namespaces in the site's order, tags without one last. */
        fun of(tags: Iterable<String>): List<EhTagGroup> {
            val parsed = tags.map(EhTag::parse)
            return (EhNamespace.entries + null).mapNotNull { namespace ->
                parsed.filter { it.namespace == namespace }.takeIf { it.isNotEmpty() }?.let { EhTagGroup(namespace, it) }
            }
        }
    }
}

/** A gallery as a list row or the `gdata` API describes it. [postedAt] is the site's time, which is UTC. */
data class EhGallery(
    val gid: Long,
    val token: String,
    val title: String,
    val titleJpn: String? = null,
    val category: EhCategory? = null,
    val thumbUrl: String? = null,
    val uploader: String? = null,
    val postedAt: Instant? = null,
    val pageCount: Int? = null,
    val rating: Double? = null,
    val tags: List<String> = emptyList(),
) {
    val id: String get() = "$gid/$token"

    /** The language its tags name; the translation marks are not languages. */
    val tagLanguage: String?
        get() = tags.map(EhTag::parse).firstOrNull { it.namespace == EhNamespace.LANGUAGE && it.name !in languageMarks }?.name

    val displayTitle: String get() = titleFor(preferJapanese = true)

    /** The Japanese title when asked for and the site has one, else the main one. */
    fun titleFor(preferJapanese: Boolean): String =
        titleJpn?.trim()?.takeIf { preferJapanese && it.isNotEmpty() } ?: title

    fun galleryUrl(host: String): String = "$host/g/$gid/$token/"

    /** The page's own address on the site, as a link to copy. */
    fun pageUrl(host: String, pageToken: String, page: Int): String = "$host/s/$pageToken/$gid-$page"

    /**
     * Where reading goes on from: [lastPage] as history kept it, when that is
     * past the first page, held inside the gallery once its length is known.
     */
    fun continuePage(lastPage: Int?): Int? = when {
        lastPage == null || lastPage <= 1 -> null
        else -> pageCount?.let { min(lastPage, it) } ?: lastPage
    }

    private companion object {
        val languageMarks = setOf("translated", "rewrite", "speechless", "text cleaned")
    }
}

/**
 * A gallery's own page, read one preview sheet (`?p=`) at a time: [previews]
 * are that sheet's tiles, [previewSheetIndex] which sheet it is of
 * [previewSheetCount]. [weakTags] are the tags the site shows dashed: few
 * votes, not settled yet. [translated] is the site's TR mark.
 */
data class EhGalleryDetail(
    val gallery: EhGallery,
    val previews: List<EhPreview> = emptyList(),
    val comments: List<EhComment> = emptyList(),
    val fileSizeBytes: Long? = null,
    val previewSheetIndex: Int = 0,
    val previewSheetCount: Int = 1,
    val weakTags: Set<String> = emptySet(),
    val language: String? = null,
    val translated: Boolean = false,
    val favoritedCount: Int? = null,
    val ratingCount: Int? = null,
) {
    val tagGroups: List<EhTagGroup> get() = EhTagGroup.of(gallery.tags)
}

/** A comment under a gallery. [posted] is the site's own wording; [uploader] marks the uploader's comment. */
data class EhComment(
    val author: String,
    val posted: String,
    val body: String,
    val score: String? = null,
    val uploader: Boolean = false,
)

/** The site's toplist windows (`toplist.php?tl=`). */
enum class EhToplistPeriod(val tl: Int) {
    YESTERDAY(11),
    MONTH(12),
    YEAR(13),
    ALL_TIME(15),
}

/** A language a search can keep to, added as `language:[tag]`; [ANY] adds nothing. */
enum class EhSearchLanguage(val tag: String) {
    ANY(""),
    ENGLISH("english"),
    JAPANESE("japanese"),
    CHINESE("chinese"),
    KOREAN("korean"),
    SPANISH("spanish"),
    FRENCH("french"),
}

/** What a search keeps to besides its words. [minRating] is 0 for any rating, else 2 to 5 stars. */
data class EhSearchFilters(
    val categories: Set<EhCategory> = EhCategory.entries.toSet(),
    val minRating: Int = 0,
    val language: EhSearchLanguage = EhSearchLanguage.ANY,
)

/**
 * One page's tile on a gallery's preview sheet, and the token that opens the
 * page. Large tiles are cut from a sprite sheet: [thumbOffsetX] is where the
 * tile starts on it, [thumbWidth] and [thumbHeight] its size, all in sheet pixels.
 */
data class EhPreview(
    val pageToken: String,
    val page: Int,
    val thumbUrl: String? = null,
    val thumbOffsetX: Double? = null,
    val thumbWidth: Double? = null,
    val thumbHeight: Double? = null,
) {
    /** Where this tile sits on its sprite sheet once the sheet is loaded at [sheetWidth] × [sheetHeight]. */
    fun spriteCrop(sheetWidth: Double, sheetHeight: Double): EhSpriteCrop =
        EhSpriteCrop.of(sheetWidth, sheetHeight, thumbOffsetX ?: 0.0, thumbWidth ?: EhSpriteCrop.TILE_WIDTH, thumbHeight)
}

/** A rectangle of a preview sprite sheet, in sheet pixels. */
data class EhSpriteCrop(val left: Double, val top: Double, val width: Double, val height: Double) {
    companion object {
        /** How wide the site's large tiles are when a tile does not say. */
        const val TILE_WIDTH = 200.0

        /**
         * The tile at [offsetX] on a sheet of [sheetWidth] × [sheetHeight]: a
         * sheet holds a row of tiles, each [tileWidth] wide and up to the
         * sheet's height, and the crop never runs past the sheet. A lone
         * thumbnail is a sheet of one.
         */
        fun of(sheetWidth: Double, sheetHeight: Double, offsetX: Double, tileWidth: Double = TILE_WIDTH, tileHeight: Double? = null): EhSpriteCrop {
            val left = min(abs(offsetX), sheetWidth)
            return EhSpriteCrop(left, 0.0, min(tileWidth, sheetWidth - left), min(tileHeight ?: sheetHeight, sheetHeight))
        }
    }
}

/**
 * A page of a gallery as its image page shows it. [imageUrl] is the resample
 * the site serves; [originalImageUrl] the full original, which only a signed-in
 * reader can open; [reloadKey] asks the site for another image server (`nl`).
 */
data class EhImagePage(
    val imageUrl: String,
    val page: Int,
    val originalImageUrl: String? = null,
    val reloadKey: String? = null,
    val nextPageUrl: String? = null,
    val prevPageUrl: String? = null,
    val pageCount: Int? = null,
) {
    /** The original for a signed-in reader when the page offers one, else the resample: a guest's `fullimg` leads to a sign-in page. */
    fun displayUrl(signedIn: Boolean): String =
        originalImageUrl?.trim()?.takeIf { signedIn && it.isNotEmpty() } ?: imageUrl

    /** What to show, then the resample to fall back on when an original fails. */
    fun imageUrls(signedIn: Boolean): List<String> = listOf(displayUrl(signedIn), imageUrl).filter { it.isNotEmpty() }.distinct()

    /** The next page's token, from its link: reading on needs no preview sheet. */
    val nextPage: EhPageLink? get() = EhPageLink.parse(nextPageUrl)

    val prevPage: EhPageLink? get() = EhPageLink.parse(prevPageUrl)
}

/** Where a page link (`/s/{pageToken}/{gid}-{page}`) leads. */
data class EhPageLink(val pageToken: String, val gid: Long, val page: Int) {
    companion object {
        private val href = Regex("""https?://(?:e-hentai|exhentai)\.org/s/([0-9a-zA-Z]+)/(\d+)-(\d+)""")

        /** The page an E-Hentai or ExHentai page link leads to; null for any other link. */
        fun parse(url: String?): EhPageLink? {
            val match = href.find(url ?: return null) ?: return null
            val gid = match.groupValues[2].toLongOrNull() ?: return null
            val page = match.groupValues[3].toIntOrNull() ?: return null
            return EhPageLink(match.groupValues[1], gid, page)
        }
    }
}
