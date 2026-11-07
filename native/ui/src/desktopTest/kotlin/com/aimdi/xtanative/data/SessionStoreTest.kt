package com.aimdi.xtanative.data

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.Session
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/**
 * The stored subscriptions (XTA's subscriptions list and groups board,
 * _list.dart and _groups.dart): a file that cannot be read is said, as XTA's
 * "Unable to refresh the subscriptions" and "Unable to load the group", and
 * Retry reads it again; a first start, with no file, is not a failure.
 */
class SessionStoreTest {
    private val nasa = FollowedAccount("1", "nasa", "NASA", null, verified = false, inFeed = true, followedAt = 1)

    private fun withFile(test: suspend (File) -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("session-store").toFile()
        try {
            test(File(dir, "session.json"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aFirstStartWithNoFileIsNoFailure() = withFile { file ->
        val store = SessionStore(file)
        store.load()
        assertNull(store.failure.value)
        assertEquals(Session(), store.state.value)
    }

    @Test
    fun aFileThatCannotBeReadIsAFailureTheScreensShow() = withFile { file ->
        file.writeText("{not json")
        val store = SessionStore(file)
        store.load()
        assertNotNull(store.failure.value)
        assertEquals(Session(), store.state.value, "the app runs on an empty session meanwhile")
    }

    @Test
    fun retryReadsTheFileAgainOnceItCanBeRead() = withFile { file ->
        file.writeText("{not json")
        val store = SessionStore(file)
        store.load()
        file.writeText(Session.encode(Session(following = listOf(nasa))))
        store.retry()
        assertNull(store.failure.value)
        assertEquals(listOf(nasa), store.state.value?.following)
    }

    @Test
    fun aWriteClearsTheFailure() = withFile { file ->
        file.writeText("")
        val store = SessionStore(file)
        store.load()
        assertNotNull(store.failure.value)
        store.update { it.copy(following = listOf(nasa)) }
        assertNull(store.failure.value)
    }
}
