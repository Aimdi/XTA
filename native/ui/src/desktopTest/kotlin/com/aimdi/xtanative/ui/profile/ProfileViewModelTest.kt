package com.aimdi.xtanative.ui.profile

import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.AppGraph
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** The profile view model with X stood in for: a late answer, a kept page, a failed read and a closed screen. */
class ProfileViewModelTest {
    private val maya = XUser("1", "maya", "Maya")
    private val sam = XUser("2", "sam", "Sam")
    private val at = Instant.parse("2026-11-01T00:00:00Z")

    private fun thread(id: String, user: XUser = maya) =
        XThread(id, listOf(XPost(id, user, at, "post $id")))

    private fun page(vararg ids: String, user: XUser = maya, next: String? = null) =
        XTimelinePage(ids.map { thread(it, user) }, next, null)

    /** The reads the test sets: the profile, the posts page, and the rest empty. */
    private class Reads : ProfileReads {
        @Volatile
        var profileAnswer: suspend (String) -> Pair<XUser, List<String>> = { error("no profile") }

        @Volatile
        var postsAnswer: suspend (String, String?) -> XTimelinePage = { _, _ -> XTimelinePage(emptyList(), null, null) }

        override suspend fun profile(screenName: String) = profileAnswer(screenName)
        override suspend fun profileById(userId: String) = profileAnswer(userId)
        override suspend fun userPosts(userId: String, cursor: String?) = postsAnswer(userId, cursor)
        override suspend fun userReposts(userId: String, cursor: String?) = XTimelinePage(emptyList(), null, null)
        override suspend fun userReplies(userId: String, cursor: String?) = XTimelinePage(emptyList(), null, null)
        override suspend fun userMedia(userId: String, cursor: String?) = XTimelinePage(emptyList(), null, null)
    }

    private fun withGraph(test: suspend (AppGraph, File) -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("profile-vm").toFile()
        try {
            test(AppGraph(dir, flowOf(true), "test"), dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aProfileThatAnswersLateCannotReplaceANewerOne() = withGraph { graph, _ ->
        val gate = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val reads = Reads().apply {
            profileAnswer = {
                if (calls.incrementAndGet() == 1) {
                    gate.await()
                    sam to emptyList()
                } else {
                    maya to emptyList()
                }
            }
        }
        val vm = ProfileViewModel(graph, "maya", reads = reads)
        withTimeout(10_000) { while (calls.get() < 1) delay(10) }

        vm.refresh()
        withTimeout(10_000) { vm.ui.first { it.user?.id == maya.id } }

        gate.complete(Unit)
        delay(300)
        assertEquals(maya.id, vm.ui.value.user?.id, "the late answer to the first load changed nothing")
        vm.viewModelScope.cancel()
    }

    @Test
    fun aProfileKeptOnTheDeviceShowsWhenTheNetworkFails() = withGraph { graph, _ ->
        graph.keptProfiles.remember(maya, emptyList())
        val reads = Reads().apply { profileAnswer = { throw IOException("offline") } }

        val vm = ProfileViewModel(graph, "maya", reads = reads)
        val ui = withTimeout(10_000) { vm.ui.first { it.user != null && it.problem != null } }

        assertEquals(maya.id, ui.user?.id)
        vm.viewModelScope.cancel()
    }

    @Test
    fun aTabReadThatFailsFallsBackToThePageKeptOnTheDevice() = withGraph { graph, _ ->
        val calls = AtomicInteger()
        val reads = Reads().apply {
            profileAnswer = { maya to emptyList() }
            postsAnswer = { _, _ ->
                if (calls.incrementAndGet() == 1) page("7") else throw IOException("offline")
            }
        }
        val vm = ProfileViewModel(graph, "maya", reads = reads)
        withTimeout(10_000) {
            vm.ui.first { it.tabs[ProfileTab.POSTS]?.threads?.any { thread -> thread.id == "7" } == true }
        }

        vm.refresh()
        val ui = withTimeout(10_000) {
            vm.ui.first {
                it.tabs[ProfileTab.POSTS]?.threads?.any { thread -> thread.id == "7" } == true &&
                    !it.loading && it.tabs[ProfileTab.POSTS]?.problem == null
            }
        }

        assertEquals(listOf("7"), ui.tabs.getValue(ProfileTab.POSTS).threads.map { it.id })
        vm.viewModelScope.cancel()
    }

    @Test
    fun closingAStalledProfilePermitsAnotherToLoad() = withGraph { graph, _ ->
        val gate = CompletableDeferred<Unit>()
        val stalled = Reads().apply { profileAnswer = { gate.await(); sam to emptyList() } }
        val first = ProfileViewModel(graph, "maya", reads = stalled)
        delay(100)
        first.viewModelScope.cancel()

        val second = ProfileViewModel(graph, "maya", reads = Reads().apply { profileAnswer = { maya to emptyList() } })
        withTimeout(10_000) { second.ui.first { it.user?.id == maya.id } }
        second.viewModelScope.cancel()
        gate.cancel()
    }
}
