package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class XtaRowsTest {
    @Test
    fun aSavedRowKeepsItsPostAsTextAsXtaDoes() {
        val row = J(json("""{"id":"99","content":"{\"id_str\":\"99\",\"full_text\":\"hi\"}","user_id":"7","folder_id":"f1","note":"mine"}"""))
        val post = XtaRows.savedPost(row, savedAt = 5)!!
        assertEquals(SavedPost("99", "7", json("""{"id_str":"99","full_text":"hi"}"""), "f1", "mine", 5), post)
        assertEquals("""{"id_str":"99","full_text":"hi"}""", J(XtaRows.row(post))["content"].string)
        assertEquals(post, XtaRows.savedPost(J(XtaRows.row(post)), savedAt = 5))
    }

    @Test
    fun aRowWithoutAPostOrWithBrokenTextKeepsTheRowWithoutContent() {
        assertNull(XtaRows.savedPost(J(json("""{"id":"1","content":"{broken"}""")), 0)?.content)
        assertNull(XtaRows.savedPost(J(json("""{"id":"1","content":null}""")), 0)?.content)
        assertNull(XtaRows.savedPost(J(json("""{"content":"{}"}""")), 0))
    }

    @Test
    fun foldersAndLikesRoundTripInXtasColumns() {
        val folder = SavedFolder("f1", "Art", 2, 1_787_918_400_000, autoDownload = true, autoUpload = false)
        val row = J(XtaRows.row(folder))
        assertEquals(1L, row["auto_download"].long)
        assertEquals("2026-08-28T12:00:00Z", row["created_at"].string)
        assertEquals(folder, XtaRows.folder(row))
        assertEquals(1_787_918_400_000, XtaRows.folder(J(json("""{"id":"f","name":"n","created_at":"2026-08-28 12:00:00"}""")))?.createdAt)

        val like = LikedPost("l1", "u1", json("""{"id_str":"l1"}"""), 3)
        assertEquals(like, XtaRows.likedPost(J(XtaRows.row(like)), likedAt = 3))
    }
}
