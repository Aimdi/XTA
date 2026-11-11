package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What a pull on a list runs, and whether it is still running: the
 * indicator stays until [work] has ended, however it ends, as XTA's
 * RefreshIndicator waits for its `onRefresh`. A pull while one runs starts
 * nothing.
 */
class PullWork(private val scope: CoroutineScope, private val work: suspend () -> Unit) {
    private val mutable = MutableStateFlow(false)
    val running: StateFlow<Boolean> = mutable.asStateFlow()

    fun start() {
        if (!mutable.compareAndSet(expect = false, update = true)) return
        scope.launch {
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // What the list shows says how it went; the pull only ends.
            } finally {
                mutable.value = false
            }
        }
    }
}

/**
 * [content] (a list) pulled down to run [reload], the indicator kept until
 * it has ended: for a list that is read from the device (the accounts
 * followed here, what is saved here), where no read of the network says
 * when it is done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToReload(reload: suspend () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(reload)
    val pull = remember(scope) { PullWork(scope) { latest() } }
    val running by pull.running.collectAsState()
    PullToRefreshBox(isRefreshing = running, onRefresh = pull::start, modifier = modifier.fillMaxSize(), content = content)
}
