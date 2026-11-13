package com.aimdi.xtanative.ui.plugins

/** Which of a network's posts a shared timeline takes (XTA's FeedPostKinds): the feed's reply and repost settings. */
data class FeedKinds(val replies: Boolean = true, val reposts: Boolean = true) {
    /**
     * Whether a post belongs in a timeline taking these: a repost follows the
     * repost setting whatever it shares, every other reply the reply setting,
     * a continuation of the author's own thread included.
     */
    fun takes(isReply: Boolean, isRepost: Boolean): Boolean = if (isRepost) reposts else replies || !isReply

    companion object {
        /** Everything, as For you takes a network's posts. */
        val ALL = FeedKinds()
    }
}

/**
 * A plugin's part of the app's shared timelines (XTA's SubscriptionSource):
 * the posts of the accounts it follows, mixed by time into Home's Following
 * and For you while its "show in the home timeline" switch is on
 * ([PluginSettings.inHomeFeed]), and of its accounts the reader put in a group
 * (members "<plugin id>:<account>", [PluginHost.addToGroup]), beside X's.
 * Nothing is asked of X for them, and nothing is written to the network.
 */
abstract class PluginFeed {
    /** The accounts followed in the plugin, as [posts] reads them and its group members name them. */
    abstract suspend fun followed(): List<String>

    /**
     * The newest posts of [accounts], newest first, as [kinds] takes them: a
     * page of each, read a few accounts at a time, and only a few of each,
     * so that one account does not own a timeline that X pages on and on. An
     * account that fails adds nothing; only when none answers does this
     * throw, and the app says so beside the posts of the other networks.
     * [force] reads past the plugin's caches, as a pull does. A post's place
     * is its [NetworkPost.createdAt]: one without it is left out.
     */
    abstract suspend fun posts(accounts: List<String>, kinds: FeedKinds, force: Boolean): List<NetworkPost>
}
