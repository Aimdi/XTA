package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.store.downloads.fillNameTemplate
import com.aimdi.xtanative.store.downloads.safeDownloadFolder
import com.aimdi.xtanative.store.downloads.safeFileStem
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What a file-name template may say about a page, in the order the editor offers them. */
internal enum class PixivNameToken(val token: String) {
    ILLUST_ID("{illust_id}"),
    PART("{part}"),
    TITLE("{title}"),
    USER_ID("{user_id}"),
    USER_NAME("{user_name}"),
    ;

    fun valueFor(illust: PixivIllust, page: Int): String = when (this) {
        ILLUST_ID -> "${illust.id}"
        PART -> "$page"
        TITLE -> illust.title
        USER_ID -> "${illust.userId}"
        USER_NAME -> illust.userName
    }
}

/** How and where saved pages land, read once from the reader's settings (XTA's file-name template and folder switches). */
internal data class PixivSaveNaming(
    val template: String = DEFAULT_TEMPLATE,
    val folderPerArtist: Boolean = false,
    val folderR18: Boolean = false,
) {
    /** The name for [page] of [illust], with the original file's extension. */
    fun pageName(illust: PixivIllust, page: Int): String = fileName(template, illust, page, extensionOf(illust.downloadUrlAt(page)))

    /** A whole-work file such as an ugoira's archive, named as its first page. */
    fun workName(illust: PixivIllust, extension: String): String = fileName(template, illust, 0, extension)

    /** `R-18/` first, then `<user name>_<user id>/`, as the settings ask. */
    fun subfolder(illust: PixivIllust): String? {
        val parts = listOfNotNull(
            "R-18".takeIf { folderR18 && illust.isR18 },
            safeFileStem("${illust.userName}_${illust.userId}").takeIf { folderPerArtist },
        )
        return if (parts.isEmpty()) null else safeDownloadFolder(parts.joinToString("/"))
    }

    companion object {
        const val DEFAULT_TEMPLATE = "{illust_id}_p{part}"
        const val PART = "{part}"

        /** A stored template that lost `{part}` is not used, so the pages of a work never overwrite each other. */
        fun of(settings: Settings): PixivSaveNaming {
            val stored = settings[PixivPrefs.fileNameTemplate]
            return PixivSaveNaming(
                template = if (hasPart(stored)) stored else DEFAULT_TEMPLATE,
                folderPerArtist = settings[PixivPrefs.folderPerArtist],
                folderR18 = settings[PixivPrefs.folderR18],
            )
        }

        fun hasPart(template: String): Boolean = PART in template

        /** [template] filled in for [page] of [illust] in one pass, made safe for a file system, [extension] (with its dot) kept. */
        fun fileName(template: String, illust: PixivIllust, page: Int, extension: String): String {
            val values = PixivNameToken.entries.associate { it.token to it.valueFor(illust, page) }
            val stem = safeFileStem(fillNameTemplate(template, values))
            return stem.ifEmpty { "${illust.id}_p$page" } + extension
        }

        private val extension = Regex("^\\.[a-z0-9]{1,5}$")

        /** The extension of the file at [url], `.jpg` when it has none. */
        fun extensionOf(url: String): String {
            val name = url.toHttpUrlOrNull()?.pathSegments?.lastOrNull().orEmpty()
            val found = name.substringAfterLast('.', "").lowercase().let { ".$it" }
            return if (extension.matches(found)) found else ".jpg"
        }
    }
}

/**
 * The pages saved on this device (`plugin.pixiv.download_index`, as XTA keeps
 * it: `<id>_p<page>` keys, oldest first, the newest [CAP] kept), so a tile can
 * show what is saved and a second save asks first. Device-only: no backup
 * carries it.
 */
internal object PixivSavedPages {
    const val CAP = 5000

    private val keyShape = Regex("^\\d{1,12}_p\\d{1,4}$")

    fun key(illustId: Long, page: Int): String = "${illustId}_p$page"

    fun of(settings: Settings): Set<String> =
        capped(J.parse(settings[PixivPrefs.downloadIndex]).list.mapNotNull { it.string?.takeIf(keyShape::matches) })

    /** [keys] in order with repeats folded, the newest [CAP] kept. */
    fun capped(keys: List<String>): Set<String> {
        val unique = LinkedHashSet(keys)
        return if (unique.size <= CAP) unique else LinkedHashSet(unique.drop(unique.size - CAP))
    }

    /** Which of [pages] of [illust] are saved already. */
    fun savedAmong(saved: Set<String>, illust: PixivIllust, pages: List<Int>): List<Int> = pages.filter { key(illust.id, it) in saved }

    /** How many pages of [illust] are saved. */
    fun savedCount(saved: Set<String>, illust: PixivIllust): Int = savedAmong(saved, illust, illust.viewerUrls.indices.toList()).size

    /** [settings] remembering [pages] of [illustId] as saved now, newest last. */
    fun recorded(settings: Settings, illustId: Long, pages: List<Int>): Settings {
        val added = pages.map { key(illustId, it) }
        if (added.isEmpty()) return settings
        val next = capped(of(settings).filterNot { it in added } + added)
        return settings.with(PixivPrefs.downloadIndex, JsonArray(next.map(::JsonPrimitive)).toString())
    }

    fun cleared(settings: Settings): Settings = settings.with(PixivPrefs.downloadIndex, "[]")
}

/** What the reader chose when some of the pages to save were saved before. */
internal enum class PixivResave { ALL, NEW_ONLY }

/** [pages], all or only the ones not saved yet, as [choice] says; none when the reader cancelled. */
internal fun pagesToSave(pages: List<Int>, saved: List<Int>, choice: PixivResave?): List<Int> = when (choice) {
    PixivResave.ALL -> pages
    PixivResave.NEW_ONLY -> pages.filterNot { it in saved }
    null -> emptyList()
}
