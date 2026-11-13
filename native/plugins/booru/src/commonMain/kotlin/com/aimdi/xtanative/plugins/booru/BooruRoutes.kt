package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruEngine
import com.aimdi.xtanative.networks.booru.BooruLinks
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruPostRef
import com.aimdi.xtanative.networks.booru.BooruPreset
import com.aimdi.xtanative.networks.booru.BooruSite
import com.aimdi.xtanative.networks.booru.BooruTagCategory
import java.net.URLDecoder
import java.net.URLEncoder

/** A site to ask, as a route names it: the post's own, or the one in use when none is named. */
internal data class SiteRef(val host: String, val engine: BooruEngine) {
    companion object {
        fun of(site: BooruSite): SiteRef = SiteRef(site.host, site.engine)
    }
}

/** The plugin's own screens, as routes on the app's back stack. */
internal sealed interface BooruRoute {
    /** A post a link named, read from its site. */
    data class Post(val ref: BooruPostRef) : BooruRoute

    /** The viewer over the posts of a list ([key], kept by [BooruViewers]) from [index]; [ref] reads the post alone when the list is gone. */
    data class Viewer(val key: String, val index: Int, val ref: BooruPostRef) : BooruRoute

    /** A tag's own page: an artist's, a character's or a series'. */
    data class Tag(val tag: String, val category: BooruTagCategory?, val site: SiteRef?) : BooruRoute

    /** A pool's posts in its own order. */
    data class Pool(val id: String, val name: String, val site: SiteRef?) : BooruRoute

    /** The tag search, opened on [query]; [edit] is a tag put back in the field to be changed. */
    data class Search(val query: String, val edit: String?) : BooruRoute
}

internal object BooruRoutes {
    fun post(ref: BooruPostRef): String = "post?" + query("host" to ref.host, "engine" to ref.engine.id, "id" to ref.id)

    fun viewer(key: String, index: Int, post: BooruPost): String =
        "view?" + query("key" to key, "index" to "$index", "host" to post.host, "engine" to post.engine.id, "id" to post.id)

    fun tag(tag: String, category: BooruTagCategory?, site: SiteRef?): String =
        "tag?" + query("name" to tag, "kind" to category?.name?.lowercase(), "host" to site?.host, "engine" to site?.engine?.id)

    fun pool(id: String, name: String, site: SiteRef?): String =
        "pool?" + query("id" to id, "name" to name, "host" to site?.host, "engine" to site?.engine?.id)

    fun search(query: String?, edit: String? = null): String = "search?" + query("q" to query, "edit" to edit)

    /** The screen [route] names; null for one this plugin does not have. */
    fun parse(route: String): BooruRoute? {
        val name = route.substringBefore('?')
        val values = route.substringAfter('?', "").split('&').filter { '=' in it }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        val site = site(values)
        val ref = site?.let { s -> values["id"]?.takeIf(String::isNotBlank)?.let { BooruPostRef(s.host, s.engine, it) } }
        return when (name) {
            "post" -> ref?.let(BooruRoute::Post)
            "view" -> ref?.let { BooruRoute.Viewer(values["key"].orEmpty(), values["index"]?.toIntOrNull() ?: 0, it) }
            "tag" -> values["name"]?.takeIf(String::isNotBlank)?.let { BooruRoute.Tag(it, category(values["kind"]), site) }
            "pool" -> values["id"]?.takeIf(String::isNotBlank)?.let { BooruRoute.Pool(it, values["name"] ?: it, site) }
            "search" -> BooruRoute.Search(values["q"].orEmpty(), values["edit"]?.takeIf(String::isNotBlank))
            else -> null
        }
    }

    /** The page of the post [ref] names on its host. */
    fun pageUrl(ref: BooruPostRef): String? = BooruPost(
        id = ref.id,
        host = ref.host,
        engine = ref.engine,
        tags = emptyList(),
        rating = null,
        score = null,
        width = 0,
        height = 0,
        previewUrl = null,
        sampleUrl = null,
        fileUrl = null,
        fileExt = null,
        source = null,
        createdAt = null,
    ).hostPageUrl

    /** The route of the screen showing [url]: a post's page on one of [sites], built in or added; null for any other link. */
    fun forUrl(url: String, sites: List<BooruPreset>): String? = BooruLinks.postRef(url.trim(), sites)?.let(::post)

    private fun site(values: Map<String, String>): SiteRef? {
        val host = values["host"]?.takeIf(String::isNotBlank) ?: return null
        val engine = BooruEngine.parse(values["engine"]) ?: return null
        return SiteRef(host, engine)
    }

    private fun category(name: String?): BooruTagCategory? = BooruTagCategory.entries.firstOrNull { it.name.lowercase() == name }

    private fun query(vararg values: Pair<String, String?>): String =
        values.filter { it.second != null }.joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }
}
