package com.aimdi.xtanative.data

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XGuest
import com.aimdi.xtanative.core.x.XSigners
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.core.x.signing.FileSignerStore
import com.aimdi.xtanative.core.x.signing.SigningBootstrap
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything the screens share, made once per process by the app's shell
 * (Android's Application, the desktop's main): [files] is the app's private
 * folder, [connection] whether there is one as it changes, [version] this
 * build's, and [mockX] the address of XTA's stand-in for X to read instead of
 * x.com (see [MockX]), or null.
 */
class AppGraph(private val files: File, connection: Flow<Boolean>, val version: String, private val mockX: String? = null) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sessions = SessionStore(File(files, "session.json"), initial = if (mockX == null) Session() else MockX.session)
    private val feedCaches = HashMap<String, FeedCache>()
    val posts = PostCache()

    /** Whether there is a connection, as it changes: the present state first. */
    val online: SharedFlow<Boolean> = connection.shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val http =
        if (mockX == null) OkHttpXHttp() else OkHttpXHttp(OkHttpXHttp.defaultClient.newBuilder().addInterceptor(MockX.interceptor(mockX)).build())
    val signers =
        if (mockX == null) XSigners(SigningBootstrap(http), FileSignerStore(File(files, "signers.json"))) else XSigners({ MockX.signer })
    val client = XClient(http, accounts = { sessions.state.value?.accounts.orEmpty() }, signers = signers, guest = XGuest(http))

    /** The Following feed's cache for null, else the group's. */
    @Synchronized
    fun feedCache(groupId: String?): FeedCache {
        val name = if (groupId == null) "feed.json" else "feed-" + groupId.filter { it.isLetterOrDigit() || it == '-' }.take(64) + ".json"
        return feedCaches.getOrPut(name) { FeedCache(File(files, name)) }
    }

    /** Every feed's cache: after an import the follows they showed may be gone. */
    suspend fun clearFeedCaches() {
        withContext(Dispatchers.IO) {
            files.listFiles { file -> file.name.startsWith("feed") && file.name.endsWith(".json") }?.forEach { it.delete() }
        }
    }

    /**
     * Runs [write] to its end even when the screen that asked for it goes
     * away: the reading place, kept as the app stops or a window closes.
     */
    fun keep(write: suspend () -> Unit) {
        writes.launch { write() }
    }

    /** Waits for the [keep] writes under way: a closing desktop app lets them finish first. */
    suspend fun awaitWrites() {
        writes.coroutineContext[Job]?.children?.toList()?.joinAll()
    }

    fun start() {
        scope.launch { sessions.load() }
        if (mockX == null) scope.launch { client.refreshEndpoints() }
    }
}
