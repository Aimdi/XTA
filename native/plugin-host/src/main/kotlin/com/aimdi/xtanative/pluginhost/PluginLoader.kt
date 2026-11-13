package com.aimdi.xtanative.pluginhost

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.URLClassLoader

/**
 * The platform's way of loading a plugin's classes from its file, with the
 * app's class loader as [parent], so the plugin and the app share
 * `:plugin-api` and the libraries the app provides. Android's, in `:app`, is
 * a DexClassLoader; the desktop's is [JarClassLoaderFactory].
 */
fun interface PluginClassLoaderFactory {
    fun create(file: File, parent: ClassLoader): ClassLoader
}

/** The desktop's [PluginClassLoaderFactory]: a URLClassLoader over the JAR, which asks the app's class loader first. */
object JarClassLoaderFactory : PluginClassLoaderFactory {
    override fun create(file: File, parent: ClassLoader): ClassLoader = URLClassLoader(arrayOf(file.toURI().toURL()), parent)
}

/** What loading an installed plugin gave. */
sealed interface PluginLoad {
    /** [plugin] is the entry class's instance, for the app to cast to XtaPlugin; [classLoader] holds its classes. */
    data class Loaded(val installed: InstalledPlugin, val plugin: Any, val classLoader: ClassLoader) : PluginLoad

    /** It was not loaded, for [reason]. */
    data class Refused(val reason: PluginFailure) : PluginLoad
}

/**
 * Makes an installed plugin's entry class with its no-argument constructor,
 * once its API is one this app loads and its file is still, byte for byte, the
 * one that was checked and installed.
 */
class PluginLoader(
    private val classLoaders: PluginClassLoaderFactory,
    private val parent: ClassLoader,
    private val api: PluginApi,
) {
    fun load(plugin: InstalledPlugin, file: File): PluginLoad {
        val refusal = api.refusal(plugin.apiVersion) ?: PluginFailure.Damaged.takeUnless { intact(plugin, file) }
        return refusal?.let(PluginLoad::Refused) ?: instantiate(plugin, file)
    }

    private fun intact(plugin: InstalledPlugin, file: File): Boolean = try {
        file.isFile && file.length() == plugin.size && sha256Of(file) == plugin.sha256
    } catch (e: IOException) {
        false
    }

    private fun instantiate(plugin: InstalledPlugin, file: File): PluginLoad = guarded(null) {
        val loader = classLoaders.create(file, parent)
        guarded(loader) { PluginLoad.Loaded(plugin, loader.loadClass(plugin.entryClass).getConstructor().newInstance(), loader) }
    }

    /** [make], or why it failed: a missing class or constructor, one that threw, or code that does not link. */
    private inline fun guarded(loader: ClassLoader?, make: () -> PluginLoad): PluginLoad = try {
        make()
    } catch (e: ReflectiveOperationException) {
        refused(loader, e)
    } catch (e: LinkageError) {
        refused(loader, e)
    } catch (e: RuntimeException) {
        refused(loader, e)
    }

    private fun refused(loader: ClassLoader?, cause: Throwable): PluginLoad {
        (loader as? Closeable)?.close()
        return PluginLoad.Refused(PluginFailure.LoadFailed("${cause.javaClass.simpleName}: ${cause.message}"))
    }
}
