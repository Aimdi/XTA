package com.aimdi.xtanative.store.downloads

import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Android allows 255 bytes for a name; this leaves room for the extension and the " (1)" it adds when the name is taken. */
const val MAX_DOWNLOAD_STEM_BYTES = 180

private val unsafeNameCharacters = Regex("[\\x00-\\x1f<>:\"|?*]")

/** Characters no file system accepts in a name. */
private val illegalFileNameCharacters = Regex("[\\x00-\\x1f/\\\\:*?\"<>|]")

/**
 * [value] as a file name that cannot leave the folder it is saved in: only
 * its last part, without a query, characters file systems refuse made `_`;
 * `xta-media` when nothing usable is left.
 */
fun safeDownloadName(value: String): String {
    val path = value.replace('\\', '/').substringBefore('?').trimEnd('/')
    val name = path.substringAfterLast('/').replace(unsafeNameCharacters, "_")
    if (name.isEmpty() || name == "." || name == "..") return "xta-media"
    return if (name.length > 240) name.takeLast(240) else name
}

/**
 * [value] as a folder path that stays inside the chosen download folder:
 * separators split it, each part loses what file systems refuse, and `.`,
 * `..` and empty parts go; at most four levels. Null when nothing is left.
 */
fun safeDownloadFolder(value: String): String? =
    value.replace('\\', '/').split('/')
        .map { folderPart(it.replace(unsafeNameCharacters, "_")) }
        .filter { it.isNotEmpty() }
        .take(4)
        .takeIf { it.isNotEmpty() }
        ?.joinToString("/")

/** Dots and spaces at either end go: some card file systems refuse them at the end, and Android hides a dot folder from the gallery. */
private fun folderPart(part: String): String = part.take(120).replace(Regex("^[.\\s]+|[.\\s]+$"), "")

/** The longest start of [value] whose UTF-8 fits [limit] bytes, cut between characters so no emoji is split. */
fun fitUtf8Bytes(value: String, limit: Int): String {
    var used = 0
    val kept = StringBuilder()
    var i = 0
    while (i < value.length) {
        val codePoint = value.codePointAt(i)
        used += String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
        if (used > limit) break
        kept.appendCodePoint(codePoint)
        i += Character.charCount(codePoint)
    }
    return kept.toString()
}

private val nameToken = Regex("\\{[a-z0-9_]+\\}")

/**
 * [template] with each `{token}` replaced from [values] (keyed with their
 * braces) in one pass, so a value that itself reads like a token stays as
 * written. A token [values] does not name stays too.
 */
fun fillNameTemplate(template: String, values: Map<String, String>): String =
    nameToken.replace(template) { match -> values[match.value] ?: match.value }

/**
 * [value] as the part of a file name before its extension: characters file
 * systems refuse become `_`, spaces fold, it fits Android's limit, and dots
 * and spaces at either end go. Empty when nothing is left.
 */
fun safeFileStem(value: String): String {
    val stem = value.replace(illegalFileNameCharacters, "_").replace(Regex("\\s+"), " ").trim()
    return fitUtf8Bytes(stem, MAX_DOWNLOAD_STEM_BYTES).replace(Regex("^[. ]+|[. ]+$"), "")
}

/** X's pictures at their original size: other hosts are left exactly as they are. */
fun originalDownloadUrl(url: String): String {
    val parsed = url.toHttpUrlOrNull()?.takeIf { it.host.equals("pbs.twimg.com", ignoreCase = true) } ?: return url
    if (parsed.queryParameter("format") != null) return parsed.newBuilder().setQueryParameter("name", "orig").build().toString()
    val path = parsed.encodedPath.replace(Regex(":(small|medium|large|thumb|orig)$"), "")
    return parsed.newBuilder().encodedPath("$path:orig").build().toString()
}

/** Headers a media host refuses to serve without, from the URL alone so a retried download sends them too. */
fun downloadRequestHeaders(url: String): Map<String, String> {
    val host = url.toHttpUrlOrNull()?.host?.lowercase() ?: return emptyMap()
    return if (host == "pximg.net" || host.endsWith(".pximg.net")) pixivImageHeaders else emptyMap()
}

private val pixivImageHeaders = mapOf("Referer" to "https://www.pixiv.net/", "User-Agent" to "Mozilla/5.0")

/** The content type each extension a download may end in stands for; a type's first extension is the one a file of it is given. */
private val downloadMimeTypes = linkedMapOf(
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "png" to "image/png",
    "gif" to "image/gif",
    "webp" to "image/webp",
    "mp4" to "video/mp4",
    "m4v" to "video/x-m4v",
    "mov" to "video/quicktime",
    "webm" to "video/webm",
    "mp3" to "audio/mpeg",
    "m4a" to "audio/mp4",
    "zip" to "application/zip",
    "epub" to "application/epub+zip",
    "json" to "application/json",
    "txt" to "text/plain",
    "html" to "text/html",
)

/** A file's content type from its extension: stored with the file, it decides whether a gallery shows it. */
fun mimeTypeFor(fileName: String): String = downloadMimeTypes[fileName.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"

/** The extension a file of [mimeType] is given, its parameters (`; charset=utf-8`) aside; null for a type downloads do not name. */
fun extensionFor(mimeType: String): String? {
    val type = mimeType.substringBefore(';').trim().lowercase()
    return downloadMimeTypes.entries.firstOrNull { it.value == type }?.key
}

/** Where a download saved without a picker lands: the shared folder for its type, with an XTA folder in it. */
enum class SharedDownloadFolder(
    /** The MediaStore collection, as Android's side names it. */
    val collection: String,
    val relativePath: String,
) {
    PICTURES("images", "Pictures/XTA"),
    MOVIES("video", "Movies/XTA"),
    DOWNLOADS("downloads", "Download/XTA"),
    ;

    companion object {
        fun of(fileName: String): SharedDownloadFolder {
            val mime = mimeTypeFor(fileName)
            return when {
                mime.startsWith("image/") -> PICTURES
                mime.startsWith("video/") -> MOVIES
                else -> DOWNLOADS
            }
        }
    }
}

/** The shared folder a finished background download went to, with its subfolder; null for any other download. */
fun sharedFolderOf(entry: DownloadEntry): String? {
    val authority = runCatching { URI(entry.savedUri.orEmpty()).authority }.getOrNull()
    if (!entry.background || authority != "media") return null
    val folder = SharedDownloadFolder.of(entry.fileName).relativePath
    return entry.subfolder?.let { "$folder/$it" } ?: folder
}
