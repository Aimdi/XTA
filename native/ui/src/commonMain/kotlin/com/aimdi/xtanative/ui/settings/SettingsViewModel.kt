package com.aimdi.xtanative.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.Settings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

/**
 * Every settings page's state: the settings and the session, as they change.
 * A change is written through [AppGraph.keep], so leaving the page as it is
 * saved never loses it.
 */
class SettingsViewModel(val graph: AppGraph) : ViewModel() {
    val settings: StateFlow<Settings> =
        graph.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, graph.settings.state.value ?: Settings())

    val session: StateFlow<Session> =
        graph.sessions.state.filterNotNull().stateIn(viewModelScope, SharingStarted.Eagerly, graph.sessions.state.value ?: Session())

    fun <T> set(pref: Pref<T>, value: T) {
        graph.keep { graph.settings.set(pref, value) }
    }

    fun update(transform: (Settings) -> Settings) {
        graph.keep { graph.settings.update(transform) }
    }

    fun updateSession(transform: (Session) -> Session) {
        graph.keep { graph.sessions.update(transform) }
    }
}
