package com.aimdi.xtanative.plugins.substack

import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.substack.Substack
import com.aimdi.xtanative.networks.substack.SubstackHosts
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Substack's Cloudflare answers some networks with its "Just a moment..."
 * check (403) on every route: nothing is wrong with the publication, and a
 * browser, which can pass the check, reads it. Such a failure says so and
 * offers the page in the browser.
 */
internal object SubstackFailures {
    fun turnedAway(error: Throwable?): Boolean =
        error is NetworkRequestException && error.network == Substack.NETWORK && error.code == 403

    /** The page a typed link, handle or name opens in the browser: the link itself, else its publication; null for neither. */
    fun pageOf(input: String): String? {
        val text = input.trim()
        return text.toHttpUrlOrNull()?.toString() ?: SubstackHosts.base(text)?.toString()
    }
}
