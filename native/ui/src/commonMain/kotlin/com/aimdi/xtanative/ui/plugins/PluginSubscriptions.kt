package com.aimdi.xtanative.ui.plugins

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * One account a plugin follows, as the app's Subscriptions list shows it
 * beside X's (XTA's subscription of a network, subscription_look.dart): its
 * mark and [avatarUrl], [name] over [subtitle], Add to group, Unfollow, and a
 * tap opening [route], the plugin's own screen for it ([XtaPlugin.screen]).
 *
 * [id] is the account as the plugin's group members name it
 * ([PluginHost.addToGroup]); [handle] is what the list's search and its
 * order by handle read, as XTA's screen name; [followedAt] (epoch
 * milliseconds, 0 when not known) its order by date. [backupId] is its id in
 * the rows of the plugin's backup sections (XTA's subscription id, which
 * XTA's groups hold), where it is not [id]: Bluesky reads an account by its
 * DID, XTA keeps its handle. [unfollow] unfollows it in the plugin, which
 * takes it out of its groups as it does ([PluginHost.unfollowed]).
 */
class PluginSubscription(
    val id: String,
    val name: String,
    val handle: String = id,
    val subtitle: String = "@$handle",
    val avatarUrl: String? = null,
    val route: String? = null,
    val followedAt: Long = 0,
    val backupId: String = id,
    val unfollow: suspend () -> Unit,
) {
    companion object {
        /** The site [url] is on, as XTA's subscriptions name a publication or a feed under its name; [url] itself when it names none. */
        fun siteOf(url: String): String = url.toHttpUrlOrNull()?.host ?: url
    }
}
