package com.aimdi.xtanative.ui.common

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Compose's resources pick a language folder by the language exactly as the
 * platform's Locale gives it. Android's still says "in" for Indonesian, "iw"
 * for Hebrew and "ji" for Yiddish; Java 17, the desktop's, says "id", "he"
 * and "yi". So each such language has its folder under the new code and a
 * link to it under the old one.
 */
class LegacyLanguageFoldersTest {
    private val resources: File by lazy {
        listOf("src/commonMain/composeResources", "native/ui/src/commonMain/composeResources", "ui/src/commonMain/composeResources")
            .map(::File).firstOrNull { File(it, "values-id").isDirectory }
            ?: fail("composeResources not found from ${File(".").absolutePath}")
    }

    private val legacyCodes = mapOf("id" to "in", "he" to "iw", "yi" to "ji")

    @Test
    fun aLanguageAndroidNamesByItsOldCodeIsFoundUnderBoth() {
        legacyCodes.filterKeys { File(resources, "values-$it").isDirectory }.forEach { (code, old) ->
            val link = File(resources, "values-$old")
            assertTrue(Files.isSymbolicLink(link.toPath()), "values-$old is a link, so nobody keeps a second copy of values-$code")
            assertEquals(File(resources, "values-$code").canonicalFile, link.canonicalFile)
        }
        assertTrue(File(resources, "values-in").isDirectory, "Indonesian is one of XTA's languages")
    }
}
