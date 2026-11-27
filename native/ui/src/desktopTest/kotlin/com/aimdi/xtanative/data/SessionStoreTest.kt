package com.aimdi.xtanative.data

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.Session
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
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
    fun anUnreadableFileIsKeptAsideBeforeAnyWriteReplacesIt() = withFile { file ->
        file.writeText("{written by a newer build")
        val store = SessionStore(file)
        store.load()
        store.update { it.copy(following = listOf(nasa)) }
        val aside = file.parentFile.listFiles { f -> f.name.startsWith("session.json.unreadable-") }.orEmpty().single()
        assertEquals("{written by a newer build", aside.readText(), "the follows it held are not lost")
        assertEquals(listOf(nasa), Session.decode(file.readText())?.following)
    }

    @Test
    fun aWriteThatFailsKeepsTheChangeForThisRunInsteadOfThrowing() = withFile { file ->
        // A file where its folder should be: nothing can be written under it, as on a full disk.
        val blocked = File(file.parentFile, "blocked").apply { writeText("") }
        val store = SessionStore(File(blocked, "session.json"))
        store.load()
        store.update { it.copy(following = listOf(nasa)) }
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

    @Test
    fun aChangeBeforeTheFirstReadIsMadeToWhatTheFileHolds() = withFile { file ->
        file.writeText(Session.encode(Session(following = listOf(nasa))))
        val store = SessionStore(file)
        store.update { it.copy(includeReposts = false) }
        assertEquals(listOf(nasa), Session.decode(file.readText())?.following, "a plugin's uninstall racing the load erases no follow")
    }

    @Test
    fun aFileThatCannotBeOpenedIsNeverWrittenOver() = withFile { file ->
        file.writeText(Session.encode(Session(following = listOf(nasa))))
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("---------"))
        try {
            if (file.canRead()) return@withFile // run as root: nothing to test
            val store = SessionStore(file)
            store.load()
            assertNotNull(store.failure.value)
            store.update { it.copy(includeReposts = false) }
            assertEquals(false, store.state.value?.includeReposts, "the change stays for this run")
            assertNotNull(store.failure.value)
        } finally {
            Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
        }
        assertEquals(listOf(nasa), Session.decode(file.readText())?.following)
    }

    @Test
    fun eachUnreadableFileIsKeptAsideBesideTheEarlierOnes() = withFile { file ->
        file.writeText("{first")
        SessionStore(file).load()
        file.writeText("{second")
        SessionStore(file).load()
        SessionStore(file).apply { load(); retry() }
        val asides = file.parentFile.listFiles { f -> f.name.startsWith("session.json.unreadable-") }.orEmpty()
        assertEquals(setOf("{first", "{second"), asides.map { it.readText() }.toSet())
        assertEquals(2, asides.size)
    }
}
