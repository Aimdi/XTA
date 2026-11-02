package com.aimdi.xtanative.data

import android.content.Context
import com.aimdi.xtanative.core.session.Session
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

    private val mock = MockX.server

    val sessions = SessionStore(File(context.filesDir, "session.json"), initial = if (mock == null) Session() else MockX.session)
    val feedCache = FeedCache(File(context.filesDir, "feed.json"))
    val posts = PostCache()

    private val http =
        if (mock == null) OkHttpXHttp() else OkHttpXHttp(OkHttpXHttp.defaultClient.newBuilder().addInterceptor(MockX.interceptor(mock)).build())
    val signers = if (mock == null) XSigners(SigningBootstrap(http)) else XSigners({ MockX.signer })
    val client = XClient(http, accounts = { sessions.state.value?.accounts.orEmpty() }, signers = signers)

    fun start() {
        scope.launch { sessions.load() }
        if (mock == null) scope.launch { client.refreshEndpoints() }
    }
}
