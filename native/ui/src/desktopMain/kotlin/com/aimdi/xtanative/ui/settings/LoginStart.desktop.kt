package com.aimdi.xtanative.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File

@Composable
internal actual fun rememberLoginStart(): LoginStart? = remember { AutostartEntry.forThisApp() }

/**
 * "Start at login" as the Linux desktop has it: an entry in
 * `$XDG_CONFIG_HOME/autostart` (the XDG autostart spec, which GNOME, KDE,
 * and uwsm under Hyprland run). The entry starts the app `--minimized`, so
 * with Close to tray on it waits in the tray; without, its window opens.
 * Whether it is on is read from the file each time, so deleting it by hand
 * counts.
 */
internal class AutostartEntry(private val folder: File, private val exec: String) : LoginStart {
    private val file = File(folder, FILE_NAME)

    override fun isOn(): Boolean = file.isFile && runCatching { file.readLines().none { it.trim().equals("Hidden=true", ignoreCase = true) } }.getOrDefault(false)

    override fun set(on: Boolean): Boolean = runCatching {
        if (on) {
            folder.mkdirs()
            val part = File(folder, "$FILE_NAME.part")
            part.writeText(text(exec))
            if (!part.renameTo(file)) {
                file.delete()
                check(part.renameTo(file))
            }
        } else {
            file.delete()
        }
        isOn() == on
    }.getOrDefault(false)

    companion object {
        const val FILE_NAME = "com.aimdi.xtanative.desktop"

        /** The entry's text, starting [exec] (a ready Exec value, quoted where needed). */
        fun text(exec: String): String = """
            [Desktop Entry]
            Type=Application
            Name=XTA Native
            Comment=Starts XTA Native at login (Settings › General › Start at login)
            Exec=$exec --minimized
            Icon=com.aimdi.xtanative
            Terminal=false
            X-GNOME-Autostart-enabled=true
        """.trimIndent() + "\n"

        /**
         * What the entry runs for [launcher]: the bare `xta-native` where the
         * package put one on [path] and the app runs from a system folder (it
         * keeps working across updates), else the launcher's own path, quoted
         * as the desktop entry spec asks. Null with no launcher (a run from Gradle).
         */
        fun execFor(launcher: String?, path: String?, home: String?): String? {
            val app = launcher?.takeIf { it.isNotBlank() } ?: return null
            val onPath = path.orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() }.any { File(it, "xta-native").let { bin -> bin.isFile && bin.canExecute() } }
            val system = home.isNullOrBlank() || !File(app).absolutePath.startsWith(File(home).absolutePath + File.separator)
            if (onPath && system) return "xta-native"
            return "\"" + app.replace("\\", "\\\\").replace("\"", "\\\"").replace("`", "\\`").replace("$", "\\$") + "\""
        }

        fun forThisApp(): AutostartEntry? {
            val exec = execFor(System.getProperty("jpackage.app-path"), System.getenv("PATH"), System.getProperty("user.home")) ?: return null
            val config = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(System.getProperty("user.home"), ".config")
            return AutostartEntry(File(config, "autostart"), exec)
        }
    }
}
