package com.aimdi.xtanative.pluginhost

import com.aimdi.xtanative.pluginhost.testplugin.HelloPlugin
import com.aimdi.xtanative.pluginhost.testplugin.NeedsArgument
import java.io.File
import java.net.URLClassLoader
import java.util.function.Supplier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PluginLoaderTest {
    private val jar = pluginJar("hello", "1.0.0", extra = listOf(classBytes(NeedsArgument::class.java)))
    private val file = File(tempDir("loader"), "xta-plugin-hello-1.0.0.jar").apply { writeBytes(jar) }
    private val installed = InstalledPlugin("hello", "1.0.0", 1, HELLO, file.name, sha256(jar), jar.size.toLong(), 0, PluginCategory.OTHER, LocalizedText.EMPTY, LocalizedText.EMPTY)
    private val loader = PluginLoader(JarClassLoaderFactory, testPluginsHidden, PluginApi(1))

    @Test
    fun theEntryClassComesFromThePluginsFileAndSeesTheAppsClasses() {
        val loaded = assertIs<PluginLoad.Loaded>(loader.load(installed, file))
        assertEquals("hello from a plugin", (loaded.plugin as Supplier<*>).get())
        assertIs<URLClassLoader>(loaded.classLoader)
        assertSame(loaded.classLoader, loaded.plugin.javaClass.classLoader)
        assertSame(testPluginsHidden, loaded.classLoader.parent)
        assertNotSame<Class<*>>(HelloPlugin::class.java, loaded.plugin.javaClass, "the plugin's own copy, not the app's")
    }

    @Test
    fun aPluginForAnApiThisAppDoesNotLoadIsRefusedWithWhy() {
        assertEquals(PluginLoad.Refused(PluginFailure.NeedsNewerApp(2)), loader.load(installed.copy(apiVersion = 2), file))
        assertEquals(PluginLoad.Refused(PluginFailure.NeedsNewerPlugin(1)), PluginLoader(JarClassLoaderFactory, testPluginsHidden, PluginApi(2)).load(installed, file))
    }

    @Test
    fun aFileChangedOrGoneSinceItWasInstalledIsNotLoaded() {
        val changed = File(file.parentFile, "changed.jar").apply { writeBytes(pluginJar("hello", "1.0.0", entry = "com.example.Other")) }
        assertEquals(PluginLoad.Refused(PluginFailure.Damaged), loader.load(installed, changed))
        assertEquals(PluginLoad.Refused(PluginFailure.Damaged), loader.load(installed, File(file.parentFile, "gone.jar")))
    }

    @Test
    fun aMissingEntryClassOrOneWithoutANoArgumentConstructorFailsToLoad() {
        val missing = assertIs<PluginLoad.Refused>(loader.load(installed.copy(entryClass = "com.example.Missing"), file))
        assertTrue((missing.reason as PluginFailure.LoadFailed).detail.startsWith("ClassNotFoundException"))
        val noConstructor = assertIs<PluginLoad.Refused>(loader.load(installed.copy(entryClass = NeedsArgument::class.java.name), file))
        assertTrue((noConstructor.reason as PluginFailure.LoadFailed).detail.startsWith("NoSuchMethodException"))
    }
}
