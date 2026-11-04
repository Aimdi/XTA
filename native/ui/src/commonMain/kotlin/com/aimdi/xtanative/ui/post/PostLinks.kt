package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.services.links.LinkOpening
import com.aimdi.xtanative.services.links.LinkRouter
import com.aimdi.xtanative.services.links.LinkSettings
import com.aimdi.xtanative.services.links.NativeRoute
import com.aimdi.xtanative.services.links.NetworkLinks
import com.aimdi.xtanative.services.links.XLink

/** Where a link tapped in a post leads. */
sealed interface PostLink {
    /** An X post, opened in XTA Native. */
    data class Post(val id: String) : PostLink

    /** An X profile, opened in XTA Native. */
    data class Profile(val screenName: String) : PostLink

    /** Anything else, for the web: [url] without its tracking when the reader asked for that. */
    data class Web(val url: String) : PostLink
}

/**
 * The order a post's links open in, as XTA's link_opening.dart: an X post or
 * profile opens here; a community, a list, a broadcast or Space, a Grok share
 * and every other address go to the web, cleaned of tracking unless the
 * reader turned that off. No other network's screens are here yet, so a link
 * one of them reads goes to the web too.
 */
object PostLinks {
    private val router = LinkRouter(NetworkLinks(), enabled = { false })

    fun route(url: String, cleanLinks: Boolean): PostLink {
        val route = router.route(url) as? NativeRoute.X
        return when (val link = route?.link) {
            is XLink.Post -> PostLink.Post(link.id)
            is XLink.Profile -> PostLink.Profile(link.screenName)
            else -> PostLink.Web(LinkOpening.prepare(url.trim(), LinkSettings(clean = cleanLinks)))
        }
    }
}

/**
 * The one function a post's links open through: [openPost] and [openProfile]
 * for X's, [web] for the rest. XTA's browser screen (ui.links) takes the
 * place of [web] when it lands; [cleanLinks] is read at each tap.
 */
fun linkOpener(
    openPost: (String) -> Unit,
    openProfile: (String) -> Unit,
    web: (String) -> Unit,
    cleanLinks: () -> Boolean,
): (String) -> Unit = { url ->
    when (val link = PostLinks.route(url, cleanLinks())) {
        is PostLink.Post -> openPost(link.id)
        is PostLink.Profile -> openProfile(link.screenName)
        is PostLink.Web -> web(link.url)
    }
}
