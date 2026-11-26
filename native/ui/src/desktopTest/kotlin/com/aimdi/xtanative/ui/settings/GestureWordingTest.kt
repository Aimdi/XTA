package com.aimdi.xtanative.ui.settings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import org.w3c.dom.Element

/** The desktop's words for a gesture: its settings read the mouse's, and none of those names a touch. */
class GestureWordingTest {
    private val resources: File by lazy {
        listOf("src/commonMain/composeResources", "native/ui/src/commonMain/composeResources", "ui/src/commonMain/composeResources")
            .map(::File).firstOrNull { File(it, "values/strings_settings.xml").isFile }
            ?: fail("composeResources not found from ${File(".").absolutePath}")
    }

    @Test
    fun theDesktopsSettingsReadTheMousesWords() {
        val picked = with(GestureWording) {
            listOf(
                avatarChange, zenModeDescription, loadManuallyDescription, doubleTapLike, doubleTapLikeDescription,
                swipeBackAnywhere, swipeBackAnywhereDescription, swipeBackTilt, swipeBackTiltDescription, translationPrivacy,
            )
        }
        picked.forEach { assertTrue(it.key.endsWith("_desktop"), it.key) }
    }

    @Test
    fun noDesktopWordingNamesATouch() {
        val touch = Regex("\\b(tap|taps|tapping|long-press|long press|swipe|finger|hold)\\b", RegexOption.IGNORE_CASE)
        val desktop = resources.resolve("values").listFiles().orEmpty().filter { it.name.endsWith(".xml") }.flatMap { file ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.getElementsByTagName("string")
            (0 until nodes.length).map { nodes.item(it) as Element }.filter { it.getAttribute("name").endsWith("_desktop") }
        }
        assertTrue(desktop.size >= 17, "only ${desktop.size} desktop strings")
        desktop.forEach { assertTrue(!touch.containsMatchIn(it.textContent), "${it.getAttribute("name")}: ${it.textContent}") }
    }
}
