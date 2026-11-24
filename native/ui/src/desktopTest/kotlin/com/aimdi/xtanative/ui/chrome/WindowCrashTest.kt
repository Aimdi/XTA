package com.aimdi.xtanative.ui.chrome

import androidx.compose.ui.ExperimentalComposeUiApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalComposeUiApi::class)
class WindowCrashTest {
    private fun env(vararg pairs: Pair<String, String>): (String) -> String? = pairs.toMap()::get

    @Test
    fun aTestCrashIsHonouredOnlyAgainstTheStandInForX() {
        assertNull(WindowCrash.testCrash(env("XTA_NATIVE_TEST_CRASH" to "main")))
        assertNull(WindowCrash.testCrash(env("XTA_NATIVE_TEST_CRASH" to "main", "XTA_NATIVE_MOCK_X" to " ")))
        assertEquals("main", WindowCrash.testCrash(env("XTA_NATIVE_TEST_CRASH" to "main", "XTA_NATIVE_MOCK_X" to "http://127.0.0.1:1")))
        assertEquals("post", WindowCrash.testCrash(env("XTA_NATIVE_TEST_CRASH" to " post ", "XTA_NATIVE_MOCK_X" to "http://127.0.0.1:1")))
        assertNull(WindowCrash.testCrash(env("XTA_NATIVE_TEST_CRASH" to "other", "XTA_NATIVE_MOCK_X" to "http://127.0.0.1:1")))
        assertNull(WindowCrash.testCrash(env("XTA_NATIVE_MOCK_X" to "http://127.0.0.1:1")))
    }

    @Test
    fun onlyTheFirstCrashRecordsAndQuits() {
        val recorded = mutableListOf<Throwable>()
        var quits = 0
        val handler = WindowCrash.handler({ recorded += it }, { quits++ }, halt = {})
        val first = IllegalStateException("first")
        handler.onException(first)
        handler.onException(IllegalStateException("again"))
        assertEquals(listOf<Throwable>(first), recorded)
        assertEquals(1, quits)
    }
}
