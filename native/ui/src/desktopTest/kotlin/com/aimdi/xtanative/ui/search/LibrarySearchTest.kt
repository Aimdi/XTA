package com.aimdi.xtanative.ui.search

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.session.SavedSearch
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.offline.OfflineArticle
import com.aimdi.xtanative.store.offline.OfflineEntry
import com.aimdi.xtanative.store.offline.OfflineFile
import com.aimdi.xtanative.store.offline.OfflineKind
import com.aimdi.xtanative.store.saved.ArchiveAnnotation
import com.aimdi.xtanative.store.saved.LocalPost
import com.aimdi.xtanative.store.saved.SavedLibrary
import com.aimdi.xtanative.store.saved.SavedPost
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class LibrarySearchTest {
    private fun json(text: String) = J.parse(text).raw as JsonObject

    private val xPost = SavedPost("100", "1", json("""{"id_str":"100","full_text":"Dinner at the Café","user":{"name":"Maya","screen_name":"maya"}}"""), note = "try the soup")
    private val redditPost = SavedPost(
        "reddit:r1",
        "rust",
        json("""{"xtaPlugin":"reddit","post":{"id":"r1","title":"Ownership tips","subreddit":"rust","permalink":"/r/rust/r1","author":"ferris"}}"""),
    )
    private val note = LocalPost("n1", "Groceries for Łódź", 1, 1)
    private val reply = LocalPost("n2", "and bread", 2, 2, replyTo = "n1")
    private val session = Session(
        following = listOf(FollowedAccount("1", "maya", "Maya Cook", null, false, true, 0)),
        searches = listOf(SavedSearch("kotlin coroutines", 0)),
        groups = listOf(FollowGroup("b", "Recipes", emptyList()), FollowGroup("a", "Cooking", emptyList(), pinned = true)),
    )

    private fun entry(id: String, kind: OfflineKind, sensitive: Boolean = false, files: List<OfflineFile> = emptyList()) =
        OfflineEntry(id, "dir-$id", "Title $id", "Example Blog", kind, files.size, files, 0, articleBytes = if (kind == OfflineKind.ARTICLE) 10 else 0, sensitive = sensitive)

    private val documents by lazy {
        LibrarySearch.saved(SavedLibrary(listOf(xPost, redditPost))) + LibrarySearch.notes(listOf(note, reply)) +
            LibrarySearch.accounts(session) + LibrarySearch.groups(session) +
            listOf(LibrarySearch.article(entry("art", OfflineKind.ARTICLE), "A long read on sourdough", ArchiveAnnotation(tags = setOf("baking"))))
    }

    @Test
    fun aSearchFoldsCaseAccentsAndCompatibilityFormsAlike() {
        assertEquals("cafe lodz ecole fine strasse", LibrarySearch.fold("Café Łódź ÉCOLE ﬁne Straße"))
        assertEquals(listOf("cafe", "ole"), LibrarySearch.words("  CAFÉ   olé "))
        assertTrue(LibrarySearch.words(" \n ").isEmpty())
        assertFalse(LibrarySearch.fold("が").contains(LibrarySearch.fold("か")), "a script's own signs are no accents")
        assertEquals("ｶﾌｪ".let(LibrarySearch::fold), "カフェ".let(LibrarySearch::fold), "half-width kana are the same letters")
    }

    @Test
    fun aResultHoldsEveryWordInTheOrderXtaListsThings() {
        assertEquals(documents, LibrarySearch.results(documents, "", null), "nothing typed finds everything")
        assertEquals(
            listOf(LibraryKind.SAVED, LibraryKind.SAVED, LibraryKind.NOTE, LibraryKind.NOTE, LibraryKind.ACCOUNT, LibraryKind.ACCOUNT, LibraryKind.GROUP, LibraryKind.GROUP, LibraryKind.ARTICLE),
            documents.map { it.kind },
        )
        assertEquals(listOf("saved:100"), LibrarySearch.results(documents, "cafe SOUP", null).map { it.id }, "the post's text and the reader's note")
        assertEquals(listOf("note:n1"), LibrarySearch.results(documents, "lodz", null).map { it.id })
        assertEquals(listOf("saved:100", "account:1"), LibrarySearch.results(documents, "maya", null).map { it.id }, "an author is found by name too")
        assertEquals(listOf("account:1"), LibrarySearch.results(documents, "maya", LibraryKind.ACCOUNT).map { it.id }, "a chip keeps one kind")
        assertEquals(listOf("article:art"), LibrarySearch.results(documents, "sourdough baking blog", null).map { it.id }, "an article by its text, notes and source")
        assertEquals(listOf("group:a", "group:b"), LibrarySearch.results(documents, "", LibraryKind.GROUP).map { it.id }, "the groups in XTA's order")
        assertTrue(LibrarySearch.results(documents, "cafe nothing", null).isEmpty())
    }

    @Test
    fun eachResultOpensInItsOwnScreen() {
        val targets = documents.associate { it.id to it.target }
        assertEquals(LibraryTarget.XPost("100"), targets["saved:100"])
        assertEquals(LibraryTarget.Saved, targets["saved:reddit:r1"], "another network's post is in Saved")
        assertEquals(LibraryTarget.NoteThread("n1"), targets["note:n2"], "a reply opens its thread from the start")
        assertEquals(LibraryTarget.Profile("maya", "1"), targets["account:1"], "an account opens by its id")
        assertEquals(LibraryTarget.Search("kotlin coroutines"), targets["search:kotlin coroutines"])
        assertEquals(LibraryTarget.Group("a"), targets["group:a"])
        assertEquals(LibraryTarget.Article("art"), targets["article:art"])
        val media = entry("saved:9", OfflineKind.MEDIA, files = listOf(OfflineFile("0.jpg", "https://e/0.jpg", "image/jpeg", 10)))
        assertEquals(LibraryTarget.OfflineMedia("saved:9"), LibrarySearch.article(media, "", ArchiveAnnotation()).target)
        assertEquals(listOf("art"), LibrarySearch.searchable(listOf(entry("art", OfflineKind.ARTICLE), entry("nsfw", OfflineKind.MEDIA, sensitive = true))).map { it.id })
        assertEquals(LibraryTarget.XPost("123"), LibrarySearch.saved(SavedLibrary(listOf(SavedPost("123", null, null)))).single().target, "too large to keep, still X's")
    }

    @Test
    fun aResultSaysWhatItIsOnAFewLines() {
        assertEquals("dinner at the café maya", LibrarySearch.preview("dinner at the café\nmaya   "))
        assertEquals(240, LibrarySearch.preview("word ".repeat(100)).length)
        assertEquals("dinner at the café", documents.first().title, "a saved post's first line")
        assertFalse(LibrarySearchUi(loading = true).saysNoResults(emptyList()), "not before anything was read")
        val read = LibrarySearchUi(documents = documents, query = "nothing like it", loading = true)
        assertTrue(read.saysNoResults(read.results))
    }

    @Test
    fun theIndexReadsTheReadersStores(): Unit = runBlocking {
        val dir = Files.createTempDirectory("library").toFile()
        try {
            val graph = AppGraph(dir, flowOf(true), "test")
            graph.saved.file(xPost.id, xPost.authorId, xPost.content)
            graph.localPosts.save("Groceries for Łódź")
            graph.sessions.update { session }
            graph.offline.keepArticle(OfflineArticle("art", "Sourdough", "Example Blog", "<p>A <b>long</b> read</p><script>x()</script>", JsonObject(emptyMap())))
            graph.archiveNotes.toggleTag("art", "baking")
            val index = LibraryIndex(graph)
            val local = index.local()
            assertEquals(listOf("saved:100"), local.filter { it.kind == LibraryKind.SAVED }.map { it.id })
            assertEquals("Groceries for Łódź", local.single { it.kind == LibraryKind.NOTE }.title)
            assertEquals(listOf("account:1", "search:kotlin coroutines", "group:a", "group:b"), local.filter { it.kind == LibraryKind.ACCOUNT || it.kind == LibraryKind.GROUP }.map { it.id })
            val article = index.articles().single()
            assertEquals("Sourdough", article.title)
            assertEquals(listOf("article:art"), LibrarySearch.results(listOf(article), "long read baking", null).map { it.id })
            assertTrue(LibrarySearch.results(listOf(article), "x()", null).isEmpty(), "a script is no text")
        } finally {
            dir.deleteRecursively()
        }
    }
}
