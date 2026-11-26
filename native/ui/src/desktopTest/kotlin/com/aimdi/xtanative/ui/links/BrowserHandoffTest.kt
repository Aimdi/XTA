package com.aimdi.xtanative.ui.links

import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BrowserHandoffTest {
    @Test
    fun aLinkIsOpenedOffTheThreadThatClickedIt() {
        val release = CountDownLatch(1)
        val opened = LinkedBlockingQueue<Pair<String, String>>()
        val browser = BrowserHandoff { url ->
            release.await(5, TimeUnit.SECONDS)
            opened.add(url to Thread.currentThread().name)
        }
        // A browser that takes its time does not hold up the caller.
        assertTrue(browser.send("https://example.com/a"))
        assertTrue(opened.isEmpty())
        release.countDown()
        browser.settle()
        val (url, thread) = opened.poll(5, TimeUnit.SECONDS)!!
        assertEquals("https://example.com/a", url)
        assertNotEquals(Thread.currentThread().name, thread)
    }

    @Test
    fun onlyWebLinksLeaveAndAFailingBrowserThrowsNothing() {
        val opened = mutableListOf<String>()
        val browser = BrowserHandoff { url -> synchronized(opened) { opened += url }; error("no browser") }
        assertFalse(browser.send("file:///etc/passwd"))
        assertFalse(browser.send("javascript:alert(1)"))
        assertTrue(browser.send("http://example.com/b"))
        assertTrue(browser.send("https://example.com/c"))
        browser.settle()
        assertEquals(listOf("http://example.com/b", "https://example.com/c"), synchronized(opened) { opened.toList() })
    }
}
