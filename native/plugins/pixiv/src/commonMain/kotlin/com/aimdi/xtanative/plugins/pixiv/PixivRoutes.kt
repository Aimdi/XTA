package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivLinkRef
import com.aimdi.xtanative.networks.pixiv.PixivLinks
import com.aimdi.xtanative.networks.pixiv.PixivSearchKind
import com.aimdi.xtanative.networks.pixiv.PixivUserListKind
import java.net.URLDecoder
import java.net.URLEncoder

/** The plugin's own screens, as routes on the app's back stack. */
internal sealed interface PixivRoute {
    /** A work; [shelf] names what was handed over with it (the list it was opened from, the copy it had). */
    data class Work(val id: Long, val shelf: String? = null) : PixivRoute

    /** A work's pages, from [page], read down or across. */
    data class Reader(val id: Long, val page: Int, val vertical: Boolean, val shelf: String? = null) : PixivRoute

    data class User(val id: Long, val tab: String? = null) : PixivRoute

    /** Whom [userId] follows, or who follows them; 0 is the reader. */
    data class Users(val kind: PixivUserListKind, val userId: Long) : PixivRoute

    data object RecommendedUsers : PixivRoute

    /** A work's or a novel's comments; [parent] names the comment whose replies are shown. */
    data class Comments(val kind: PixivKind, val id: Long, val parent: String? = null) : PixivRoute

    data class Search(val query: String, val kind: PixivSearchKind) : PixivRoute

    data class Series(val id: Long) : PixivRoute

    data class Novel(val id: Long, val shelf: String? = null) : PixivRoute

    data class NovelSeries(val id: Long) : PixivRoute

    data object PixivisionList : PixivRoute

    data class Pixivision(val id: Long, val language: String) : PixivRoute

    /** A pixiv.me short link, read for where it goes. */
    data class Short(val name: String) : PixivRoute

    data class History(val kind: PixivKind) : PixivRoute

    data object FavoriteTags : PixivRoute

    data object Mute : PixivRoute

    data object CopyTemplate : PixivRoute

    data object SignIn : PixivRoute

    /** The plugin's settings as a screen of its own, as More opens them. */
    data object Settings : PixivRoute

    /** A picture full screen, such as a profile's; [shelf] holds what it shows. */
    data class Picture(val shelf: String) : PixivRoute
}

internal object PixivRoutes {
    fun of(route: PixivRoute): String = when (route) {
        is PixivRoute.Work -> query("work", "id" to "${route.id}", "shelf" to route.shelf)
        is PixivRoute.Reader -> query("reader", "id" to "${route.id}", "page" to "${route.page}", "vertical" to flag(route.vertical), "shelf" to route.shelf)
        is PixivRoute.User -> query("user", "id" to "${route.id}", "tab" to route.tab)
        is PixivRoute.Users -> query("users", "kind" to route.kind.name.lowercase(), "id" to "${route.userId}")
        PixivRoute.RecommendedUsers -> "recommended-users"
        is PixivRoute.Comments -> query("comments", "kind" to route.kind.stored, "id" to "${route.id}", "shelf" to route.parent)
        is PixivRoute.Search -> query("search", "q" to route.query, "kind" to kindName(route.kind))
        is PixivRoute.Series -> query("series", "id" to "${route.id}")
        is PixivRoute.Novel -> query("novel", "id" to "${route.id}", "shelf" to route.shelf)
        is PixivRoute.NovelSeries -> query("novel-series", "id" to "${route.id}")
        PixivRoute.PixivisionList -> "pixivision"
        is PixivRoute.Pixivision -> query("pixivision", "id" to "${route.id}", "lang" to route.language)
        is PixivRoute.Short -> query("short", "name" to route.name)
        is PixivRoute.History -> query("history", "kind" to route.kind.stored)
        PixivRoute.FavoriteTags -> "favorite-tags"
        PixivRoute.Mute -> "mute"
        PixivRoute.CopyTemplate -> "copy-template"
        PixivRoute.SignIn -> "sign-in"
        PixivRoute.Settings -> "settings"
        is PixivRoute.Picture -> query("picture", "shelf" to route.shelf)
    }

    /** The screen [route] names; null for one this plugin does not have. */
    fun parse(route: String): PixivRoute? {
        val name = route.substringBefore('?')
        val args = route.substringAfter('?', "").split('&').filter { '=' in it }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        val id = args["id"]?.toLongOrNull()?.takeIf { it >= 0 }
        val shelf = args["shelf"]?.ifEmpty { null }
        return when (name) {
            "work" -> id?.takeIf { it > 0 }?.let { PixivRoute.Work(it, shelf) }
            "reader" -> id?.takeIf { it > 0 }?.let { PixivRoute.Reader(it, args["page"]?.toIntOrNull() ?: 0, args["vertical"] == "1", shelf) }
            "user" -> id?.takeIf { it > 0 }?.let { PixivRoute.User(it, args["tab"]) }
            "users" -> id?.let { PixivRoute.Users(listKind(args["kind"]), it) }
            "recommended-users" -> PixivRoute.RecommendedUsers
            "comments" -> id?.takeIf { it > 0 }?.let { PixivRoute.Comments(PixivKind.of(args["kind"]), it, shelf) }
            "search" -> PixivRoute.Search(args["q"].orEmpty(), searchKind(args["kind"]))
            "series" -> id?.takeIf { it > 0 }?.let { PixivRoute.Series(it) }
            "novel" -> id?.takeIf { it > 0 }?.let { PixivRoute.Novel(it, shelf) }
            "novel-series" -> id?.takeIf { it > 0 }?.let { PixivRoute.NovelSeries(it) }
            "pixivision" -> if (id == null) PixivRoute.PixivisionList else PixivRoute.Pixivision(id, args["lang"] ?: "en")
            "short" -> args["name"]?.ifEmpty { null }?.let { PixivRoute.Short(it) }
            "history" -> PixivRoute.History(PixivKind.of(args["kind"]))
            "favorite-tags" -> PixivRoute.FavoriteTags
            "mute" -> PixivRoute.Mute
            "copy-template" -> PixivRoute.CopyTemplate
            "sign-in" -> PixivRoute.SignIn
            "settings" -> PixivRoute.Settings
            "picture" -> shelf?.let { PixivRoute.Picture(it) }
            else -> null
        }
    }

    /** The screen of a Pixiv link: a work, a creator, a tag, a series, a novel, a Pixivision article, a pixiv.me link. Null for any other. */
    fun forUrl(url: String): String? {
        val text = url.trim()
        if (!text.startsWith("https://") && !text.startsWith("http://") && !text.startsWith("pixiv://")) return null
        return PixivLinks.parse(text)?.let(::routeOf)?.let(::of)
    }

    /** The screen that shows what [ref] names. */
    fun routeOf(ref: PixivLinkRef): PixivRoute = when (ref) {
        is PixivLinkRef.Artwork -> PixivRoute.Work(ref.id)
        is PixivLinkRef.User -> PixivRoute.User(ref.id, ref.tab)
        is PixivLinkRef.Tag -> PixivRoute.Search(ref.tag, if (ref.novels) PixivSearchKind.NOVELS else PixivSearchKind.WORKS)
        is PixivLinkRef.ShortLink -> PixivRoute.Short(ref.name)
        is PixivLinkRef.Series -> PixivRoute.Series(ref.id)
        is PixivLinkRef.Novel -> PixivRoute.Novel(ref.id)
        is PixivLinkRef.NovelSeries -> PixivRoute.NovelSeries(ref.id)
        is PixivLinkRef.PixivisionPage -> PixivRoute.Pixivision(ref.id, ref.language)
    }

    private fun query(name: String, vararg args: Pair<String, String?>): String {
        val present = args.filter { it.second != null }
        if (present.isEmpty()) return name
        return name + "?" + present.joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }
    }

    private fun flag(on: Boolean): String = if (on) "1" else "0"

    private fun kindName(kind: PixivSearchKind): String = if (kind.isWorks) "works" else "novels"

    private fun searchKind(name: String?): PixivSearchKind = if (name == "novels") PixivSearchKind.NOVELS else PixivSearchKind.WORKS

    private fun listKind(name: String?): PixivUserListKind = if (name == "followers") PixivUserListKind.FOLLOWERS else PixivUserListKind.FOLLOWING
}
