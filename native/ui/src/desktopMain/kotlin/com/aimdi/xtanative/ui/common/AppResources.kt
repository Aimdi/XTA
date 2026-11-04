package com.aimdi.xtanative.ui.common

import java.io.File

/**
 * The native parts the desktop app carries beside its jars, in
 * lib/app/resources (Compose's app resources): the voice engine and the
 * video engine's shim.
 */
internal object AppResources {
    /**
     * The folder [name] among them: [override] when given (an environment
     * variable naming one for a build run from Gradle), else in Compose's app
     * resources as the app was started with them, else beside the launcher
     * jpackage made (bin/xta-native); null when the app was started neither way.
     */
    fun folder(
        name: String,
        override: String?,
        resources: String? = System.getProperty("compose.application.resources.dir"),
        launcher: String? = System.getProperty("jpackage.app-path"),
    ): File? {
        override?.ifEmpty { null }?.let { return File(it) }
        resources?.let { return File(it, name) }
        return launcher?.let { File(File(it).parentFile?.parentFile, "lib/app/resources/$name") }
    }
}
