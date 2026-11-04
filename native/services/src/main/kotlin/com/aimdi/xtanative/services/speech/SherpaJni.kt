package com.aimdi.xtanative.services.speech

import java.io.File

/**
 * sherpa-onnx's C API through the app's JNI shim (services/src/main/c/xta_sherpa.c),
 * the binding that costs the APK least: a few kilobytes per processor, where
 * JNA's dispatch library and classes would add about a megabyte, and
 * sherpa-onnx's own Kotlin API a JNI library of several megabytes beside the
 * C API the engine archives carry. The shim links against nothing of the
 * engine: [open] loads it from the folder it is in, onnxruntime first.
 * Strings cross as NUL-terminated UTF-8, which the C API reads.
 */
object SherpaJni : SherpaEngine {
    @Volatile
    private var opened: File? = null

    /**
     * The engine in [folder], its [libraries] loaded in order (onnxruntime,
     * then sherpa-onnx's C API), after [loadShim] has loaded the shim; once
     * per folder. Throws, saying why, when either cannot load.
     */
    @Synchronized
    fun open(folder: File, libraries: List<String>, loadShim: () -> Unit): SherpaEngine {
        if (opened == folder) return this
        require(libraries.size == 2) { "the engine is onnxruntime and sherpa-onnx: $libraries" }
        loadShim()
        val (onnxruntime, sherpa) = libraries.map { File(folder, it).path }
        nativeOpen(cString(onnxruntime), cString(sherpa))?.let { throw IllegalStateException(String(it, Charsets.UTF_8)) }
        opened = folder
        return this
    }

    override fun create(config: SherpaConfig): Long =
        nativeCreate(kindOf(config.kind), cString(config.model), cString(config.tokens), cString(config.dataDir), cString(config.voices.orEmpty()), config.threads)

    override fun speak(model: Long, text: String, speakerId: Int, speed: Float, out: File): Int =
        nativeSpeak(model, cString(text.replace('\u0000', ' ')), speakerId, speed, cString(out.path))

    override fun destroy(model: Long) = nativeDestroy(model)

    /** The model kinds as the shim numbers them. */
    fun kindOf(kind: VoiceModelKind): Int = when (kind) {
        VoiceModelKind.VITS -> 0
        VoiceModelKind.KOKORO -> 1
    }

    /** [value] as the C API reads a string: UTF-8, ended by a NUL. */
    fun cString(value: String): ByteArray = (value + '\u0000').toByteArray(Charsets.UTF_8)

    @JvmStatic
    private external fun nativeOpen(onnxruntime: ByteArray, sherpa: ByteArray): ByteArray?

    @JvmStatic
    private external fun nativeCreate(kind: Int, model: ByteArray, tokens: ByteArray, dataDir: ByteArray, voices: ByteArray, threads: Int): Long

    @JvmStatic
    private external fun nativeSpeak(model: Long, text: ByteArray, speakerId: Int, speed: Float, out: ByteArray): Int

    @JvmStatic
    private external fun nativeDestroy(model: Long)
}
