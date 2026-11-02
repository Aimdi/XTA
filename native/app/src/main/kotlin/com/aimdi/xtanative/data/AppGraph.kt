package com.aimdi.xtanative.data

import android.content.Context
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XSigners
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.core.x.signing.SigningBootstrap
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Everything the screens share, made once per process. */
class AppGraph(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessions = SessionStore(File(context.filesDir, "session.json"))
    val feedCache = FeedCache(File(context.filesDir, "feed.json"))
    val posts = PostCache()

    private val http = OkHttpXHttp()
    val signers = XSigners(SigningBootstrap(http))
    val client = XClient(http, accounts = { sessions.state.value?.accounts.orEmpty() }, signers = signers)

    fun start() {
        scope.launch { sessions.load() }
        scope.launch { client.refreshEndpoints() }
    }
}
