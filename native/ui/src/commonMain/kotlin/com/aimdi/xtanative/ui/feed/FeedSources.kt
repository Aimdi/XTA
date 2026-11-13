package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.NetworkPost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What a timeline asks of one plugin (XTA's sourceIdsFor): its accounts in
 * the group read ([members]), and with [followed] every account followed in
 * it too, as the Following feed and For you read a plugin whose switch is on;
 * [kinds] are the feed's reply and repost settings.
 */
data class SourceAsk(val members: List<String> = emptyList(), val followed: Boolean = false, val kinds: FeedKinds = FeedKinds.ALL) {
    /** The accounts asked: the members, then the followed ones, each once. */
    fun accounts(following: List<String>): List<String> = (members + if (followed) following else emptyList()).distinct()
}

/** Which plugins a timeline mixes in, and what it asks of each. */
object FeedSources {
    /**
     * What a timeline asks of the running plugins with a feed ([feeds]): of
     * each, its accounts among the timeline's members ([members]), and on a
     * home timeline ([home], Following or For you) its followed accounts while
     * [inHome] says its switch is on. A plugin asked for neither is left out,
     * so its posts leave the timeline with it.
     */
    fun asks(feeds: Set<String>, members: Map<String, List<String>>, home: Boolean, inHome: (String) -> Boolean, kinds: FeedKinds): Map<String, SourceAsk> =
        feeds.sorted().mapNotNull { id ->
            val ask = SourceAsk(members[id].orEmpty(), followed = home && inHome(id), kinds = kinds)
            if (ask.members.isEmpty() && !ask.followed) null else id to ask
        }.toMap()
}

/** The plugins' feeds as the shared timelines read them; the app's plugins provide them. */
interface PluginFeeds {
    /** The running plugins with a feed, by id, as they come and go. */
    val ids: StateFlow<Set<String>>

    /** What plugin [id] answers for [ask], with the accounts it follows where they are asked for; nothing once it is gone. */
    suspend fun posts(id: String, ask: SourceAsk, force: Boolean): List<NetworkPost>

    /** No plugins: the timelines are X's alone. */
    object None : PluginFeeds {
        override val ids: StateFlow<Set<String>> = MutableStateFlow(emptySet())

        override suspend fun posts(id: String, ask: SourceAsk, force: Boolean): List<NetworkPost> = emptyList()
    }
}
