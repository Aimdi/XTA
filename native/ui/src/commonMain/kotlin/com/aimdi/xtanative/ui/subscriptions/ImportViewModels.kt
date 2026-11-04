package com.aimdi.xtanative.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.session.ImportHandles
import com.aimdi.xtanative.core.session.ListImport
import com.aimdi.xtanative.core.session.ListNotFoundException
import com.aimdi.xtanative.core.session.ListReads
import com.aimdi.xtanative.core.session.MemberPage
import com.aimdi.xtanative.core.x.model.XUserPage
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How an import stands: not started, reading (from [handle], when it reads several), done, or failed and why. */
sealed interface ImportRun {
    data object Idle : ImportRun

    data class Reading(val count: Int, val handle: String? = null) : ImportRun

    data class Done(val count: Int) : ImportRun

    /** [notFound] is a list X has not, or will not show. */
    data class Failed(val problem: Problem, val notFound: Boolean = false) : ImportRun
}

data class ImportUi(
    val input: String = "",
    val run: ImportRun = ImportRun.Idle,
    /** What was typed is no handle, or no list. */
    val invalid: Boolean = false,
) {
    val running: Boolean get() = run is ImportRun.Reading
}

/**
 * XTA's import of subscriptions (_import.dart): the public following of each
 * handle pasted becomes local follows, page by page. Nothing is followed on X.
 */
class ImportFollowingViewModel(private val graph: AppGraph) : ViewModel() {
    private val state = MutableStateFlow(ImportUi())
    val ui: StateFlow<ImportUi> = state.asStateFlow()
    private val reads = object : FollowingReads {
        override suspend fun userId(screenName: String): String = graph.client.profile(screenName).first.id

        override suspend fun following(userId: String, cursor: String?): XUserPage = graph.client.following(userId, cursor)
    }

    fun type(text: String) = state.update { it.copy(input = text, invalid = false) }

    fun start() {
        val handles = ImportHandles.parse(state.value.input)
        if (handles.isEmpty()) {
            state.update { it.copy(invalid = true) }
            return
        }
        if (state.value.running) return
        viewModelScope.launch {
            val at = System.currentTimeMillis()
            var total = 0
            try {
                for (handle in handles) {
                    val before = total
                    state.update { it.copy(run = ImportRun.Reading(before, handle)) }
                    total += FollowingImport.read(handle, reads) { users ->
                        graph.sessions.update { FollowingImport.followed(it, users, at) }
                        state.update { current ->
                            val soFar = (current.run as? ImportRun.Reading)?.count ?: before
                            current.copy(run = ImportRun.Reading(soFar + users.size, handle))
                        }
                    }
                }
                state.update { it.copy(run = ImportRun.Done(total)) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { it.copy(run = ImportRun.Failed(Problem.of(error))) }
            }
        }
    }
}

/**
 * XTA's import of an X list as a group (_import_list.dart): its members are
 * followed here (those already followed keep their date and switches) and a
 * group named after the list holds them all.
 */
class ListImportViewModel(private val graph: AppGraph, initial: String?) : ViewModel() {
    private val state = MutableStateFlow(ImportUi(input = initial.orEmpty()))
    val ui: StateFlow<ImportUi> = state.asStateFlow()

    private val reads = object : ListReads {
        override suspend fun listName(listId: String): String? = graph.client.listInfo(listId).name

        override suspend fun listMembers(listId: String, cursor: String?): MemberPage =
            graph.client.listMembers(listId, cursor).let { MemberPage(it.users, it.bottomCursor) }
    }

    fun type(text: String) = state.update { it.copy(input = text, invalid = false) }

    fun start() {
        val listId = ListImport.listId(state.value.input)
        if (listId == null) {
            state.update { it.copy(invalid = true) }
            return
        }
        if (state.value.running) return
        viewModelScope.launch {
            state.update { it.copy(run = ImportRun.Reading(0)) }
            try {
                val list = ListImport.read(listId, reads) { count -> state.update { it.copy(run = ImportRun.Reading(count)) } }
                val now = System.currentTimeMillis()
                val id = GroupActions.newId()
                graph.sessions.update { it.importingList(list, id, now).updatingGroup(id) { group -> group.copy(createdAt = now) } }
                state.update { it.copy(run = ImportRun.Done(list.members.size)) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: ListNotFoundException) {
                state.update { it.copy(run = ImportRun.Failed(Problem.Other, notFound = true)) }
            } catch (error: Throwable) {
                state.update { it.copy(run = ImportRun.Failed(Problem.of(error))) }
            }
        }
    }
}
