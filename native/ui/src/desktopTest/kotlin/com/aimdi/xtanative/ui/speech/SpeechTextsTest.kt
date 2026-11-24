package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.ui.post.post
import com.aimdi.xtanative.ui.post.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class SpeechTextsTest {
    private val focal = post("1", by = "ana", text = "first")

    @Test
    fun aPostNobodyWentOnWithIsReadAlone() {
        assertEquals(listOf("1"), SpeechTexts.listened(focal, emptyList()).map { it.id })
        val others = listOf(thread(post("2", by = "bo", replyToId = "1")), thread(post("3", by = "cy", replyToId = "1")))
        assertEquals(listOf("1"), SpeechTexts.listened(focal, others).map { it.id })
    }

    @Test
    fun theAuthorsOwnThreadIsReadAfterThePost() {
        val own = thread(post("2", by = "ana", text = "second", replyToId = "1"), post("3", by = "ana", text = "third", replyToId = "2"))
        val replies = listOf(thread(post("9", by = "bo", replyToId = "1")), own)
        val read = SpeechTexts.listened(focal, replies)
        assertEquals(listOf("1", "2", "3"), read.map { it.id })
        assertEquals("first\n\nsecond\n\nthird", SpeechTexts.thread(read))
    }

    @Test
    fun theThreadEndsWhereSomeoneElseAnswers() {
        val mixed = thread(post("2", by = "ana", replyToId = "1"), post("3", by = "bo", replyToId = "2"), post("4", by = "ana", replyToId = "3"))
        assertEquals(listOf("1", "2"), SpeechTexts.listened(focal, listOf(mixed)).map { it.id })
    }

    @Test
    fun theAuthorsAnswerToSomeoneElseIsNoThread() {
        val answer = thread(post("5", by = "bo", replyToId = "1"), post("6", by = "ana", replyToId = "5"))
        val elsewhere = thread(post("7", by = "ana", replyToId = "0"))
        assertEquals(listOf("1"), SpeechTexts.listened(focal, listOf(answer, elsewhere)).map { it.id })
    }
}
