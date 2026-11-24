package com.aimdi.xtanative.ui.common

import java.io.File

/** The reader's XDG user folders (Downloads, Pictures, Videos), in their own language and place. */
object XdgFolders {
    /** The XDG download folder ([userDir] of `DOWNLOAD`). */
    fun downloads(home: File = File(System.getProperty("user.home")), env: (String) -> String? = System::getenv): File =
        userDir("DOWNLOAD", "Downloads", home, env)

    /**
     * The XDG user folder [name] (`DOWNLOAD`, `PICTURES`, `VIDEOS`):
     * `$XDG_<name>_DIR`, else what `user-dirs.dirs` in the XDG config folder
     * says, else [fallback] in the home folder.
     */
    fun userDir(name: String, fallback: String, home: File, env: (String) -> String?): File {
        env("XDG_${name}_DIR")?.takeIf { it.isNotBlank() }?.let { expanded(it, home) }?.let { return it }
        val config = env("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".config")
        val dirs = runCatching { File(config, "user-dirs.dirs").takeIf { it.isFile }?.readText() }.getOrNull()
        return userDirsEntry(dirs, name, home) ?: File(home, fallback)
    }

    /** `XDG_<name>_DIR` in the text of `user-dirs.dirs`; null when unset, or set to the home folder, which XDG reads as none. */
    fun userDirsEntry(text: String?, name: String, home: File): File? {
        val key = "XDG_${name}_DIR="
        val line = text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.startsWith(key) } ?: return null
        val value = line.removePrefix(key).trim().removeSurrounding("\"")
        return expanded(value, home)?.takeIf { it.absoluteFile != home.absoluteFile }
    }

    private fun expanded(value: String, home: File): File? {
        val path = value.replace("\$HOME", home.path).let { if (it == "~" || it.startsWith("~/")) home.path + it.drop(1) else it }
        return File(path).takeIf { path.isNotEmpty() && it.isAbsolute }
    }
}
