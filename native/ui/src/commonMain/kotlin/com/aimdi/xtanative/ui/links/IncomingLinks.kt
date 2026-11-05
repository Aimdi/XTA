package com.aimdi.xtanative.ui.links

import com.aimdi.xtanative.services.links.SharedLinks
import com.aimdi.xtanative.services.links.XLink
import com.aimdi.xtanative.services.links.XLinks
import kotlinx.coroutines.CancellationException

/** Where a link another app opens here goes. */
sealed interface IncomingStep {
    /** A plugin's or a network's screen took it. */
    data object Opened : IncomingStep

    /** Not X's to read (a Pixiv page no network opened): out to the web. */
    data class Outside(val url: String) : IncomingStep

    /** An X screen: a post, a profile, a community, a list or the live player. */
    data class Screen(val link: XLink, val url: String) : IncomingStep

    /** A shared Grok conversation, which only X's page shows: opened as a post's link is. */
    data class PostLink(val url: String) : IncomingStep

    /** Nothing in the app reads it: the reader is asked what to do with [url]. */
    data class Unable(val url: String) : IncomingStep
}

/**
 * The route of a link another app opens here (main.dart's handleInitialLink):
 * a plugin or a network first, a page X's reader should not read out to the
 * web, a t.co short link followed, then X's screens, the live player for a
 * broadcast or a Space, a Grok share as a post's link, and XTA's "Unable to
 * open link" for anything else.
 */
object IncomingLinks {
    /** Where the "Report" of "Unable to open link" leads. */
    const val ISSUES_URL = "https://github.com/Aimdi/XTA/issues"

    /**
     * [openOnNetwork] opens [url] on a plugin's or a network's screen and
     * answers whether one did; [resolve] follows a t.co short link to the X
     * link it leads to, null when it leads nowhere or off X.
     */
    suspend fun step(url: String, openOnNetwork: (String) -> Boolean, resolve: suspend (String) -> String?): IncomingStep {
        if (openOnNetwork(url)) return IncomingStep.Opened
        if (!SharedLinks.readsAsXLink(url)) return IncomingStep.Outside(url)
        val target = if (LinkSteps.hostOf(url) == "t.co") followed(url, resolve) else url
        target ?: return IncomingStep.Unable(url)
        return when (val link = XLinks.parse(target)) {
            is XLink.Post, is XLink.Profile, is XLink.Community, is XLink.XList, is XLink.Live -> IncomingStep.Screen(link, target)
            is XLink.GrokShare -> IncomingStep.PostLink(link.url)
            XLink.Unknown -> IncomingStep.Unable(url)
        }
    }

    /**
     * Opens [step] on [screens]: a community, a list or a live room the app
     * has no screen for leaves for the web ([outside]); [postLink] opens a
     * page as a post's link, and [unable] asks the reader.
     */
    fun open(step: IncomingStep, screens: LinkScreens, outside: (String) -> Unit, postLink: (String) -> Unit, unable: (String) -> Unit) {
        when (step) {
            IncomingStep.Opened -> Unit
            is IncomingStep.Outside -> outside(step.url)
            is IncomingStep.PostLink -> postLink(step.url)
            is IncomingStep.Unable -> unable(step.url)
            is IncomingStep.Screen -> when (val link = step.link) {
                is XLink.Post -> screens.post(link.id)
                is XLink.Profile -> screens.profile(link.screenName)
                is XLink.Community -> if (!screens.community(link.id, link.about)) outside(step.url)
                is XLink.XList -> if (!screens.list(link.id)) outside(step.url)
                is XLink.Live -> if (!screens.live(link)) outside(link.url)
                else -> unable(step.url)
            }
        }
    }

    private suspend fun followed(url: String, resolve: suspend (String) -> String?): String? = try {
        resolve(url)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}
