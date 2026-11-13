package com.aimdi.xtanative.plugins.rss

import com.aimdi.xtanative.networks.rss.RssException
import com.aimdi.xtanative.ui.plugins.PluginText

/** Why an address could not be followed, when the reading layer knows: not an address, or no feed there; null for a failed read. */
internal object RssAddFailures {
    fun reasonOf(error: Throwable?): PluginText? = when ((error as? RssException)?.reason) {
        RssException.Reason.NOT_A_URL -> Strings.add_not_a_url
        RssException.Reason.NO_FEED -> Strings.add_no_feed
        null -> null
    }
}
