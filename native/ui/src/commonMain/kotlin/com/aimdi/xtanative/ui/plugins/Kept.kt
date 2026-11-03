package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    val slot = key + "@" + System.identityHashCode(make.javaClass.classLoader)
    @Suppress("UNCHECKED_CAST")
    return remember(kept, slot) { kept.values.getOrPut(slot) { make(kept.viewModelScope) } as T }
}

private const val KEPT = "xta-kept"

/** The values [rememberKept] keeps for one screen. */
internal class KeptValues : ViewModel() {
    val values = HashMap<String, Any>()
}
