package com.aimdi.xtanative.networks

import com.aimdi.xtanative.core.repairs.Repairs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * XTA's published `repairs.json` at the repository's root, which both apps
 * read: every network whose values it repairs is a plugin
 * (`native/plugins/<id>`), which declares its slots and whose own tests check
 * each published value against the one it ships. None is left in :networks.
 */
class NetworkRepairsTest {
    @Test
    fun everyPublishedValueBelongsToAPluginThatChecksIt() {
        val plugins = File(repairsJson().parentFile, "native/plugins").listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.toSet()
        val published = Repairs.parse(repairsJson().readText()).keys

        assertTrue(published.isNotEmpty(), "repairs.json publishes values")
        published.forEach { key -> assertTrue(networkOf(key) in plugins, "$key: no plugin in native/plugins has it") }
    }

    /** The network a key repairs: `pixiv` of `pixiv.app_version`. */
    private fun networkOf(key: String): String = key.substringBefore('.')

    /** The repository's `repairs.json`, found from wherever the tests run (native/networks under Gradle). */
    private fun repairsJson(): File {
        val found = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "repairs.json") }
            .firstOrNull { it.isFile && File(it.parentFile, "native").isDirectory }
        return assertNotNull(found, "repairs.json beside native/")
    }
}
