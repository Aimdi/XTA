package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.feed.FeedKeys
import com.aimdi.xtanative.core.feed.FeedSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How Following's bar fits Posts | Media beside its controls (home_timeline_controls.dart's HomeFollowingBarLayout). */
enum class BarFit {
    /** One row, as XTA draws it. */
    ONE_ROW,

    /** One row with the tabs closer together. */
    COMPACT,

    /** The tabs under the controls: even closer they do not fit. */
    TWO_ROWS,
}

/** Following's one bar (home_timeline_controls.dart, home-following-bar.md). */
object FollowingBar {
    /** The bar's height, a row's in two rows, in dp. */
    const val HEIGHT = 48f

    /** A tab's padding on either side, and the closer fit's. */
    const val TAB_PADDING = 14f
    const val COMPACT_PADDING = 8f

    /** Every control is a 48 dp target; the bar is inset 4 dp at either end. */
    const val TOUCH_TARGET = 48f
    const val INSET = 4f

    /** The feed whose bar this is: the media choice is kept under its key. */
    const val MEDIA_KEY = FeedKeys.FOLLOWING

    /**
     * The fit for a bar [width] dp wide holding the order button and [actions]
     * more buttons, with Posts and Media measuring [labels] dp together in
     * their bold, widest state.
     */
    fun fit(width: Float, labels: Float, actions: Int): BarFit {
        val controls = INSET + (1 + actions) * TOUCH_TARGET + INSET
        fun tabs(padding: Float) = labels + 2 * 2 * padding
        return when {
            tabs(TAB_PADDING) + controls <= width -> BarFit.ONE_ROW
            tabs(COMPACT_PADDING) + controls <= width -> BarFit.COMPACT
            else -> BarFit.TWO_ROWS
        }
    }

    /** The bar's height for [fit], in dp. */
    fun height(fit: BarFit): Float = if (fit == BarFit.TWO_ROWS) 2 * HEIGHT else HEIGHT

    /** The buttons beside the order: the source picker, the search of the loaded posts and Home feed accounts, and refresh on the desktop. */
    fun actions(desktop: Boolean): Int = if (desktop) 4 else 3

    /** The orders the menu offers, in XTA's order. */
    val ORDERS: List<FeedSort> = listOf(FeedSort.RECENT, FeedSort.POPULAR, FeedSort.CUSTOM)

    /** Picking Custom opens its rules too, as XTA does once Custom is on. */
    fun opensCustom(picked: FeedSort): Boolean = picked == FeedSort.CUSTOM
}

/** The sheet that picks Home's timeline (home_timeline_picker.dart, home_source_picker.dart). */
object SourcePicker {
    /** The search field shows once there are more rows than this, or a search is typed. */
    const val SEARCH_AFTER = 8

    fun hasSearch(rows: Int, query: String): Boolean = rows > SEARCH_AFTER || query.isNotEmpty()

    /** Whether every word of [query] is in [label], whatever the case. */
    fun matches(label: String, query: String): Boolean {
        val text = label.lowercase()
        return query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.all { it in text }
    }

    /** The timelines the sheet offers: Following, For you and the networks, in the strip's order; groups have a section of their own. */
    fun sources(tabs: List<HomeTab>): List<HomeTab> = tabs.filter { it !is HomeTab.Group }
}

/**
 * What another screen asks Home to show (XTA's FeedTabStore, which a group's
 * title menu sets before going back to Home): a timeline, by its tab's key.
 * Home takes the request once. And what Home asks of Discover (XTA's
 * SearchScopeStore): reached from a network's page on the bar, it searches
 * that network.
 */
object HomeRequests {
    private val asked = MutableStateFlow<String?>(null)
    private val scope = MutableStateFlow<String?>(null)

    val tab: StateFlow<String?> = asked.asStateFlow()

    /** The network Discover is asked to search, by plugin id, until it takes the request. */
    val searchScope: StateFlow<String?> = scope.asStateFlow()

    fun show(tabKey: String) {
        asked.value = tabKey
    }

    fun taken(tabKey: String) {
        asked.compareAndSet(tabKey, null)
    }

    fun searchIn(networkId: String) {
        scope.value = networkId
    }

    fun searchScopeTaken(networkId: String) {
        scope.compareAndSet(networkId, null)
    }
}
