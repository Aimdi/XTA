package com.aimdi.xtanative.networks.rss

import java.io.File

/**
 * The feed documents the shared parser is tested with
 * (`native/networks/src/test/resources/rss`), which the client is tested
 * with too: found from wherever the tests run, as the repairs tests find
 * `repairs.json`, so the documents are kept once.
 */
internal object RssFixtures {
    private val folder: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .flatMap { sequenceOf(File(it, "networks/src/test/resources/rss"), File(it, "native/networks/src/test/resources/rss")) }
            .firstOrNull { it.isDirectory }
            ?: error("networks/src/test/resources/rss beside native/plugins")
    }

    fun text(name: String): String = File(folder, name).readText()
}
