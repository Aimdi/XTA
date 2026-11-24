package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.store.settings.DesktopPrefs
import com.aimdi.xtanative.store.settings.ProxyMode
import com.aimdi.xtanative.store.settings.ProxyType
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProxyFormTest {
    @Test
    fun aFormIsCompleteWithAHostAndAPort() {
        assertTrue(ProxyForm(host = "proxy.lan", port = "3128").isComplete)
        assertTrue(ProxyForm(host = "[fd00::1]", port = "1080").isComplete)
        assertFalse(ProxyForm(host = "", port = "3128").isComplete)
        assertFalse(ProxyForm(host = "proxy.lan", port = "").isComplete)
        assertFalse(ProxyForm(host = "proxy.lan", port = "70000").isComplete)
        assertFalse(ProxyForm(host = "http://proxy.lan", port = "3128").isComplete)
        assertFalse(ProxyForm(host = "proxy lan", port = "3128").isComplete)
    }

    @Test
    fun savingChoosesManualAndReadsBack() {
        val form = ProxyForm(ProxyType.SOCKS5, " proxy.lan ", "1080", "me", "pw", " nas.lan ")
        val saved = form.saved(Settings())
        assertEquals(ProxyMode.MANUAL, saved[DesktopPrefs.proxyMode])
        assertEquals("proxy.lan", saved[DesktopPrefs.proxyHost])
        assertEquals(1080, saved[DesktopPrefs.proxyPort])
        assertEquals(ProxyForm(ProxyType.SOCKS5, "proxy.lan", "1080", "me", "pw", "nas.lan"), ProxyForm.of(saved))
        assertEquals("proxy.lan:1080", ProxyForm.summary(saved))
        assertNull(ProxyForm.summary(Settings()))
    }
}
