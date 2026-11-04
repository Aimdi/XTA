package com.aimdi.xtanative.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where an import stands. */
enum class ListImportStage { IDLE, RUNNING, DONE, FAILED }

data class ListImportUi(
    val input: String = "",
    /** What was typed names no list. */
    val invalid: Boolean = false,
    val stage: ListImportStage = ListImportStage.IDLE,
    /** The members followed so far. */
    val imported: Int = 0,
    /** X has no such list, or will not show it. */
    val notFound: Boolean = false,
    val problem: Problem? = null,
)

/**
 * XTA's import of an X list as a group: the list's name, then its members
 * page by page, followed here as they come, and at the end a group named
 * after the list holding them all. X is only read.
 */
class ListImportViewModel(private val graph: AppGraph, listId: String?) : ViewModel() {
    private val state = MutableStateFlow(ListImportUi(input = listId.orEmpty()))
    val ui: StateFlow<ListImportUi> = state.asStateFlow()
    private var job: Job? = null

    fun type(input: String) = state.update { it.copy(input = input) }

    fun import() {
        val listId = ListIds.of(state.value.input)
        if (listId == null) {
            state.update { it.copy(invalid = true) }
            return
        }
        job?.cancel()
        state.update { it.copy(invalid = false, stage = ListImportStage.RUNNING, imported = 0, notFound = false, problem = null) }
        job = viewModelScope.launch {
            try {
                val name = graph.client.listInfo(listId).name
                if (name == null) {
                    state.update { it.copy(stage = ListImportStage.FAILED, notFound = true) }
                    return@launch
                }
                val members = followMembers(listId)
                graph.sessions.update { ListImport.grouped(it, UUID.randomUUID().toString(), name, members) }
                state.update { it.copy(stage = ListImportStage.DONE) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { it.copy(stage = ListImportStage.FAILED, problem = Problem.of(error)) }
            }
        }
    }

    /** Every member of the list, followed here page by page; their ids, in the order they came. */
    private suspend fun followMembers(listId: String): Set<String> {
        val seen = LinkedHashSet<String>()
        var cursor: String? = null
        while (true) {
            val page = graph.client.listMembers(listId, cursor)
            val fresh = ListImport.fresh(page.users, seen)
            if (fresh.isNotEmpty()) {
                val at = System.currentTimeMillis()
                graph.sessions.update { ListImport.followed(it, fresh, at) }
                state.update { it.copy(imported = seen.size) }
            }
            if (ListImport.ended(page.bottomCursor, cursor, fresh)) return seen
            cursor = page.bottomCursor
        }
    }
}
