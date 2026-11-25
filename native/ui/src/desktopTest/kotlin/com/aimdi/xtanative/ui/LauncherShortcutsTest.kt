package com.aimdi.xtanative.ui

import com.aimdi.xtanative.ui.links.LauncherShortcut
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The launcher's shortcuts ([LauncherShortcut], [LauncherShortcuts]) and the files Android reads them from. */
class LauncherShortcutsTest {
    private fun action(name: String) = LauncherShortcut.PREFIX + name

    @Test
    fun eachShortcutOpensItsScreen() {
        assertEquals("search?focus=1", LauncherShortcuts.route(action("SEARCH"), Routes.HOME))
        assertEquals(Routes.SAVED, LauncherShortcuts.route(action("SAVED"), Routes.HOME))
        assertEquals(Routes.SUBSCRIPTIONS, LauncherShortcuts.route(action("SUBSCRIPTIONS"), Routes.HOME))
        assertEquals(Routes.SAVED, LauncherShortcuts.route(action("SAVED"), Routes.SUBSCRIPTIONS), "over another screen too")
        assertEquals(Routes.DOWNLOADS, LauncherShortcuts.route(action("DOWNLOADS"), Routes.HOME), "the downloads' notification")
        assertNull(LauncherShortcuts.route(action("DOWNLOADS"), Routes.DOWNLOADS))
    }

    @Test
    fun otherIntentsAreNoShortcuts() {
        assertFalse(LauncherShortcut.isOne(null))
        assertFalse(LauncherShortcut.isOne("android.intent.action.MAIN"))
        assertFalse(LauncherShortcut.isOne("android.intent.action.VIEW"))
        assertTrue(LauncherShortcut.isOne(action("SAVED")))
        assertNull(LauncherShortcuts.route(null, Routes.HOME))
        assertNull(LauncherShortcuts.route("android.intent.action.SEND", Routes.HOME))
        assertNull(LauncherShortcuts.route(action("COMPOSE"), Routes.HOME), "a shortcut this version does not have")
        assertNull(LauncherShortcuts.route("SAVED", Routes.HOME))
        assertTrue(LauncherShortcut.isOne(action("COMPOSE")), "still a shortcut's, so no other reader takes it")
        assertEquals(LauncherShortcut.SUBSCRIPTIONS, LauncherShortcut.of(action("SUBSCRIPTIONS")))
    }

    @Test
    fun aScreenToFinishFirstIsNotLeft() {
        listOf(Routes.INTRO, Routes.SIGN_IN, Routes.IMPORT).forEach { over ->
            assertNull(LauncherShortcuts.route(action("SEARCH"), over), over)
            assertNull(LauncherShortcuts.route(action("SAVED"), over), over)
        }
        assertNull(LauncherShortcuts.route(action("SAVED"), null), "before any screen is shown")
    }

    @Test
    fun theScreenShownIsNotOpenedAgain() {
        assertNull(LauncherShortcuts.route(action("SEARCH"), Routes.SEARCH))
        assertNull(LauncherShortcuts.route(action("SAVED"), Routes.SAVED))
        assertNull(LauncherShortcuts.route(action("SUBSCRIPTIONS"), Routes.SUBSCRIPTIONS))
    }

    /** The app's Android resources, from wherever the tests run (the module, or the repository's root). */
    private val res: File? = listOf("../app/src/main/res", "app/src/main/res", "native/app/src/main/res").map(::File).firstOrNull { it.isDirectory }

    @Test
    fun everyShortcutInTheFileOpensAScreen() {
        val file = res?.resolve("xml/shortcuts.xml") ?: return
        val actions = Regex("android:action=\"([^\"]+)\"").findAll(file.readText()).map { it.groupValues[1] }.toList()
        assertEquals(3, actions.size)
        actions.forEach { assertTrue(LauncherShortcuts.route(it, Routes.HOME) != null, it) }
    }

    @Test
    fun everyLanguageNamesWhatAndroidShows() {
        val root = res ?: return
        fun names(folder: File) = Regex("<string name=\"([a-z_]+)\"").findAll(folder.resolve("strings.xml").readText()).map { it.groupValues[1] }.toSet()
        val english = names(root.resolve("values"))
        val languages = root.listFiles { file -> file.name.startsWith("values-") && file.resolve("strings.xml").isFile }.orEmpty()
        assertEquals(28, languages.size, "the screens' languages, Indonesian as Android names it")
        languages.forEach { folder ->
            assertEquals(english, names(folder), folder.name)
            val unescaped = Regex("(?<!\\\\)'").containsMatchIn(folder.resolve("strings.xml").readText().substringAfter("<resources>"))
            assertFalse(unescaped, "${folder.name}: an apostrophe Android would drop")
        }
    }
}
