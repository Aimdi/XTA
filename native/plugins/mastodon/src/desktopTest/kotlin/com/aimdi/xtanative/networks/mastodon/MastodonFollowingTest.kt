package com.aimdi.xtanative.networks.mastodon

import com.aimdi.xtanative.networks.NetworkRequestException
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** The merged Following timeline, with the rules of XTA's account_posts.dart and mastodon_feed_reliability_test.dart. */
class MastodonFollowingTest {
    /** Followed accounts on their instances: each answers its statuses, fails, or is held until let go. */
    private class World {
        var now = 1_000_000L
        var configured = listOf<String>()
        val statuses = ConcurrentHashMap<String, List<JsonObject>>()
        val failing: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val held = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
        val fake = FakeMastodon { asked ->
            if (asked.path == "/api/v1/accounts/lookup") {
                val acct = asked.query("acct")!!
                ok(accountJson(acct.substringBefore('@'), acct.substringBefore('@'), acct))
            } else {
                val user = asked.path.split('/')[4]
                val answer = if (user in failing) answer(500) else ok(JsonArray(statuses[user].orEmpty()))
                held.remove(user)?.await()
                answer
            }
        }
        val following = MastodonFollowing(MastodonClient(fake), { configured }, { now })

        fun statusReads(user: String) = fake.paths.count { it.endsWith("/api/v1/accounts/$user/statuses") }

        suspend fun waitForRead(user: String) {
            while (statusReads(user) == 0) yield()
        }
    }

    private fun status(user: String, id: String, hour: Int, boost: Boolean = false): JsonObject {
        val original = obj(
            "id" to id,
            "created_at" to "2026-09-01T%02d:00:00Z".format(hour),
            "url" to "https://origin.example/@$user/$id",
            "content" to "<p>$id</p>",
            "account" to accountJson(user, user, "$user@origin.example"),
        )
        return if (!boost) original else obj("id" to "$id-boost", "created_at" to "2026-09-01T%02d:30:00Z".format(hour), "reblog" to original, "account" to accountJson("booster", "booster"))
    }

    private fun accts(vararg users: String) = users.map { "$it@origin.example" }

    @Test
    fun followedAccountsAreMergedNewestFirst() = runBlocking {
        val world = World()
        world.statuses["alice"] = listOf(status("alice", "a2", 10), status("alice", "a1", 8))
        world.statuses["bob"] = listOf(status("bob", "b1", 9))
        val posts = world.following.posts(accts("alice", "bob"))
        assertEquals(listOf("a2", "b1", "a1"), posts.map { it.id })
    }

    @Test
    fun anOriginalIsKeptOverABoostOfIt() = runBlocking {
        val world = World()
        world.statuses["alice"] = listOf(status("alice", "shared", 8), status("alice", "other", 9))
        world.statuses["booster"] = listOf(status("alice", "shared", 8, boost = true))
        val posts = world.following.posts(accts("booster", "alice"))
        assertEquals(listOf("other", "shared"), posts.map { it.id })
        assertTrue(posts.none { it.boosted })
    }

    @Test
    fun eachAccountIsAskedAtItsOwnInstanceFirst() = runBlocking {
        val world = World()
        world.configured = listOf("https://home.example")
        world.following.posts(accts("alice"))
        assertEquals("origin.example", world.fake.asked.first().host)
    }

    @Test
    fun anAccountsPostsAreReusedForTenMinutes() = runBlocking {
        val world = World()
        world.statuses["alice"] = listOf(status("alice", "a1", 8))
        world.following.posts(accts("alice"))
        world.following.posts(accts("alice"))
        assertEquals(1, world.statusReads("alice"))

        world.now += 10 * 60 * 1000 + 1
        world.following.posts(accts("alice"))
        assertEquals(2, world.statusReads("alice"))
        world.following.posts(accts("alice"), forceRefresh = true)
        assertEquals(3, world.statusReads("alice"), "a forced refresh asks at once")
    }

    @Test
    fun aLoadAsksAtMostTwelveAccountsAndTheNextLoadTheRest() = runBlocking {
        val world = World()
        val users = (1..14).map { "user$it" }
        users.forEachIndexed { index, user -> world.statuses[user] = listOf(status(user, "$user-post", index % 24)) }
        val first = world.following.posts(users.map { "$it@origin.example" })
        assertEquals(12, users.count { world.statusReads(it) == 1 })
        assertEquals(12, first.size)

        val second = world.following.posts(users.map { "$it@origin.example" })
        assertEquals(14, second.size, "the accounts read before are still fresh, the other two are read now")
        assertTrue(users.all { world.statusReads(it) == 1 })
    }

    @Test
    fun oneFailingAccountLeavesTheOthersAndWhatItSaidLast() = runBlocking {
        val world = World()
        world.statuses["alice"] = listOf(status("alice", "a1", 8))
        world.statuses["bob"] = listOf(status("bob", "b1", 9))
        world.following.posts(accts("alice", "bob"))

        world.failing += "bob"
        world.statuses["alice"] = listOf(status("alice", "a2", 10))
        val posts = world.following.posts(accts("alice", "bob"), forceRefresh = true)
        assertEquals(listOf("a2", "b1"), posts.map { it.id })
    }

    @Test
    fun whenNoAccountGaveAnythingTheFailureIsThrownAfterShowingWhatTheySaidLast() {
        val world = World()
        world.statuses["alice"] = listOf(status("alice", "a1", 8))
        runBlocking { world.following.posts(accts("alice")) }
        world.failing += "alice"
        val painted = ArrayList<List<String>>()
        val error = assertFailsWith<NetworkRequestException> {
            runBlocking { world.following.posts(accts("alice"), forceRefresh = true) { posts -> painted += posts.map { it.id } } }
        }
        assertEquals(MastodonFailure.BAD_RESPONSE, MastodonFailure.of(error))
        assertEquals(listOf("a1"), painted.first(), "what was held is painted before the network is asked")
    }

    @Test
    fun partialsGrowAsAccountsAnswer() = runBlocking {
        val world = World()
        world.statuses["alice"] = listOf(status("alice", "a1", 8))
        world.statuses["bob"] = listOf(status("bob", "b1", 9))
        val painted = ArrayList<List<String>>()
        val posts = world.following.posts(accts("alice", "bob")) { partial -> painted += partial.map { it.id } }
        assertEquals(listOf(listOf("a1"), listOf("b1", "a1")), painted)
        assertEquals(listOf("b1", "a1"), posts.map { it.id })
    }

    @Test
    fun aForgottenReadDeliversNothing() = runBlocking {
        val world = World()
        world.statuses["alice"] = listOf(status("alice", "old", 8))
        val gate = CompletableDeferred<Unit>()
        world.held["alice"] = gate
        val painted = ArrayList<List<MastodonPost>>()
        val read = async { world.following.posts(accts("alice")) { painted += it } }
        world.waitForRead("alice")
        world.following.forget()
        world.statuses["alice"] = listOf(status("alice", "new", 9))
        gate.complete(Unit)

        assertTrue(read.await().isEmpty())
        assertTrue(painted.isEmpty())
        assertEquals(listOf("new"), world.following.posts(accts("alice")).map { it.id }, "nothing of the forgotten read was kept")
    }

    @Test
    fun otherInstancesOvertakeAnOlderRead() = runBlocking {
        val world = World()
        world.statuses["alice"] = listOf(status("alice", "old", 8))
        val gate = CompletableDeferred<Unit>()
        world.held["alice"] = gate
        val older = async { world.following.posts(accts("alice")) }
        world.waitForRead("alice")

        world.configured = listOf("https://second.example")
        world.statuses["alice"] = listOf(status("alice", "new", 9))
        assertEquals(listOf("new"), world.following.posts(accts("alice")).map { it.id })
        gate.complete(Unit)
        assertTrue(older.await().isEmpty())
    }

    @Test
    fun anOlderReadCannotOverwriteWhatANewerOneFound() = runBlocking {
        val world = World()
        world.statuses["alice"] = listOf(status("alice", "old", 8))
        val gate = CompletableDeferred<Unit>()
        world.held["alice"] = gate
        val older = async { world.following.posts(accts("alice"), forceRefresh = true) }
        world.waitForRead("alice")

        world.statuses["alice"] = listOf(status("alice", "new", 9))
        assertEquals(listOf("new"), world.following.posts(accts("alice"), forceRefresh = true).map { it.id })
        gate.complete(Unit)
        assertEquals(listOf("old"), older.await().map { it.id })

        assertEquals(listOf("new"), world.following.posts(accts("alice")).map { it.id })
        assertEquals(2, world.statusReads("alice"), "the newer read's posts were reused")
    }

    @Test
    fun noAccountsReadNothing() = runBlocking {
        val world = World()
        assertTrue(world.following.posts(emptyList()).isEmpty())
        assertTrue(world.fake.asked.isEmpty())
    }
}
