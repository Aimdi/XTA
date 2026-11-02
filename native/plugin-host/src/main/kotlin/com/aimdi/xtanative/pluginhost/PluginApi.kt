package com.aimdi.xtanative.pluginhost

/**
 * The plugin API versions this app loads: the `:plugin-api` it was built
 * with, and any older one it still keeps working. A plugin built for another
 * is offered as "needs a newer XTA Native" (or waits for its own update), never
 * loaded.
 */
class PluginApi(val versions: Set<Int>) {
    constructor(vararg versions: Int) : this(versions.toSet())

    init {
        require(versions.isNotEmpty()) { "an app loads at least one plugin API version" }
    }

    fun supports(apiVersion: Int): Boolean = apiVersion in versions

    /** Why a plugin built for [apiVersion] cannot load here, or null when it can. */
    fun refusal(apiVersion: Int): PluginFailure? = when {
        supports(apiVersion) -> null
        apiVersion > versions.max() -> PluginFailure.NeedsNewerApp(apiVersion)
        else -> PluginFailure.NeedsNewerPlugin(apiVersion)
    }
}

/** Plugin versions in order: "1.0.10" after "1.0.9", "1.0" the same as "1.0.0", "1.1-beta" before "1.1". */
internal object PluginVersions {
    fun compare(a: String, b: String): Int {
        val left = a.split('.')
        val right = b.split('.')
        return (0 until maxOf(left.size, right.size))
            .map { comparePart(left.getOrElse(it) { "0" }, right.getOrElse(it) { "0" }) }
            .firstOrNull { it != 0 } ?: 0
    }

    private fun comparePart(a: String, b: String): Int {
        val byNumber = compareValues(numberOf(a), numberOf(b))
        val restA = a.dropWhile(Char::isDigit)
        val restB = b.dropWhile(Char::isDigit)
        return when {
            byNumber != 0 -> byNumber
            restA == restB -> 0
            // A release comes after its pre-releases: "1" after "1-beta".
            restA.isEmpty() -> 1
            restB.isEmpty() -> -1
            else -> restA.compareTo(restB)
        }
    }

    private fun numberOf(part: String): Long = part.takeWhile(Char::isDigit).toLongOrNull() ?: -1
}
