package com.aimdi.xtanative.ui.plugins

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A plugin's own screen as a route ([PluginHost.open], [XtaPlugin.screen]):
 * the screen's [name] and what it opens with, written `name?key=value&…`
 * with each value escaped, so any text survives the back stack.
 */
data class PluginRoute(val name: String, val args: Map<String, String>) {
    constructor(name: String, vararg args: Pair<String, String?>) : this(name, args.mapNotNull { (key, value) -> value?.let { key to it } }.toMap())

    /** The argument [key], when it is there and not blank. */
    operator fun get(key: String): String? = args[key]?.takeIf { it.isNotBlank() }

    override fun toString(): String =
        if (args.isEmpty()) name else name + "?" + args.entries.joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }

    companion object {
        /** The route [text] writes; arguments without a value are left out. */
        fun parse(text: String): PluginRoute {
            val args = text.substringAfter('?', "").split('&').filter { '=' in it }.associate { part ->
                part.substringBefore('=') to (runCatching { URLDecoder.decode(part.substringAfter('='), "UTF-8") }.getOrNull() ?: "")
            }
            return PluginRoute(text.substringBefore('?'), args)
        }
    }
}
