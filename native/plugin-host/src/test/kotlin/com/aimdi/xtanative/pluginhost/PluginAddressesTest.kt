package com.aimdi.xtanative.pluginhost

import com.aimdi.xtanative.core.json.J
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.HttpUrl.Companion.toHttpUrl

class PluginAddressesTest {
    @Test
    fun pluginsComeOverHttpsOrFromThisMachine() {
        assertTrue(PluginAddresses.allowed("https://raw.githubusercontent.com/Aimdi/XTA/HEAD/plugins.json".toHttpUrl()))
        assertTrue(PluginAddresses.allowed("http://127.0.0.1:8788/plugins.json".toHttpUrl()), "the desktop check's catalogue")
        assertTrue(PluginAddresses.allowed("http://10.0.2.2:8788/plugins.json".toHttpUrl()), "the emulator's")
        assertFalse(PluginAddresses.allowed("http://plugins.example/plugins.json".toHttpUrl()), "anyone on the way could swap what comes")
    }

    @Test
    fun anArtifactOverPlainHttpIsNoArtifact() {
        val sha = "a".repeat(64)
        assertNull(PluginArtifact.from(J.parse("""{"url":"http://plugins.example/a.jar","sha256":"$sha","size":3}""")))
        assertEquals("https://plugins.example/a.jar", PluginArtifact.from(J.parse("""{"url":"https://plugins.example/a.jar","sha256":"$sha","size":3}"""))?.url)
    }
}
