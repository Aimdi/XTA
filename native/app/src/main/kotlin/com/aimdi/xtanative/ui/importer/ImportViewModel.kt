package com.aimdi.xtanative.ui.importer

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.backup.XtaBackup
import com.aimdi.xtanative.data.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportUi {
    data object Idle : ImportUi
    data object Reading : ImportUi
    data class Ready(val backup: XtaBackup) : ImportUi
    data object NotABackup : ImportUi
    data object Done : ImportUi
}

/** Reads a file XTA exported and, once confirmed, takes its sessions and follows. */
class ImportViewModel(private val graph: AppGraph) : ViewModel() {
    private val state = MutableStateFlow<ImportUi>(ImportUi.Idle)
    val ui: StateFlow<ImportUi> = state.asStateFlow()

    fun read(resolver: ContentResolver, uri: Uri) {
        state.value = ImportUi.Reading
        viewModelScope.launch {
            val backup = withContext(Dispatchers.IO) {
                runCatching { resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()?.let(XtaBackup::parse)
            }
            state.value = if (backup == null || backup.isEmpty) ImportUi.NotABackup else ImportUi.Ready(backup)
        }
    }

    fun use(backup: XtaBackup) {
        viewModelScope.launch {
            graph.sessions.update { it.importing(backup) }
            graph.clearFeedCaches()
            state.value = ImportUi.Done
        }
    }

    /** No backup: profiles and posts are read as a guest, and follows made here make the feed. */
    fun readWithoutAccount() {
        viewModelScope.launch {
            graph.sessions.update { it.copy(guest = true) }
            state.value = ImportUi.Done
        }
    }

    fun reset() {
        state.value = ImportUi.Idle
    }
}
