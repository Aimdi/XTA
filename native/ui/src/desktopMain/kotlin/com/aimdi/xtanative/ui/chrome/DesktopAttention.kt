package com.aimdi.xtanative.ui.chrome

import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.settings.DesktopPrefs
import com.aimdi.xtanative.ui.DesktopWindowTitle
import com.aimdi.xtanative.ui.chrome.tray.TrayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    /** The count shown for [total] unread posts while the setting is [on]. */
    fun shown(total: Int, on: Boolean): Int = if (on) total.coerceAtLeast(0) else 0

    /** Follows [graph]'s unread marks from now on, telling the [tray], the [badge] and the window's title. */
    fun follow(graph: AppGraph, tray: TrayService = TrayService.desktop, badge: LauncherBadge = LauncherBadge.desktop) {
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
