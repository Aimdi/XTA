package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.store.settings.RssPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class RssReadsTest {
    @Test
    fun readIdsAreNewestFirstAndCapped() {
        val ids = (1..RssReads.CAP).map { "old$it" }

        val marked = RssReads.marked(ids, listOf("new", "old1", "", "new"))

        assertEquals(RssReads.CAP, marked.size)
        assertEquals(listOf("new", "old1", "old2"), marked.take(3))
        assertEquals(ids, RssReads.marked(ids, listOf("old5")), "what was read stays where it is")
        assertEquals(listOf("a", "b"), RssReads.decode(RssReads.encode(listOf("a", "b"))))
        assertEquals(listOf("a"), RssReads.decode("""["a", "", 3]"""))
    }

    @Test
    fun anRssItemAndAWebSourcesPostAreMarkedInTheOneList(): Unit = runBlocking {
        val app = MutableStateFlow<Settings?>(Settings().with(RssPrefs.readIds, "[\"https://blog.example/feed::1\"]"))
        val websources = PluginSettings("websources", app) { change -> app.value = change(app.value!!) }

        RssReads.mark(websources, listOf("lobsters:abc"))

        assertEquals(setOf("lobsters:abc", "https://blog.example/feed::1"), RssReads.of(app.value))
        assertEquals("[\"lobsters:abc\",\"https://blog.example/feed::1\"]", app.value!![RssPrefs.readIds], "as XTA's RssReadStore writes them")
        assertEquals(emptySet(), RssReads.of(null), "nothing is read before the settings are")
    }
}
