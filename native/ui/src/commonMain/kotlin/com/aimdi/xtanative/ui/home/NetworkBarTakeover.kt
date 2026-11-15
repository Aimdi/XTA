package com.aimdi.xtanative.ui.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.aimdi.xtanative.ui.plugins.PluginClientBar
import com.aimdi.xtanative.ui.plugins.PluginHomeTab

/**
 * Which networks' own sections take over Home's bottom bar while their
 * timeline is shown on Home (the Reddit bottom bar canvas, option A): their
 * sections go to the bottom, and the app's other pages behind More, so the
 * top bar keeps only the network's button and its actions.
 */
internal object NetworkBarRules {
    /** The networks whose sections move to the bottom bar. */
    val NETWORKS: Set<String> = setOf("reddit")

    /** Whether [network]'s timeline, handing [sections] sections to Home, takes over the bottom bar: one of [NETWORKS], with two or more. */
    fun takesOver(network: String?, sections: Int): Boolean = network in NETWORKS && sections > 1

    /** The bar's places More lists: every one but Home's Feed, where the network's timeline already is. */
    fun moreSlots(slots: List<BarSlot>, bar: List<BarPage>): List<BarSlot> =
        slots.filter { slot -> slot !is BarSlot.Page || bar.getOrNull(slot.index) != BarPage.Feed }
}

/**
 * The network timeline Home shows, as its bottom bar needs it: which network
 * ([network]) and what its plugin hands Home's bar ([client]). Home's Feed
 * page sets both while a network's timeline is shown and clears them when
 * it is not.
 */
@Stable
internal class NetworkBarTakeover {
    var network: String? by mutableStateOf(null)
    var client: PluginClientBar? by mutableStateOf(null)

    /** The network's sections, as its plugin hands them now. */
    val sections: List<PluginHomeTab> get() = client?.content?.tabs?.value.orEmpty()

    /** The plugin's own colour for the section shown, if it names one. */
    val accent: Color? get() = client?.content?.accent?.value

    /** Whether the sections are on the bottom bar now. */
    val active: Boolean get() = NetworkBarRules.takesOver(network, sections.size)

    fun show(network: String?, client: PluginClientBar?) {
        this.network = network
        this.client = client
    }
}

/** Home's bottom-bar takeover, from Home's body to the Feed page that fills it; null outside Home. */
internal val LocalNetworkBarTakeover = staticCompositionLocalOf<NetworkBarTakeover?> { null }
