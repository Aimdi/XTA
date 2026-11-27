package com.aimdi.xtanative.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.settings.DesktopPrefs
import com.aimdi.xtanative.ui.DesktopWindowTitle
import com.aimdi.xtanative.ui.chrome.tray.TrayAction
import com.aimdi.xtanative.ui.chrome.tray.TrayService
import com.aimdi.xtanative.ui.home.LocalDesktopActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * How many posts are unread, said outside the window, as Thunderbird and
 * Telegram say it: before the window's title while it is not in use
 * ([DesktopWindowTitle]), as the tray icon's attention and tooltip, and on
 * the launcher's icon ([LauncherBadge]). The count alone, never a post's
 * author or words; nothing while Settings › General › Unread count outside
 * the window is off.
 */
object DesktopAttention {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** The tray's Refresh, which the main window's screen answers ([TrayRefreshFollows]). */
    val refreshAsked: SharedFlow<Unit> = refreshes.asSharedFlow()

    /** The count shown for [total] unread posts while the setting is [on]. */
    fun shown(total: Int, on: Boolean): Int = if (on) total.coerceAtLeast(0) else 0

    /** Follows [graph]'s unread marks from now on, telling the [tray], the [badge] and the window's title. */
    fun follow(graph: AppGraph, tray: TrayService = TrayService.desktop, badge: LauncherBadge = LauncherBadge.desktop) {
        tray.onAppAction = { action ->
            when (action) {
                TrayAction.MARK_ALL_READ -> graph.unread.markAllRead()
                TrayAction.REFRESH -> refreshes.tryEmit(Unit)
                else -> Unit
            }
        }
        graph.unread.reload()
        scope.launch {
            combine(graph.unread.now, graph.settings.observe(DesktopPrefs.unreadOutside)) { now, on -> shown(now.total, on) }
                .distinctUntilChanged()
                .collect { count ->
                    tray.unread(count)
                    badge.show(count)
                    DesktopWindowTitle.unread(count)
                }
        }
    }
}

/** The tray's Refresh in the main window: the screen shown reads again, as F5 has it. */
@Composable
internal fun TrayRefreshFollows() {
    val actions = LocalDesktopActions.current ?: return
    LaunchedEffect(actions) { DesktopAttention.refreshAsked.collect { actions.refresh() } }
}
