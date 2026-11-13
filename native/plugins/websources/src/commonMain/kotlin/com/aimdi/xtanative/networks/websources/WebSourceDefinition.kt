package com.aimdi.xtanative.networks.websources

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** How a source's posts are drawn: links, articles, posts, or a gallery of pictures. */
enum class WebSourceLayout { LINKS, ARTICLES, POSTS, GALLERY }

/** What a list's pages are: JSON, HTML, or an RSS or Atom feed. */
enum class WebSourcePageType { JSON, HTML, RSS }

/** The fields a source can read from a post, named in its file as [key]. */
enum class WebSourceField {
    ID, TITLE, LINK, TEXT, HTML, AUTHOR, AVATAR, DATE, IMAGE, IMAGES, SCORE, COMMENTS, DISCUSSION, TAGS;

    val key: String get() = name.lowercase()
}

/** One list of posts: a feed (a tab), or the source's search. */
data class WebSourceList(
    val id: String,
    val name: String,
    /** The address template: `{page}`, `{cursor}` and, for search, `{query}`. */
    val url: String,
    val type: WebSourcePageType,
    /** A JSON path to the posts, or a CSS selector matching each post. */
    val items: String,
    /** Where each field is in one post: the file's fields, then the list's over them. */
    val fields: Map<WebSourceField, String>,
    val firstPage: Int = 1,
    /** Where a page names the next one, when it does. */
    val next: String? = null,
)

/** Where a post's full text is, for the reader. */
data class WebSourceArticle(
    /** An address template over the post's fields: `{id}`, `{link}`… */
    val url: String,
    val type: WebSourcePageType,
    /** A JSON path or HTML rule to the text's markup. */
    val html: String,
)

/**
 * A web source file, read and checked: a site's lists, and where each post's
 * fields sit in them (docs/web-sources.md). It is data, never code: it can
 * only read the [hosts] it lists, over HTTPS.
 */
data class WebSourceDefinition(
    val id: String,
    val version: Long,
    val name: String,
    val description: String,
    val homepage: HttpUrl,
    /** `0xFFRRGGBB`, the source's mark's colour; null for the theme's. */
    val color: Long?,
    val icon: HttpUrl?,
    val hosts: List<String>,
    val layout: WebSourceLayout,
    val feeds: List<WebSourceList>,
    val search: WebSourceList?,
    val article: WebSourceArticle?,
    /** The file as read, so it can be stored and read again. */
    val raw: JsonObject,
) {
    /** Whether this source may contact [url]. */
    fun allows(url: HttpUrl): Boolean = WebSources.hostAllowed(hosts, url)

    /** The feed, or the search (`search`), named [listId]. */
    fun list(listId: String): WebSourceList? = (feeds + listOfNotNull(search)).firstOrNull { it.id == listId }
}

/** Why a file was refused, to tell the reader. */
enum class WebSourceRefusal { UNREADABLE, NEWER_FORMAT, INVALID, INSECURE, TOO_LARGE }

/** A source file as read: the source, or why it was refused and where (the key or limit, as written in the file). */
sealed interface WebSourceFile {
    data class Read(val source: WebSourceDefinition) : WebSourceFile
    data class Refused(val refusal: WebSourceRefusal, val detail: String) : WebSourceFile
}

/**
 * Reads and checks web source files. A file that breaks a rule is refused
 * whole, with the reason and the key it broke: a source is either exactly
 * what it says or not installed.
 */
object WebSources {
    /** How the network names itself in a [com.aimdi.xtanative.networks.NetworkRequestException]. */
    const val NETWORK = "websources"
    const val FORMAT = 1
    const val MAX_FILE_BYTES = 64 * 1024
    const val MAX_LISTS = 12
    const val MAX_HOSTS = 8
    const val MAX_RULE_LENGTH = 300

    private val sourceId = Regex("^[a-z0-9][a-z0-9-]{1,39}$")
    private val listId = Regex("^[a-z0-9][a-z0-9-]{0,39}$")
    private val hostName = Regex("^(?:\\*\\.)?(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
    private val hexColor = Regex("^#[0-9A-Fa-f]{6}$")
    private val fieldNames = WebSourceField.entries.map { it.key }.toSet()

    private class Refusal(val refusal: WebSourceRefusal, val detail: String) : Exception(detail)

    /** [body], a source file as downloaded. */
    fun read(body: String): WebSourceFile {
        if (body.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES) return WebSourceFile.Refused(WebSourceRefusal.TOO_LARGE, "64 KB")
        val decoded = runCatching { Json.parseToJsonElement(body) }.getOrNull()
            ?: return WebSourceFile.Refused(WebSourceRefusal.UNREADABLE, "")
        return fromJson(decoded)
    }

    /** [decoded], a source file already decoded, such as a stored copy. */
    fun fromJson(decoded: JsonElement?): WebSourceFile {
        val file = decoded as? JsonObject ?: return WebSourceFile.Refused(WebSourceRefusal.UNREADABLE, "")
        return try {
            WebSourceFile.Read(definition(J(file), file))
        } catch (refused: Refusal) {
            WebSourceFile.Refused(refused.refusal, refused.detail)
        }
    }

    /** Whether [id] is a well-formed source id: 2 to 40 of `a-z`, `0-9` and `-`, not starting with `-`. */
    fun isSourceId(id: String?): Boolean = id != null && sourceId.matches(id)

    /**
     * Whether a source listing [hosts] may contact [url]: HTTPS on the
     * standard port, no credentials, and a listed host (`*.example.com`
     * takes its subdomains and itself).
     */
    fun hostAllowed(hosts: List<String>, url: HttpUrl): Boolean {
        if (url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty() || url.port != 443) return false
        val host = url.host.lowercase()
        return host.isNotEmpty() && hosts.any { allowed ->
            if (allowed.startsWith("*.")) host == allowed.substring(2) || host.endsWith(allowed.substring(1)) else host == allowed
        }
    }

    // Checked in XTA's order, so a file with several mistakes is refused for the same one.
    private fun definition(file: J, raw: JsonObject): WebSourceDefinition {
        checkFormat(file["format"].integer)
        val hosts = hosts(file["hosts"])
        val layout = enumNamed(WebSourceLayout.entries, file["layout"].string, "layout")
        val feeds = feeds(file, hosts, layout)
        return WebSourceDefinition(
            id = matching(file["id"].string, sourceId, "id"),
            version = file["version"].integer?.takeIf { it > 0 } ?: refuse("version"),
            name = text(file["name"].string, "name", max = 40),
            description = if (file["description"].exists) text(file["description"].string, "description", max = 200) else "",
            homepage = onHosts(file["homepage"].string, hosts, "homepage"),
            color = color(file["color"]),
            icon = if (file["icon"].exists) onHosts(file["icon"].string, hosts, "icon") else null,
            hosts = hosts,
            layout = layout,
            feeds = feeds,
            search = if (file["search"].exists) list(file["search"], file, hosts, layout, "search", search = true) else null,
            article = if (file["article"].exists) article(file["article"], file) else null,
            raw = raw,
        )
    }

    private fun checkFormat(format: Long?) {
        if (format != null && format > FORMAT) throw Refusal(WebSourceRefusal.NEWER_FORMAT, "format $format")
        if (format != FORMAT.toLong()) refuse("format")
    }

    private fun hosts(hosts: J): List<String> {
        val names = hosts.list.map { it.string?.lowercase() }
        if (names.isEmpty()) refuse("hosts")
        if (names.size > MAX_HOSTS) throw Refusal(WebSourceRefusal.TOO_LARGE, "hosts: $MAX_HOSTS")
        return names.mapIndexed { index, name -> matching(name, hostName, "hosts[$index]") }
    }

    private fun feeds(file: J, hosts: List<String>, layout: WebSourceLayout): List<WebSourceList> {
        val feeds = file["feeds"].list
        if (feeds.isEmpty()) refuse("feeds")
        if (feeds.size > MAX_LISTS) throw Refusal(WebSourceRefusal.TOO_LARGE, "feeds: $MAX_LISTS")
        val lists = feeds.mapIndexed { index, feed -> list(feed, file, hosts, layout, "feeds[$index]") }
        val ids = HashSet<String>()
        lists.forEachIndexed { index, list -> if (!ids.add(list.id)) refuse("feeds[$index].id") }
        return lists
    }

    private fun list(list: J, file: J, hosts: List<String>, layout: WebSourceLayout, key: String, search: Boolean = false): WebSourceList {
        val type = enumNamed(WebSourcePageType.entries, list["type"].string ?: file["type"].string, "$key.type")
        val items = list["items"].string ?: file["items"].string ?: ""
        if (type != WebSourcePageType.RSS && !validRule(items, type, items = true)) refuse("$key.items")
        val fields = if (type == WebSourcePageType.RSS) emptyMap() else fields(list, file, type, layout, key)
        val next = list["next"].string
        if (next != null && !validRule(next, type)) refuse("$key.next")
        return WebSourceList(
            id = if (search) "search" else matching(list["id"].string, listId, "$key.id"),
            name = if (search) "search" else text(list["name"].string, "$key.name", max = 40),
            url = listUrl(list["url"].string, hosts, "$key.url", search),
            type = type,
            items = items,
            fields = fields,
            firstPage = firstPage(list["first_page"], "$key.first_page"),
            next = next,
        )
    }

    private fun firstPage(value: J, key: String): Int =
        if (!value.exists) 1 else value.integer?.takeIf { it in 0..Int.MAX_VALUE }?.toInt() ?: refuse(key)

    private fun listUrl(template: String?, hosts: List<String>, key: String, search: Boolean): String {
        val placeholders = WebSourcePaths.templatePlaceholders(template.orEmpty())
        val allowed = setOf("page", "cursor") + if (search) setOf("query") else emptySet()
        if (template == null || placeholders.any { it !in allowed }) refuse(key)
        if (search && "query" !in placeholders) refuse(key)
        val sample = WebSourcePaths.fillTemplate(template, encode = true) { if (it == "cursor") "x" else "1" }
        onHosts(sample, hosts, key)
        return template
    }

    /** The file's fields, then the list's over them; each a rule this page type can read, naming and finding a post. */
    private fun fields(list: J, file: J, type: WebSourcePageType, layout: WebSourceLayout, key: String): Map<WebSourceField, String> {
        val merged = LinkedHashMap<WebSourceField, String>()
        for (source in listOf(file["fields"], list["fields"])) {
            WebSourceField.entries.forEach { field -> source[field.key].string?.let { merged[field] = it } }
        }
        merged.forEach { (field, rule) -> if (!validRule(rule, type)) refuse("$key.fields.${field.key}") }
        val named = WebSourceField.TITLE in merged || WebSourceField.TEXT in merged
        val found = WebSourceField.LINK in merged || WebSourceField.ID in merged
        val pictured = WebSourceField.IMAGE in merged || WebSourceField.IMAGES in merged
        if (!named || !found || (layout == WebSourceLayout.GALLERY && !pictured)) refuse("$key.fields")
        return merged
    }

    /** Whether [rule] can be read from a [type] page: a path or selector, or a template whose every placeholder is one. */
    private fun validRule(rule: String, type: WebSourcePageType, items: Boolean = false): Boolean {
        if (rule.isEmpty() || rule.length > MAX_RULE_LENGTH) return false
        if (!items && WebSourcePaths.isTemplate(rule)) return WebSourcePaths.templatePlaceholders(rule).all { validRule(it, type) }
        return when (type) {
            WebSourcePageType.JSON -> WebSourcePaths.isJsonPath(rule)
            WebSourcePageType.HTML -> WebSourceSelectors.htmlRule(rule).let {
                WebSourceSelectors.isCssSelector(it.selector) && (!items || it.attribute == null)
            }
            WebSourcePageType.RSS -> true
        }
    }

    private fun article(article: J, file: J): WebSourceArticle {
        val type = enumNamed(WebSourcePageType.entries, article["type"].string ?: file["type"].string, "article.type")
        val url = article["url"].string.orEmpty()
        val html = article["html"].string.orEmpty()
        val placeholders = WebSourcePaths.templatePlaceholders(url)
        if (placeholders.isEmpty() || placeholders.any { it !in fieldNames }) refuse("article.url")
        if (type == WebSourcePageType.RSS || !validRule(html, type)) refuse("article.html")
        return WebSourceArticle(url, type, html)
    }

    private fun matching(value: String?, pattern: Regex, key: String): String =
        value?.takeIf { pattern.matches(it) } ?: refuse(key)

    private fun text(value: String?, key: String, max: Int): String =
        value?.trim()?.takeIf { it.isNotEmpty() && it.length <= max } ?: refuse(key)

    /** A value named in the file, or the first when it names none; a name that is not one is refused. */
    private fun <T : Enum<T>> enumNamed(values: List<T>, name: String?, key: String): T =
        values.firstOrNull { it.name.lowercase() == (name ?: values.first().name.lowercase()) } ?: refuse(key)

    private fun color(color: J): Long? =
        if (!color.exists) null else 0xFF000000L or matching(color.string, hexColor, "color").substring(1).toLong(16)

    private fun onHosts(address: String?, hosts: List<String>, key: String): HttpUrl {
        if (address.isNullOrEmpty()) refuse(key)
        val url = address.toHttpUrlOrNull()
        if (url == null || !hostAllowed(hosts, url)) throw Refusal(WebSourceRefusal.INSECURE, key)
        return url
    }

    private fun refuse(key: String): Nothing = throw Refusal(WebSourceRefusal.INVALID, key)
}

/** A whole number as XTA's Json reads one: a number (fractions dropped), or a string of digits. */
internal val J.integer: Long?
    get() {
        val primitive = raw as? JsonPrimitive ?: return null
        return when {
            primitive.isString -> primitive.content.toLongOrNull()
            else -> primitive.longOrNull ?: primitive.doubleOrNull?.takeIf { it.isFinite() }?.toLong()
        }
    }
