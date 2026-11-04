package com.aimdi.xtanative.ui.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.backup.BackupDocument
import com.aimdi.xtanative.ui.settings.BackupRead
import com.aimdi.xtanative.ui.settings.DeviceBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportUi {
    data object Idle : ImportUi
    data object Reading : ImportUi
    data class Ready(val document: BackupDocument) : ImportUi

    /** Not a backup this build can restore: [read] says why. */
    data class Refused(val read: BackupRead) : ImportUi
    data object Restoring : ImportUi
    data object Done : ImportUi
}

/** Reads a file XTA exported and, once confirmed, restores every section of it, as XTA's import does. */
class ImportViewModel(private val graph: AppGraph) : ViewModel() {
    private val state = MutableStateFlow<ImportUi>(ImportUi.Idle)
    val ui: StateFlow<ImportUi> = state.asStateFlow()

    /** Reads the file the chooser gave, through [text]: a backup to confirm, or why it is none. */
    fun read(text: suspend () -> String?) {
        state.value = ImportUi.Reading
        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) { BackupRead.of(runCatching { text() }.getOrNull()) }
            state.value = if (read is BackupRead.Ready) ImportUi.Ready(read.document) else ImportUi.Refused(read)
        }
    }

    /** Restores [document], to its end even when the screen goes away; reading positions only when asked for. */
    fun use(document: BackupDocument, includeReadPositions: Boolean) {
        state.value = ImportUi.Restoring
        viewModelScope.launch {
            withContext(NonCancellable) { DeviceBackup(graph).restore(document, includeReadPositions) }
            state.value = ImportUi.Done
        }
    }

    fun reset() {
        state.value = ImportUi.Idle
    }
}
