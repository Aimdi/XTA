package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The place on Home's strip for the networks plugins bring (XTA's plugin
 * timelines beside For you). The app has none of its own: the plugin host
 * fills this by providing [LocalHomeNetworks] around the app's content.
 * Which of them sit on the strip, in what order, is the reader's choice
 * (Settings' `home.feed_strip_plugins`), made in the strip's sheet.
 */
interface HomeNetworks {
    /** The installed, enabled networks that can sit on the strip, in the plugin registry's order. */
    val available: StateFlow<List<StripNetwork>>

    /** The network's mark, [size] square, beside its name on the strip and in the sheet. */
    @Composable
    fun Mark(id: String, size: Dp)

    /**
     * The network's timeline as a page of the strip. [toTop] grows each time
     * the reader asks for its top (its tab tapped again, the title tapped):
     * scroll up when it is past the last request answered.
     */
    @Composable
    fun Page(id: String, contentPadding: PaddingValues, toTop: Int)

    /** What its top bar's options menu offers; the reader may pin any to the bar itself. */
    @Composable
    fun options(id: String): List<NetworkOption>

    /** Whether it has posts the reader has not seen: the dot on its tab. */
    @Composable
    fun unread(id: String): Boolean

    /** Reads it again: the desktop's refresh button, F5 and Ctrl+R. */
    fun refresh(id: String)

    companion object {
        /** No plugin networks: Following and For you alone. */
        val None: HomeNetworks = object : HomeNetworks {
            override val available: StateFlow<List<StripNetwork>> = MutableStateFlow(emptyList())

            @Composable
            override fun Mark(id: String, size: Dp) = Unit

            @Composable
            override fun Page(id: String, contentPadding: PaddingValues, toTop: Int) = Unit

            @Composable
            override fun options(id: String): List<NetworkOption> = emptyList()

            @Composable
            override fun unread(id: String): Boolean = false

            override fun refresh(id: String) = Unit
        }
    }
}

/** One option of a network's top bar; [id] is what a pin keeps, so it stays the same between versions. */
class NetworkOption(val id: String, val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** The networks Home's strip can show; [HomeNetworks.None] until the plugin host provides its own. */
val LocalHomeNetworks = staticCompositionLocalOf { HomeNetworks.None }
