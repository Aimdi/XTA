package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class PluginNoticesTest {
    @Test
    fun noticesReachTheScreenOnViewInTheOrderTold(): Unit = runBlocking {
        val notices = PluginNotices()
        val shown = async(start = CoroutineStart.UNDISPATCHED) { notices.notices.take(2).toList() }

        notices.tell("Account added")
        notices.tell("  ")
        notices.tell("Session works (zuck)")

        assertEquals(listOf("Account added", "Session works (zuck)"), shown.await(), "a blank notice is not shown")
    }
}
