package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.ui.saved.ExportFormat
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class PostExportsTest {
    private val author = XUser("1", "jane", "Jane Doe")

    private fun post(id: String) = XPost(id, author, Instant.EPOCH, "post $id")

    @Test
    fun `a post in a thread saves the whole thread`() {
        val thread = XThread("1", listOf(post("1"), post("2"), post("3")))
        assertEquals(listOf("1", "2", "3"), PostExports.posts(post("2"), thread).map { it.id })
    }

    @Test
    fun `a post alone, or one of another thread, saves itself`() {
        assertEquals(listOf("9"), PostExports.posts(post("9"), null).map { it.id })
        assertEquals(listOf("9"), PostExports.posts(post("9"), XThread("1", listOf(post("1"), post("2")))).map { it.id })
    }

    @Test
    fun `the file is named after the first post's author and id`() {
        assertEquals("jane 1.md", PostExports.fileName(listOf(post("1"), post("2")), ExportFormat.MARKDOWN))
        assertEquals("jane 1.html", PostExports.fileName(listOf(post("1")), ExportFormat.HTML))
    }
}
