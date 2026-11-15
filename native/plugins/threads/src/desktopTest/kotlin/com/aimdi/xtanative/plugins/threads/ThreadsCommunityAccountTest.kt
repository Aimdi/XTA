package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsSearchFound
import com.aimdi.xtanative.networks.threads.ThreadsTopic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/** A community opened with a session: the account's feed first, the guest's reading whenever that gives nothing. */
class ThreadsCommunityAccountTest {
    private val cosplay = ThreadsTopic("Cosplayers of Threads", emoji = "🎭", community = true, url = "https://www.threads.com/communities/9")
    private fun post(id: String) = ThreadsPost(id = id, handle = "a", authorName = "A", text = "t")
    private val own = ThreadsSearchFound(listOf(post("own")))
    private val filed = ThreadsSearchFound(listOf(post("filed")))

    @Test
    fun theAccountsFeedComesFirstWithItsNextPage() = runBlocking {
        val read = ThreadsCommunityReads.readWithAccount(cosplay, { Page(listOf(post("mine")), "c1") }, { own }, { filed })
        assertEquals(ThreadsSearchFound(listOf(post("mine")), next = "c1") to ThreadsCommunitySource.ACCOUNT, read)
    }

    @Test
    fun aRestingFailedOrEmptyAccountReadFallsBackToTheGuestsQuietly() = runBlocking {
        assertEquals(own to ThreadsCommunitySource.OWN_PAGE, ThreadsCommunityReads.readWithAccount(cosplay, { error("resting") }, { own }, { filed }))
        assertEquals(own to ThreadsCommunitySource.OWN_PAGE, ThreadsCommunityReads.readWithAccount(cosplay, { Page(emptyList(), null) }, { own }, { filed }))
        assertEquals(filed to ThreadsCommunitySource.TOPIC, ThreadsCommunityReads.readWithAccount(cosplay.copy(url = null), { error("signed out") }, { own }, { filed }))
    }

    @Test
    fun withoutTheAccountNothingIsAskedOfIt() = runBlocking {
        assertEquals(own to ThreadsCommunitySource.OWN_PAGE, ThreadsCommunityReads.readWithAccount(cosplay, null, { own }, { filed }))
    }
}
