package com.aimdi.xtanative.plugins.mastodon

import com.aimdi.xtanative.networks.mastodon.MastodonLink
import com.aimdi.xtanative.networks.mastodon.MastodonPost
import com.aimdi.xtanative.networks.mastodon.MastodonUrls
import com.aimdi.xtanative.ui.plugins.PluginRoute

/** A post a screen opens: its address, its author, and the id its card had, which is local to the instance that served it. */
internal data class MastodonTarget(val url: String, val acct: String, val id: String) {
    /** Enough of the post to find it again, while the rest is read. */
    fun seed(): MastodonPost = MastodonPost(id = id, acct = acct, authorName = acct.substringBefore('@'), text = "", url = url)

    companion object {
        fun of(post: MastodonPost) = MastodonTarget(post.url, post.acct, post.id)
    }
}

/** The plugin's own screens. */
internal sealed interface MastodonRoute {
    data class Thread(val target: MastodonTarget) : MastodonRoute

    data class Profile(val acct: String) : MastodonRoute

    data class Tag(val name: String) : MastodonRoute

    data class Reposts(val target: MastodonTarget) : MastodonRoute

    data class Quotes(val target: MastodonTarget) : MastodonRoute

    data object Saved : MastodonRoute
}

/** The plugin's screens as routes on the app's back stack, and the Mastodon links it opens itself. */
internal object MastodonRoutes {
    private const val THREAD = "thread"
    private const val PROFILE = "profile"
    private const val TAG = "tag"
    private const val REPOSTS = "reposts"
    private const val QUOTES = "quotes"
    private const val SAVED = "saved"

    fun thread(post: MastodonPost): String = target(THREAD, MastodonTarget.of(post))

    fun profile(acct: String): String = PluginRoute(PROFILE, "acct" to acct).toString()

    fun tag(name: String): String = PluginRoute(TAG, "name" to name.removePrefix("#")).toString()

    fun reposts(post: MastodonPost): String = target(REPOSTS, MastodonTarget.of(post))

    fun quotes(post: MastodonPost): String = target(QUOTES, MastodonTarget.of(post))

    fun saved(): String = SAVED

    /** The screen [route] names; null for one this plugin does not have. */
    fun parse(route: String): MastodonRoute? {
        val parsed = PluginRoute.parse(route)
        val target = targetOf(parsed)
        return when (parsed.name) {
            THREAD -> target?.let { MastodonRoute.Thread(it) }
            REPOSTS -> target?.let { MastodonRoute.Reposts(it) }
            QUOTES -> target?.let { MastodonRoute.Quotes(it) }
            PROFILE -> parsed["acct"]?.let { MastodonRoute.Profile(it) }
            TAG -> parsed["name"]?.let { MastodonRoute.Tag(it) }
            SAVED -> MastodonRoute.Saved
            else -> null
        }
    }

    /** The screen showing [url], a profile or a post on one of [hosts] (the reader's instances and the built-in ones), as XTA opens them. */
    fun forUrl(url: String, hosts: Set<String>): String? = when (val link = MastodonUrls.link(url, hosts)) {
        is MastodonLink.Profile -> profile(link.acct)
        is MastodonLink.Status -> target(THREAD, MastodonTarget(link.url, link.acct, link.statusId))
        null -> null
    }

    private fun target(name: String, target: MastodonTarget): String =
        PluginRoute(name, "url" to target.url, "acct" to target.acct, "id" to target.id).toString()

    private fun targetOf(route: PluginRoute): MastodonTarget? {
        val url = route["url"] ?: return null
        val acct = route["acct"] ?: return null
        return MastodonTarget(url, acct, route["id"].orEmpty())
    }
}
