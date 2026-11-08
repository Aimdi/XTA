package com.aimdi.xtanative.ui.group

import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.plugins.PluginMembers
import com.aimdi.xtanative.store.settings.AiPrefs
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** A group's Discover with X stood in for: a late source, a stalled one, a failure, adding, and the AI's order. */
class DiscoveryViewModelTest {
    private val ids = AtomicInteger()

    private fun member() = "vm-${ids.incrementAndGet()}"

    private class Reads : DiscoveryXReads {
        @Volatile
        var postsAnswer: suspend (String) -> List<XThread> = { emptyList() }
        @Volatile
        var followingAnswer: suspend (String) -> List<XUser> = { emptyList() }

        override suspend fun posts(memberId: String) = postsAnswer(memberId)
        override suspend fun following(memberId: String) = followingAnswer(memberId)
    }

    private fun withGroup(
        following: List<FollowedAccount> = emptyList(),
        ai: Boolean = false,
        test: suspend (AppGraph, String) -> Unit,
    ) = runBlocking {
        val dir = Files.createTempDirectory("discovery-vm").toFile()
        try {
            val graph = AppGraph(dir, flowOf(true), "test")
            val accounts = following.ifEmpty { listOf(FollowedAccount(member(), "m", "M", null, verified = false, inFeed = true, followedAt = 1)) }
            graph.sessions.update {
                Session(following = accounts, groups = listOf(FollowGroup("g1", "Space", accounts.map { it.id })))
            }
            if (ai) {
                graph.settings.update { it.with(AiPrefs.baseUrl, "https://ai.example/v1").with(AiPrefs.apiKey, "key").with(AiPrefs.model, "model") }
            }
            test(graph, accounts.first().id)
        } finally {
            dir.deleteRecursively()
        }
    }

    private suspend fun DiscoveryViewModel.settled(what: (DiscoveryUi) -> Boolean) = withTimeout(10_000) { ui.first(what) }

    private fun user(id: String, handle: String) = XUser(id, handle, handle.replaceFirstChar { it.uppercase() })

    @Test
    fun aLateSourceCannotReplaceANewerScan() = withGroup { graph, owner ->
        val gate = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val reads = Reads().apply {
            followingAnswer = {
                if (calls.incrementAndGet() == 1) {
                    gate.await()
                    listOf(user("old", "oldhandle"))
                } else {
                    listOf(user("new", "newhandle"))
                }
            }
        }
        val vm = DiscoveryViewModel(graph, "g1", reads = reads)
        withTimeout(10_000) { while (calls.get() < 1) delay(10) }

        vm.load()
        vm.settled { ui -> !ui.scanning && ui.candidates.any { it.handle == "newhandle" } }

        gate.complete(Unit)
        delay(300)
        assertFalse(vm.ui.value.candidates.any { it.handle == "oldhandle" }, "the late answer of the first scan changed nothing")
        vm.viewModelScope.cancel()
    }

    @Test
    fun aFinishedSourcePaintsWhileAnotherStillWaits() = withGroup(following = listOf(
        FollowedAccount(member(), "m1", "M1", null, verified = false, inFeed = true, followedAt = 1),
        FollowedAccount(member(), "m2", "M2", null, verified = false, inFeed = true, followedAt = 2),
    )) { graph, _ ->
        val ids = graph.sessions.state.value!!.groups.first().memberIds
        val gate = CompletableDeferred<Unit>()
        val reads = Reads().apply {
            followingAnswer = { memberId ->
                if (memberId == ids[1]) gate.await()
                listOf(user("fast", "fast$memberId"))
            }
        }
        val vm = DiscoveryViewModel(graph, "g1", reads = reads)

        val painted = vm.settled { ui -> ui.candidates.isNotEmpty() && ui.scanning }
        assertTrue(painted.candidates.any { it.handle.startsWith("fast") })

        gate.complete(Unit)
        vm.settled { !it.loading && !it.scanning && it.coverage.total > 0 }
        vm.viewModelScope.cancel()
    }

    @Test
    fun aFailedSourceSaysWhyAndKeepsWhatAnswered() = withGroup { graph, owner ->
        val reads = Reads().apply {
            followingAnswer = { throw IOException("offline") }
        }
        val vm = DiscoveryViewModel(graph, "g1", reads = reads)

        val ui = vm.settled { it.failures.containsKey(Discovery.X) }
        assertTrue(ui.failures.getValue(Discovery.X) != null)
        vm.viewModelScope.cancel()
    }

    @Test
    fun addingACandidateMarksItAddedAndPutsItInTheGroup() = withGroup { graph, owner ->
        val reads = Reads().apply { followingAnswer = { listOf(user("new", "newhandle")) } }
        val vm = DiscoveryViewModel(graph, "g1", reads = reads)
        val candidate = vm.settled { it.candidates.isNotEmpty() }.candidates.first { it.handle == "newhandle" }

        vm.add(candidate)
        val added = vm.settled { it.added.contains(candidate.key) }
        assertTrue(added.added.contains(candidate.key))

        withTimeout(10_000) {
            graph.sessions.state.first { session -> session?.groups?.firstOrNull { it.id == "g1" }?.memberIds?.contains("new") == true }
        }
        vm.viewModelScope.cancel()
    }

    @Test
    fun anAllFollowedCandidatesNeverTriggerTheAi() = withGroup(following = listOf(
        FollowedAccount(member(), "m1", "M1", null, verified = false, inFeed = true, followedAt = 1),
        FollowedAccount("known", "known", "Known", null, verified = false, inFeed = true, followedAt = 2),
    ), ai = true) { graph, owner ->
        val calls = AtomicInteger()
        val completion: suspend (com.aimdi.xtanative.services.ai.AiConfig, String, Long) -> String = { _, _, _ ->
            calls.incrementAndGet()
            "[]"
        }
        val reads = Reads().apply { followingAnswer = { listOf(user("known", "known")) } }
        val vm = DiscoveryViewModel(graph, "g1", reads = reads, completion = completion)
        vm.settled { !it.loading && !it.scanning && it.coverage.total > 0 }

        vm.rankWithAi()
        delay(300)

        assertEquals(0, calls.get(), "nothing unfollowed was found, so the AI was not asked")
        vm.viewModelScope.cancel()
    }

    @Test
    fun aSupersededAiOrderCannotReplaceANewerScan() = withGroup(ai = true) { graph, owner ->
        val gate = CompletableDeferred<Unit>()
        val completions = AtomicInteger()
        val completion: suspend (com.aimdi.xtanative.services.ai.AiConfig, String, Long) -> String = { _, _, _ ->
            completions.incrementAndGet()
            gate.await()
            "[]"
        }
        val reads = Reads().apply { followingAnswer = { listOf(user("new", "newhandle")) } }
        val vm = DiscoveryViewModel(graph, "g1", reads = reads, completion = completion)
        vm.settled { !it.loading && !it.scanning && it.coverage.total > 0 && it.candidates.isNotEmpty() }

        vm.rankWithAi()
        withTimeout(10_000) { while (completions.get() < 1) delay(10) }
        vm.load()
        vm.settled { !it.loading && !it.scanning && it.coverage.total > 0 && it.candidates.isNotEmpty() }

        gate.complete(Unit)
        delay(300)
        assertFalse(vm.ui.value.usedAi, "the older AI order did not land")
        vm.viewModelScope.cancel()
    }

    @Test
    fun aCandidateIsKeyedTheWayItsNetworkKeysIt() = withGroup { graph, _ ->
        val vm = DiscoveryViewModel(graph, "g1")
        assertEquals("7", vm.memberOf(Candidate("7", "h", "H")))
        assertEquals(PluginMembers.memberId("pixiv", "42"), vm.memberOf(Candidate("42", "h", "H", source = "pixiv")))
        vm.viewModelScope.cancel()
    }

    @Test
    fun followingDuringASourceRequestExcludesItsEventualResult() = withGroup { graph, owner ->
        val gate = CompletableDeferred<Unit>()
        val reads = Reads().apply { followingAnswer = { gate.await(); listOf(user("new", "newhandle")) } }
        val vm = DiscoveryViewModel(graph, "g1", reads = reads)
        withTimeout(10_000) { while (!vm.ui.value.scanning) delay(10) }

        graph.sessions.update {
            it.copy(following = it.following + FollowedAccount("new", "newhandle", "New", null, verified = false, inFeed = true, followedAt = 2))
        }
        gate.complete(Unit)
        vm.settled { !it.loading && !it.scanning && it.coverage.total > 0 }

        assertFalse(vm.ui.value.candidates.any { it.handle == "newhandle" }, "followed meanwhile, so it is not a suggestion")
        vm.viewModelScope.cancel()
    }

    @Test
    fun closingDiscoveryRetiresPendingSources() = withGroup { graph, owner ->
        val gate = CompletableDeferred<Unit>()
        val reads = Reads().apply { followingAnswer = { gate.await(); listOf(user("new", "newhandle")) } }
        val vm = DiscoveryViewModel(graph, "g1", reads = reads)
        withTimeout(10_000) { while (!vm.ui.value.scanning) delay(10) }

        vm.viewModelScope.cancel()
        gate.complete(Unit)
        delay(300)
        assertFalse(vm.ui.value.candidates.any { it.handle == "newhandle" })
    }
}
