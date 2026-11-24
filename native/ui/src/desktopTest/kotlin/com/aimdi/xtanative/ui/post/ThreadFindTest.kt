package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XWithheld
import com.aimdi.xtanative.ui.common.FindBlock
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadFindTest {
    private fun post(id: String, text: String = "text $id", quoted: XPost? = null) = XPost(id, XUser("u", "u", "U"), null, text, quoted = quoted)

    @Test
    fun eachTextIsInTheRowTheListDrawsItIn() {
        val state = PostUi(
            withheldBefore = listOf(XWithheld("w", null)),
            before = listOf(post("b1")),
            focal = post("f", "Fish &amp; chips", quoted = post("q")),
            replies = listOf(XThread("r1", listOf(post("r1"), post("r2"))), XThread("r3", listOf(post("r3")))),
            loading = false,
        )
        val blocks = ThreadFind.blocks(state, threaded = false, repliesShown = true)
        assertEquals(
            listOf(
                FindBlock("b1", "text b1", 1),
                FindBlock("f", "Fish & chips", 2),
                FindBlock("quote-q", "text q", 2),
                FindBlock("r1", "text r1", 3),
                FindBlock("r2", "text r2", 3),
                FindBlock("r3", "text r3", 4),
            ),
            blocks,
        )
    }

    @Test
    fun aLoadingRowCountsAndHiddenRepliesAreNotFound() {
        val loading = PostUi(focal = post("f"), replies = emptyList(), loading = true)
        assertEquals(listOf(FindBlock("f", "text f", 0)), ThreadFind.blocks(loading, threaded = false, repliesShown = true))
        val zen = PostUi(focal = post("f"), replies = listOf(XThread("r", listOf(post("r")))), loading = false)
        assertEquals(listOf("f"), ThreadFind.blocks(zen, threaded = false, repliesShown = false).map { it.key })
    }
}
