package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertEquals

class TextSaveEndedTest {
    private fun told(placed: Boolean, name: String?): List<String> {
        val told = mutableListOf<String>()
        textSaveEnded(placed, name, onSaved = { told += "saved $it" }, onFailed = { told += "failed" })
        return told
    }

    @Test
    fun aFileWrittenIsToldUnderItsName() {
        assertEquals(listOf("saved novel.txt"), told(placed = true, name = "novel.txt"))
    }

    @Test
    fun aFilePlacedButNotWrittenIsAFailure() {
        assertEquals(listOf("failed"), told(placed = true, name = null))
    }

    @Test
    fun backingOutOfTheDialogTellsNothing() {
        assertEquals(emptyList(), told(placed = false, name = null))
    }
}
