package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.saved.SavedLibrary
import com.aimdi.xtanative.store.saved.SavedPost
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginSavedTest {
    private fun saved(id: String, json: String?, note: String? = null, author: String? = null) =
        SavedPost(id, author, json?.let { J.parse(it).raw }, note = note)

    private val library = SavedLibrary(
        listOf(
            saved("threads:https://www.threads.com/@zuck/post/B", """{"xtaPlugin":"threads","post":{"id":"2","handle":"zuck"}}""", note = "read later", author = "zuck"),
            saved("1", """{"id_str":"1","full_text":"an X post"}"""),
            saved(
                "mastodon:https://example.social/@alice/9",
                """{"xtaPlugin":"mastodon","post":{"version":1,"id":"9","acct":"alice@example.social","url":"https://example.social/@alice/9"}}""",
                author = "alice@example.social",
            ),
            saved("threads:https://www.threads.com/@zuck/post/A", """{"xtaPlugin":"link","source":"threads","url":"https://www.threads.com/@zuck/post/A","author":"zuck","text":"kept as a link"}"""),
            saved("threads:too-large", null),
            saved("instagram:https://www.instagram.com/p/C/", PluginPostArchive.link("instagram", "https://www.instagram.com/p/C/", "ann", "a photo").content.toString()),
        ),
    )

    @Test
    fun aPluginsSavedPostsAreThoseItsRecordsNameNewestFirstWithTheirNotes() {
        val threads = PluginSaved.posts(library, "threads")
        assertEquals(listOf("threads:https://www.threads.com/@zuck/post/B", "threads:https://www.threads.com/@zuck/post/A"), threads.map { it.post.id })
        assertEquals(listOf("read later", null), threads.map { it.note })
        assertEquals("zuck", threads.first().post.authorId)
        assertEquals("2", J(threads.first().post.content)["post"]["id"].string, "the record as it was kept, for the plugin to read back")
    }

    @Test
    fun eachPluginHasOnlyItsOwnAndXsPostsAreNoPlugins() {
        assertEquals(listOf("mastodon:https://example.social/@alice/9"), PluginSaved.posts(library, "mastodon").map { it.post.id })
        assertEquals(listOf("instagram:https://www.instagram.com/p/C/"), PluginSaved.posts(library, "instagram").map { it.post.id }, "a link record is its source's")
        assertTrue(PluginSaved.posts(library, "bluesky").isEmpty())
        assertTrue(PluginSaved.posts(SavedLibrary(), "threads").isEmpty())
    }
}
