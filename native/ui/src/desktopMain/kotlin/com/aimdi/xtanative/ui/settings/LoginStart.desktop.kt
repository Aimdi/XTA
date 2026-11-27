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

    // An entry the desktop's own settings switched off (Hidden, or GNOME's own key) counts as off.
    override fun isOn(): Boolean = file.isFile && runCatching { file.readLines().map { it.trim().lowercase() }.none { it in OFF_LINES } }.getOrDefault(false)

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
         * as the desktop entry spec asks: `xta-native-launch` beside it where
         * the tarball has it (as install.sh's menu entry), so a start at login
         * gets its class-data archive too. Null with no launcher (a run from Gradle).
         */
        fun execFor(launcher: String?, path: String?, home: String?): String? {
            val app = launcher?.takeIf { it.isNotBlank() } ?: return null
            val onPath = path.orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() }.any { File(it, "xta-native").let { bin -> bin.isFile && bin.canExecute() } }
            val system = home.isNullOrBlank() || !File(app).absolutePath.startsWith(File(home).absolutePath + File.separator)
            if (onPath && system) return "xta-native"
            val withArchive = File(File(app).parentFile, LAUNCH_SCRIPT).takeIf { it.isFile && it.canExecute() }
            return quoted(withArchive?.path ?: app)
        }

        /** The tarball's script that starts the app with its class-data archive (packaging/linux/xta-native-launch). */
        private const val LAUNCH_SCRIPT = "xta-native-launch"

        private val OFF_LINES = setOf("hidden=true", "x-gnome-autostart-enabled=false")

        /** The characters that take a backslash inside a quoted Exec argument. */
        private const val QUOTED_ESCAPES = "\"`$\\"

        /**
         * [arg] as one quoted argument of an Exec value, by the Desktop Entry
         * spec's two levels: inside the quotes `"`, `` ` ``, `$` and `\` take a
         * backslash; then the value's own string escaping doubles every
         * backslash (a literal `\` ends up as four), and `%` is `%%`, so it
         * reads as no field code.
         */
        fun quoted(arg: String): String {
            val inQuotes = buildString { arg.forEach { c -> if (c in QUOTED_ESCAPES) append('\\'); append(c) } }
            return "\"$inQuotes\"".replace("\\", "\\\\").replace("%", "%%")
        }

        fun forThisApp(): AutostartEntry? {
            val exec = execFor(System.getProperty("jpackage.app-path"), System.getenv("PATH"), System.getProperty("user.home")) ?: return null
            // The XDG spec has a relative $XDG_CONFIG_HOME ignored.
            val config = System.getenv("XDG_CONFIG_HOME")?.let(::File)?.takeIf { it.isAbsolute } ?: File(System.getProperty("user.home"), ".config")
            return AutostartEntry(File(config, "autostart"), exec)
        }
    }
}
