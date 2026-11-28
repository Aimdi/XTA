package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope

/**
 * A value kept as long as the screen it was made on stays on the back stack:
 * across recomposition, a home tab scrolled out of view and back, and
 * Android's configuration changes, as a view model is. [make] runs once per
 * [key] on that screen, with a scope that ends when the screen leaves the
 * back stack: what the value reads, it reads there.
 */
@Composable
fun <T : Any> rememberKept(key: String, make: (CoroutineScope) -> T): T {
    val kept: KeptValues = viewModel(key = KEPT) { KeptValues() }
    // An update brings the plugin's classes anew: what the old ones made is not handed to the new.
    val slot = KeptSlots.of(LocalKeptPlace.current, key, System.identityHashCode(make.javaClass.classLoader))
    @Suppress("UNCHECKED_CAST")
    return remember(kept, slot) { kept.values.getOrPut(slot) { make(kept.viewModelScope) } as T }
}

private const val KEPT = "xta-kept"

/**
 * Which of a screen's places a plugin's page is drawn in, where one screen
 * shows the same page twice: Home's timeline pinned beside For you and the
 * plugin's own page of Home's bar (the desktop column's row) each keep their
 * own values, so a section picked in one is not picked in the other. Null
 * for a screen's one place.
 */
internal val LocalKeptPlace = staticCompositionLocalOf<String?> { null }

/** Where [rememberKept] keeps a value. */
internal object KeptSlots {
    /** The slot of [key] in [place] (null: the screen's one place), made by the classes of [loader]. */
    fun of(place: String?, key: String, loader: Int): String = (place?.let { "$it|" } ?: "") + key + "@" + loader
}

/** The values [rememberKept] keeps for one screen. */
internal class KeptValues : ViewModel() {
    val values = HashMap<String, Any>()
}
