package com.aimdi.xtanative.data

import android.content.Context
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XSigners
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.core.x.signing.FileSignerStore
import com.aimdi.xtanative.core.x.signing.SigningBootstrap
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the screens share, made once per process. */
class AppGraph(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val mock = MockX.server

    val sessions = SessionStore(File(context.filesDir, "session.json"), initial = if (mock == null) Session() else MockX.session)
    private val files = context.filesDir
    private val feedCaches = HashMap<String, FeedCache>()
    val posts = PostCache()

    private val http =
        if (mock == null) OkHttpXHttp() else OkHttpXHttp(OkHttpXHttp.defaultClient.newBuilder().addInterceptor(MockX.interceptor(mock)).build())
    val signers =
        if (mock == null) XSigners(SigningBootstrap(http), FileSignerStore(File(context.filesDir, "signers.json"))) else XSigners({ MockX.signer })
    val client = XClient(http, accounts = { sessions.state.value?.accounts.orEmpty() }, signers = signers)

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

    fun start() {
        scope.launch { sessions.load() }
        if (mock == null) scope.launch { client.refreshEndpoints() }
    }
}
