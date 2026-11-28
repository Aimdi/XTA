package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XAccountHealth
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.currentLocale
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.home_notice_reads_paused
import com.aimdi.xtanative.ui.generated.resources.home_notice_signed_out
import com.aimdi.xtanative.ui.generated.resources.settings_account
import com.aimdi.xtanative.ui.settings.shortDateTime
import java.time.Instant
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** What Home tells the reader about X's side of the reads, the most pressing first. */
sealed interface XReadNotice {
    /** Every X read is paused until [until] (epoch milliseconds): X called two accounts automated. */
    data class Paused(val until: Long) : XReadNotice

    /** These accounts' sign-ins look broken: their reads were refused again and again. */
    data class SignedOut(val names: List<String>) : XReadNotice

    companion object {
        /** The notice for now, or none: [health] is each account's, by id. */
        fun of(accounts: List<XAccount>, health: (String) -> XAccountHealth, pausedUntil: Long?, now: Long): XReadNotice? {
            if (pausedUntil != null && pausedUntil > now) return Paused(pausedUntil)
            val out = accounts.filter { health(it.id).let { state -> state.flagged(now) || state.needsProbe(now) } }
            return if (out.isEmpty()) null else SignedOut(out.map { "@" + (it.screenName ?: "?") })
        }

        /** When [notice] changes on its own, with nothing read: a pause ends. */
        fun endsAt(notice: XReadNotice?): Long? = (notice as? Paused)?.until
    }
}

/**
 * A thin row over Home's pages while X pauses the reads or an account looks
 * signed out: what happened, Accounts for the latter, and Close, which hides
 * this notice until another one comes.
 */
@Composable
internal fun XReadNoticeBar(onAccounts: (() -> Unit)?) {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val paused by graph.client.readsPausedUntil.collectAsStateWithLifecycle()
    val changes by graph.client.accountHealthChanges.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val notice = remember(session, paused, changes, now) {
        XReadNotice.of(session?.accounts.orEmpty(), graph.accountHealth::read, paused, now)
    }
    LaunchedEffect(notice, paused, changes) {
        val ends = XReadNotice.endsAt(notice) ?: return@LaunchedEffect
        delay((ends - System.currentTimeMillis()).coerceAtLeast(0) + 1_000)
        now = System.currentTimeMillis()
    }
    var dismissed by remember { mutableStateOf<XReadNotice?>(null) }
    if (notice == null || notice == dismissed) return
    val text = when (notice) {
        is XReadNotice.Paused -> stringResource(Res.string.home_notice_reads_paused, shortDateTime(Instant.ofEpochMilli(notice.until), currentLocale()))
        is XReadNotice.SignedOut -> stringResource(Res.string.home_notice_signed_out, notice.names.joinToString(", "))
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp))
            if (notice is XReadNotice.SignedOut && onAccounts != null) TextButton(onClick = onAccounts) { Text(stringResource(Res.string.settings_account)) }
            IconTip(stringResource(Res.string.close)) {
                IconButton(onClick = { dismissed = notice }) { Icon(Icons.Filled.Close, stringResource(Res.string.close), modifier = Modifier.size(18.dp)) }
            }
        }
    }
}
