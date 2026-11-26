package com.aimdi.xtanative.ui.home

import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.feed.HomeReads
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.AppGraph
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** For you's new-posts pill: a refresh the reader did not ask for waits while they are down the list. */
class ForYouNewPostsTest {
    private val base = Instant.parse("2026-11-01T00:00:00Z")

    private fun thread(id: Long) = XThread(id.toString(), listOf(XPost(id.toString(), XUser("1", "nasa", "NASA"), base.plusSeconds(id), "post $id")))

    @Volatile
    private var answer: List<Long> = listOf(3, 2, 1)

    private val reads = HomeReads { _, _, _ -> XTimelinePage(answer.map(::thread), null, null) }

    private fun withModel(test: suspend ForYouViewModel.() -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("for-you").toFile()
        try {
            val graph = AppGraph(dir, flowOf(true), "test")
            graph.sessions.update { Session(accounts = listOf(XAccount("a", "me", emptyMap()))) }
            val vm = withContext(Dispatchers.Main) { ForYouViewModel(graph, reads) }
            try {
                vm.until { it.threads.size == 3 && !it.refreshing }
                vm.test()
            } finally {
                vm.viewModelScope.cancel()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private suspend fun ForYouViewModel.until(what: (ForYouUi) -> Boolean): ForYouUi = withTimeout(10_000) { ui.first(what) }

    private suspend fun ForYouViewModel.onMain(action: ForYouViewModel.() -> Unit) = withContext(Dispatchers.Main) { action() }

    private fun ids(ui: ForYouUi) = ui.threads.map { it.id.toLong() }

    @Test
    fun aRefreshWhileDownTheListWaitsBehindThePill() = withModel {
        answer = listOf(5, 4, 3, 2, 1)
        onMain { atTop(false); refresh(byReader = false) }
        val held = until { it.newPosts > 0 }
        assertEquals(2, held.newPosts)
        assertEquals(listOf(3L, 2, 1), ids(held), "nothing moves under the reader's eyes")
        onMain { showNew() }
        val shown = until { it.newPosts == 0 && it.threads.size == 5 }
        assertEquals(listOf(5L, 4, 3, 2, 1), ids(shown))
    }

    @Test
    fun backAtTheTopWhatWaitedIsShown() = withModel {
        answer = listOf(4, 3, 2, 1)
        onMain { atTop(false); refresh(byReader = false) }
        until { it.newPosts == 1 }
        onMain { atTop(true) }
        assertEquals(listOf(4L, 3, 2, 1), ids(until { it.newPosts == 0 && it.threads.size == 4 }))
    }

    @Test
    fun aPullIsShownAtOnce() = withModel {
        answer = listOf(4, 3, 2, 1)
        onMain { atTop(false); refresh(byReader = true) }
        val shown = until { it.threads.size == 4 && !it.refreshing }
        assertEquals(0, shown.newPosts)
    }

    @Test
    fun atTheTopARefreshIsShownAtOnce() = withModel {
        answer = listOf(4, 3, 2, 1)
        onMain { refresh(byReader = false) }
        assertEquals(0, until { it.threads.size == 4 && !it.refreshing }.newPosts)
    }

    @Test
    fun theCountIsOfThePostsNotShownYet() {
        assertEquals(2, ForYouRules.newCount(listOf(thread(2), thread(1)), listOf(thread(4), thread(3), thread(2))))
        assertEquals(0, ForYouRules.newCount(listOf(thread(2)), listOf(thread(2))))
    }
}
