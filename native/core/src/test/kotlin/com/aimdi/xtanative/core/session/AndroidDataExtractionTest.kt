package com.aimdi.xtanative.core.session

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's android_data_extraction_test.dart: the session file holds X cookies, so no backup or transfer may copy it. */
class AndroidDataExtractionTest {
    private val domains = setOf(
        "root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref",
    )
    private val comments = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)

    private val app: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main") }
        .first { File(it, "AndroidManifest.xml").isFile }

    private fun read(path: String) = File(app, path).readText().replace(comments, "")

    private fun excluded(rules: String, section: String): Set<String> {
        val body = Regex("<$section>(.*?)</$section>", RegexOption.DOT_MATCHES_ALL).find(rules)?.groupValues?.get(1).orEmpty()
        return Regex("""<exclude domain="([a-z_]+)"\s*/>""").findAll(body).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun theAppOptsOutOfBackupAndNamesItsExtractionRules() {
        val manifest = read("AndroidManifest.xml")
        assertTrue("android:allowBackup=\"false\"" in manifest)
        assertTrue("android:dataExtractionRules=\"@xml/data_extraction_rules\"" in manifest)
    }

    @Test
    fun nothingGoesToACloudBackupOrADeviceToDeviceTransfer() {
        val rules = read("res/xml/data_extraction_rules.xml")
        assertEquals(domains, excluded(rules, "cloud-backup"))
        assertEquals(domains, excluded(rules, "device-transfer"))
        assertFalse("<include" in rules)
    }
}
