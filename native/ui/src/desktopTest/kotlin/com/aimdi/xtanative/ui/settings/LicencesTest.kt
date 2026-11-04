package com.aimdi.xtanative.ui.settings

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Settings › App info › Licences: the list the build writes (gradle/licences.gradle.kts), as XTA's licence page shows it. */
class LicencesTest {
    private val json = """
        {"libraries":[
          {"name":"okhttp","module":"com.squareup.okhttp3:okhttp","version":"4.12.0","url":"https://square.github.io/okhttp/","licences":["Apache-2.0"],"texts":["tapache"]},
          {"name":"jsoup Java HTML Parser","module":"org.jsoup:jsoup","version":"1.18.3","licences":["MIT"],"texts":["tjsoup"]},
          {"name":"Compose Runtime","module":"org.jetbrains.compose.runtime:runtime-desktop","version":"1.8.2","licences":["Apache-2.0"],"texts":["tapache","tnotice"]},
          {"module":"org.example:no-name","version":"1","licences":[],"texts":[]}
        ],
        "texts":{"tapache":"Apache License\nVersion 2.0\n\n   TERMS","tjsoup":"The MIT License\n\nCopyright (c) Jonathan Hedley","tnotice":"NOTICE"}}
    """.trimIndent()

    @Test
    fun theLibrariesAreInNameOrderWhateverTheirCase() {
        val list = LicenceList.parse(json)
        assertEquals(listOf("Compose Runtime", "jsoup Java HTML Parser", "no-name", "okhttp"), list.libraries.map { it.name })
    }

    @Test
    fun aLibraryHasItsOwnTextsSharedOnesKeptOnce() {
        val list = LicenceList.parse(json)
        val compose = list.find("org.jetbrains.compose.runtime:runtime-desktop")!!
        assertEquals(listOf("Apache License\nVersion 2.0\n\n   TERMS", "NOTICE"), list.textsOf(compose))
        assertEquals(list.textsOf(list.find("com.squareup.okhttp3:okhttp")!!).first(), list.textsOf(compose).first())
        assertEquals("https://square.github.io/okhttp/", list.find("com.squareup.okhttp3:okhttp")!!.url)
        assertNull(list.find("org.example:missing"))
    }

    @Test
    fun aListThatCannotBeReadIsEmptyAndThePlatformsJoinInOrder() {
        assertEquals(emptyList(), LicenceList.parse("not json").libraries)
        val runtime = LicenceList(listOf(ShippedLibrary("OpenJDK", "openjdk:java-runtime", "17", null, listOf("GPL-2.0-with-classpath-exception"), listOf("java:LICENSE"))), mapOf("java:LICENSE" to "GPL"))
        val joined = LicenceList.parse(json) + runtime
        assertEquals(listOf("Compose Runtime", "jsoup Java HTML Parser", "no-name", "okhttp", "OpenJDK"), joined.libraries.map { it.name })
        assertEquals(listOf("GPL"), joined.textsOf(joined.find("openjdk:java-runtime")!!))
    }

    @Test
    fun aLicenceReadsParagraphByParagraphWithACloverBeforeTheNext() {
        val paragraphs = LicenceText.paragraphs(listOf("Apache License\nVersion 2.0\n\n   TERMS\n", "NOTICE"))
        assertEquals(
            listOf(
                LicenceText.Paragraph.Words("Apache License\nVersion 2.0"),
                LicenceText.Paragraph.Words("   TERMS"),
                LicenceText.Paragraph.Between,
                LicenceText.Paragraph.Words("NOTICE"),
            ),
            paragraphs,
        )
    }

    @Test
    fun theDesktopsJavaRuntimeIsListedFromItsOwnNotices() {
        val legal = Files.createTempDirectory("legal").toFile()
        try {
            assertSame(LicenceList.EMPTY, JavaRuntimeLicence.of(legal, "17.0.12"))
            legal.resolve("LICENSE").writeText("The GNU General Public License (GPL)\n")
            legal.resolve("ASSEMBLY_EXCEPTION").writeText("OPENJDK ASSEMBLY EXCEPTION")
            val list = JavaRuntimeLicence.of(legal, "17.0.12")
            val runtime = list.libraries.single()
            assertEquals("OpenJDK", runtime.name)
            assertEquals("17.0.12", runtime.version)
            assertEquals(listOf("The GNU General Public License (GPL)", "OPENJDK ASSEMBLY EXCEPTION"), list.textsOf(runtime))
        } finally {
            legal.deleteRecursively()
        }
    }
}
