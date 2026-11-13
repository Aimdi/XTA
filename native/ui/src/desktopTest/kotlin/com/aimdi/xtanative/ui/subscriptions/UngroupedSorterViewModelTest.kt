package com.aimdi.xtanative.ui.subscriptions

import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.data.AppGraph
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** The one-by-one sorter's writes: only its choice, one at a time, and a failure leaves the account on show. */
class UngroupedSorterViewModelTest {
    private class Writes : UngroupedWrites {
        val added = mutableListOf<Pair<String, List<String>>>()
        val removed = mutableListOf<Pair<String, List<String>>>()
        var gate: CompletableDeferred<Unit>? = null
        var failure: Throwable? = null

        override suspend fun addTo(memberId: String, groupIds: List<String>) {
            gate?.await()
            failure?.let { throw it }
            added += memberId to groupIds
        }

        override suspend fun removeFrom(memberId: String, groupIds: List<String>) {
            removed += memberId to groupIds
        }

        override suspend fun create(name: String) = "created"
    }

    private fun withGraph(test: suspend (AppGraph, File) -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("sorter").toFile()
        try {
            val graph = AppGraph(dir, flowOf(true), "test")
            graph.sessions.update {
                Session(
                    following = listOf(FollowedAccount("1", "maya", "Maya", null, verified = false, inFeed = true, followedAt = 1)),
                    groups = listOf(FollowGroup("g1", "Friends", emptyList())),
                )
            }
            test(graph, dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private suspend fun UngroupedSorterViewModel.current() = withTimeout(10_000) { ui.first { it.current != null } }

    @Test
    fun onlyPlacingWritesAndUndoTakesItBack() = withGraph { graph, _ ->
        val writes = Writes()
        val vm = UngroupedSorterViewModel(graph, listOf("1"), writer = writes)
        vm.current()

        vm.put(listOf("g1"))
        withTimeout(10_000) { while (writes.added.isEmpty()) delay(10) }
        assertEquals(listOf("1" to listOf("g1")), writes.added)

        vm.undo()
        withTimeout(10_000) { while (writes.removed.isEmpty()) delay(10) }
        assertEquals(listOf("1" to listOf("g1")), writes.removed, "the undo takes back only what the choice added")
        vm.viewModelScope.cancel()
    }

    @Test
    fun aTapWhileAChoiceIsSavedIsIgnored() = withGraph { graph, _ ->
        val writes = Writes().apply { gate = CompletableDeferred() }
        val vm = UngroupedSorterViewModel(graph, listOf("1"), writer = writes)
        vm.current()

        vm.put(listOf("g1"))
        withTimeout(10_000) { vm.ui.first { it.sorter.saving } }
        vm.put(listOf("g1"))
        writes.gate!!.complete(Unit)
        withTimeout(10_000) { while (writes.added.isEmpty()) delay(10) }
        delay(100)

        assertEquals(1, writes.added.size)
        vm.viewModelScope.cancel()
    }

    @Test
    fun aFailedSaveLeavesTheAccountOnShowAndSaysSo() = withGraph { graph, _ ->
        val writes = Writes().apply { failure = IOException("offline") }
        val vm = UngroupedSorterViewModel(graph, listOf("1"), writer = writes)
        vm.current()

        vm.put(listOf("g1"))
        val ui = withTimeout(10_000) { vm.ui.first { it.failed && !it.sorter.saving } }

        assertEquals("1", ui.current?.id, "the account is still on show to try again")
        assertEquals(false, ui.sorter.saving)
        vm.viewModelScope.cancel()
    }
}
