package com.aimdi.xtanative.ui.plugins

import java.time.Instant

/**
 * How a group's member points at an account the group does not hold, as a
 * group's Discover says it (XTA's DiscoverySignal): a repost, a quote or the
 * network's own pick of a similar account weighs most, a follow or a reply
 * less, a mention least.
 */
enum class DiscoverySignal { REPOSTED, QUOTED, REPLIED, MENTIONED, FOLLOWED, SUGGESTED, RELATED }

/**
 * An account one of the plugin's group members points at
 * ([PluginDiscovery.around]): [id] as the plugin's group members name it
 * ([PluginHost.addToGroup]), [handle] shown as "@handle", [name], its face and
 * [bio]; [signal] how the member pointed at it, and [pointedAt] when. [text]
 * is what the row shows of the post that shows it ([post], drawn whole when
 * the reader opens the row; [postUrl] its link, [postedAt] when it was
 * written); [route] the plugin's own screen for the account; [follow] follows
 * it in the plugin, as adding it to a group does first. Nothing is followed on
 * the network.
 */
class PluginDiscovered(
    val id: String,
    val handle: String,
    val name: String,
    val signal: DiscoverySignal,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val text: String = "",
    val postUrl: String? = null,
    val postedAt: Instant? = null,
    val pointedAt: Instant? = null,
    val post: NetworkPost? = null,
    val route: String? = null,
    val follow: suspend () -> Unit = {},
)

/**
 * A plugin's part of a group's Discover (XTA's group_discovery_sources.dart):
 * the accounts its members in the group point at, as its network tells (the
 * accounts it suggests for one, what one reposted or quoted, the creators it
 * relates to one). The app asks a rotating few members on each open, the
 * least lately asked first, [perScan] of them and twice that on Scan more;
 * keeps each answer ten minutes (while [scope] stays the same); leaves out
 * the group's members and what the reader follows; and ranks what every
 * network found together, each account's members once.
 */
abstract class PluginDiscovery {
    /** How many members one open asks about. */
    open val perScan: Int get() = 6

    /** What the answers depend on besides the member (the account signed in, the reader's filters): a change asks again. */
    open val scope: String get() = ""

    /** The accounts [account] (one of the plugin's, as a group holds it) points at; throws when it could not be read. */
    abstract suspend fun around(account: String): List<PluginDiscovered>
}
