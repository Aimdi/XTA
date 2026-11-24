package com.aimdi.xtanative.ui.chrome

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The desktop app's log: what it writes to its terminal (stdout and
 * stderr, a video that failed, an uncaught error) is also kept in
 * `$XDG_STATE_HOME/xta-native/logs` ([folder]), so it is still there when
 * the app was started from the menu, where nothing reads the terminal.
 * Lines are [LogRedaction]-cleaned of credentials first; the files are
 * kept small by [RotatingLog].
 */
object AppLog {
    /** The log's folder: under `$XDG_STATE_HOME` when that is an absolute path (the spec says to ignore a relative one), else `~/.local/state`. */
    fun folder(env: (String) -> String? = System::getenv, home: String = System.getProperty("user.home").orEmpty()): File {
        val state = env("XDG_STATE_HOME")?.takeIf { it.startsWith("/") }?.let(::File) ?: File(home, ".local/state")
        return File(File(state, "xta-native"), "logs")
    }

    @Volatile
    private var installed: RotatingLog? = null

    /**
     * Tees stdout and stderr into the log in [folder]; once a process. The
     * terminal still gets everything as it did; the log only fails quietly.
     */
    @Synchronized
    fun install(folder: File = folder()) {
        if (installed != null) return
        val log = runCatching { RotatingLog(folder) }.getOrNull() ?: return
        installed = log
        System.setOut(PrintStream(LogTee(System.out, "out", log::write), true, Charsets.UTF_8))
        System.setErr(PrintStream(LogTee(System.err, "err", log::write), true, Charsets.UTF_8))
    }
}

/**
 * Takes credentials out of a log line before it is written: an
 * `Authorization` or `Cookie` header's value, a bearer token, and the
 * session's cookies and tokens by name (X's `ct0`, `auth_token`, …), in a
 * header, a query, a cookie string or JSON.
 */
object LogRedaction {
    private const val HIDDEN = "[redacted]"

    /** Cookie and token names whose values are a session. */
    private const val SECRET_NAMES =
        "ct0|auth_token|auth_multi|kdt|twid|att|sessionid|sid|csrftoken|csrf_token|x-csrf-token|x-guest-token|guest_token|gt|" +
            "access_token|refresh_token|id_token|oauth_token|oauth_token_secret|api_key|apikey|client_secret|password|passwd|token"

    private val rules: List<Pair<Regex, String>> = listOf(
        // A whole header's value: everything to the end of the line.
        Regex("""(?i)\b((?:proxy-)?authorization|set-cookie|cookie)("?\s*[:=]\s*"?)[^\r\n"]*""") to "$1$2$HIDDEN",
        Regex("""(?i)\b(bearer)\s+[A-Za-z0-9._~+/%=-]{8,}""") to "$1 $HIDDEN",
        // name=value; name: value; "name":"value".
        Regex("""(?i)(?<![A-Za-z0-9_-])($SECRET_NAMES)("?\s*[:=]\s*"?)[^\s;&",}]+""") to "$1$2$HIDDEN",
    )

    fun redact(line: String): String = rules.fold(line) { text, (pattern, replacement) -> pattern.replace(text, replacement) }
}

/**
 * A log file of at most [maxBytes], and [files] in all: when the next line
 * would go past [maxBytes], `xta-native.log` becomes `xta-native.log.1`, that
 * one `.2`, and the oldest goes. The folder is the reader's alone (0700), as
 * are the files (0600).
 */
class RotatingLog(
    private val folder: File,
    private val maxBytes: Long = 1_000_000,
    private val files: Int = 3,
    private val name: String = "xta-native.log",
) {
    private var out: FileOutputStream
    private var size: Long

    init {
        folder.mkdirs()
        listOfNotNull(folder.parentFile?.takeIf { it.name == "xta-native" }, folder).forEach(::onlyMine)
        val current = File(folder, name)
        size = current.length()
        out = open(current)
    }

    /** One [line] (a newline added), redacted, stamped with the time and its [stream]. */
    @Synchronized
    fun write(stream: String, line: String) {
        val bytes = "${LocalDateTime.now().format(STAMP)} [$stream] ${LogRedaction.redact(line)}\n".toByteArray()
        runCatching {
            if (size > 0 && size + bytes.size > maxBytes) rotate()
            out.write(bytes)
            out.flush()
            size += bytes.size
        }
    }

    private fun rotate() {
        runCatching { out.close() }
        File(folder, "$name.${files - 1}").delete()
        for (index in files - 2 downTo 1) File(folder, "$name.$index").takeIf { it.exists() }?.renameTo(File(folder, "$name.${index + 1}"))
        File(folder, name).renameTo(File(folder, "$name.1"))
        size = 0
        out = open(File(folder, name))
    }

    private fun open(file: File): FileOutputStream {
        if (!file.exists()) {
            file.createNewFile()
            runCatching { Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------")) }
        }
        return FileOutputStream(file, true)
    }

    private fun onlyMine(dir: File) {
        runCatching { Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString("rwx------")) }
    }

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    }
}

/**
 * A stream that writes through to [through] (the terminal) and hands each
 * whole line, as UTF-8 text, to [line] with its [stream]'s name.
 */
class LogTee(private val through: OutputStream, private val stream: String, private val line: (String, String) -> Unit) : OutputStream() {
    private val pending = ByteArrayOutputStream()

    @Synchronized
    override fun write(b: Int) {
        through.write(b)
        if (b == '\n'.code) emit() else pending.write(b)
        if (pending.size() > MAX_LINE) emit()
    }

    @Synchronized
    override fun write(b: ByteArray, off: Int, len: Int) {
        through.write(b, off, len)
        var start = off
        for (i in off until off + len) {
            if (b[i] == '\n'.code.toByte()) {
                pending.write(b, start, i - start)
                emit()
                start = i + 1
            }
        }
        pending.write(b, start, off + len - start)
        if (pending.size() > MAX_LINE) emit()
    }

    override fun flush() = through.flush()

    private fun emit() {
        val text = pending.toString(Charsets.UTF_8).trimEnd('\r')
        pending.reset()
        runCatching { line(stream, text) }
    }

    private companion object {
        /** A line longer than this is cut into pieces, so a runaway print cannot hold the memory. */
        const val MAX_LINE = 16_384
    }
}

/** Settings › App info's "Open logs folder": the log's folder (made if need be) in the desktop's file manager. */
object LogsFolder {
    /** Hands [folder] to [opener] (xdg-open) as one argument, no shell; false when it could not be started. */
    fun open(folder: File = AppLog.folder(), opener: String = "xdg-open"): Boolean = runCatching {
        folder.mkdirs()
        listOfNotNull(folder.parentFile?.takeIf { it.name == "xta-native" }, folder).forEach {
            Files.setPosixFilePermissions(it.toPath(), PosixFilePermissions.fromString("rwx------"))
        }
        ProcessBuilder(opener, folder.path).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        true
    }.getOrDefault(false)
}
