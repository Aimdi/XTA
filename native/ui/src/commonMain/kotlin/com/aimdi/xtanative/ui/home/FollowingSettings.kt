package com.aimdi.xtanative.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.feed.FeedKeys
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.group.FeedPrefs
import com.aimdi.xtanative.ui.group.GroupFeedUi
import com.aimdi.xtanative.ui.group.GroupSettingsActions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

/**
 * Following's order and rules as its bar, its filters and its Custom screen
 * show them (XTA's GroupModel for its group `-1`): the Following feed's own
 * settings in the session over the session's switches, the reader's language
 * filter, and the accounts it reads, for their post caps.
 */
class FollowingSettingsViewModel(graph: AppGraph) : ViewModel() {
    val actions = GroupSettingsActions(graph, FeedKeys.FOLLOWING, viewModelScope)

    val ui: StateFlow<GroupFeedUi> = combine(graph.sessions.state.filterNotNull(), graph.settings.settings, ::of)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupFeedUi())

    private fun of(session: Session, settings: Settings): GroupFeedUi {
        val feed = session.settingsOf(FeedKeys.FOLLOWING)
        val members = session.feedMembers.mapTo(HashSet()) { it.id }
        return GroupFeedUi(
            loaded = true,
            memberCount = members.size,
            settings = feed,
            includeReplies = feed.includeReplies ?: session.includeReplies,
            includeReposts = feed.includeReposts ?: session.includeReposts,
            prefs = FeedPrefs.of(settings),
            accounts = session.following.filter { it.id in members },
            postCaps = session.postCaps,
            session = session,
        )
    }
}
