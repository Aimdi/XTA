package com.aimdi.xtanative.plugins.substack

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.networks.substack.SubstackPublication
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.store.settings.SubstackPrefs
import com.aimdi.xtanative.ui.plugins.PluginSettings
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** What the plugin keeps through the app's stores: its follows as XTA's backup rows, and posts in the offline library. */
class SubstackStoresTest {
    private val store = StoreDir(Files.createTempDirectory("substack-plugin").toFile())
    private val app = SettingsStore(store).also { runBlocking { it.load() } }
    private val settings = PluginSettings("substack", app.state) { change -> app.update(change) }

    @Test
    fun theFollowsXtaKeptInAPreferenceAreTakenOverOnceAndKeptAsItsBackupRows(): Unit = runBlocking {
        settings.set(SubstackPrefs.publications, """[{"subdomain":"astral","baseUrl":"https://astral.substack.com","name":"Astral","logoUrl":"https://logo"}]""")
        val follows = SubstackFollowStore(store.file("follows.json"), settings)
        assertEquals(listOf("astral"), follows.load().map { it.publication.id })
        assertEquals("", app.get(SubstackPrefs.publications), "the preference is cleared, as XTA clears it")
        follows.follow(SubstackPublication("field", "https://www.field.example", "Field Notes"))
        val rows = J.parse(follows.export()).list
        assertEquals(listOf("astral", "field"), rows.map { it["id"].string })
        assertEquals(listOf("https://astral.substack.com", "https://www.field.example"), rows.map { it["base_url"].string })
        assertEquals("https://logo", rows[0]["logo_url"].string)
        assertEquals(1, rows[0]["in_feed"].int)
        assertTrue(rows.all { Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""").matches(it["created_at"].string.orEmpty()) })
        val again = SubstackFollowStore(store.file("follows.json"), settings)
        assertEquals(listOf("astral", "field"), again.load().map { it.publication.id }, "read back from the plugin's folder")
    }

    @Test
    fun aBackupsRowsReplaceTheFollows(): Unit = runBlocking {
        val follows = SubstackFollowStore(store.file("follows.json"), settings)
        follows.follow(SubstackPublication("old", "https://old.substack.com", "Old"))
        follows.import("""[{"id":"new","base_url":"https://new.substack.com","name":"New","logo_url":null,"in_feed":1,"created_at":"2026-01-02 03:04:05"},{"id":"bad"}]""")
        assertEquals(listOf("new"), follows.state.value?.map { it.publication.id })
        assertEquals("2026-01-02 03:04:05", follows.state.value?.single()?.createdAt)
    }

    @Test
    fun aPostGoesIntoTheOfflineLibraryAndComesBackWithItsBody() {
        val post = SubstackPost(
            "42", "On plans", "on-plans", "https://field.substack.com", "Field Notes",
            canonicalUrl = "https://field.substack.com/p/on-plans", bodyHtml = "<p>Body</p>", audience = "everyone", reactionCount = 3,
        )
        val article = SubstackOffline.articleOf(post)
        assertEquals("substack:field:42", article.id)
        assertEquals("substack", article.kind)
        assertEquals(post, SubstackOffline.postOf(article))
        assertNull(SubstackOffline.postOf(article.copy(payload = kotlinx.serialization.json.JsonObject(emptyMap()))))
        assertEquals("substack:field:on-plans", ReaderIds.readingIdOf(post))
    }
}
