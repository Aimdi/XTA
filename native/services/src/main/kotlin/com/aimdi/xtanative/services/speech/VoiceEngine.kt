package com.aimdi.xtanative.services.speech

import java.io.File

/** One library of the voice engine, pinned by size and SHA-256. */
data class VoiceEngineLibrary(val name: String, val bytes: Long, val sha256: String)

/**
 * The engine that runs downloaded voices (onnxruntime and sherpa-onnx's C
 * API) for one processor (voice_engine.dart). On Android it is not in the
 * APK: it downloads with the first voice from XTA's GitHub release, which
 * publishes one archive per processor beside the APKs. Every library is
 * pinned here, so a file that does not match is refused whatever served it.
 * The Linux build carries the engine in its bundle.
 *
 * [libraries] are in the order they load: onnxruntime first, since Android's
 * linker looks for sherpa-onnx's dependency among the libraries already
 * loaded before the app's own folder, where a downloaded engine is not.
 */
data class VoiceEnginePackage(
    /** Android's name for the processor, as an APK's `lib/` folder names it. */
    val abi: String,
    /** About how large the download is, shown before it starts. */
    val archiveBytes: Long,
    val libraries: List<VoiceEngineLibrary>,
) {
    val archiveName: String get() = "xta-voice-engine-sherpa-onnx-${VoiceEngines.VERSION}-$abi.tar.xz"

    /** What it takes on the device once unpacked. */
    val installedBytes: Long get() = libraries.sumOf { it.bytes }
}

object VoiceEngines {
    /** The sherpa-onnx release the engine is. */
    const val VERSION = "1.13.8"

    /** The licences of what the engine holds: sherpa-onnx, onnxruntime and the espeak-ng it links in. */
    const val LICENCES = "Apache-2.0, MIT, GPL-3.0"

    /** Where the engine archives are published. */
    const val REPOSITORY = "Aimdi/XTA"

    private fun libraries(onnx: Pair<Long, String>, sherpa: Pair<Long, String>) = listOf(
        VoiceEngineLibrary("libonnxruntime.so", onnx.first, onnx.second),
        VoiceEngineLibrary("libsherpa-onnx-c-api.so", sherpa.first, sherpa.second),
    )

    /** Hashed on 2026-11-01 from pub.dev's `sherpa_onnx_android_*` 1.13.8 packages, which pub.dev never changes. */
    val PACKAGES: List<VoiceEnginePackage> = listOf(
        VoiceEnginePackage(
            "arm64-v8a",
            6_023_748,
            libraries(
                22_249_560L to "33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1",
                4_499_112L to "42308b0dd41ac79639330c58ac69dec9c3a6bc94c8b6ae96b0311963bc13f0b0",
            ),
        ),
        VoiceEnginePackage(
            "armeabi-v7a",
            6_175_724,
            libraries(
                15_359_592L to "7a8f17a707e52e0ac3a026be319272876d9eae61f4bb19585292772d785f7744",
                3_202_776L to "66b2e4caa8abc40cd8b33d4856a5e494072b97f4dca4b69d81fdcc860feab03e",
            ),
        ),
        VoiceEnginePackage(
            "x86_64",
            7_315_652,
            libraries(
                25_581_120L to "087e84297cad64acb823c3d901d6e0cc934e91add7cdb20da802cd1c0bea5c3e",
                5_002_888L to "f3204723864da2d2ed43898e08baff0975472cc1d7412dcde77c5dc0b051d336",
            ),
        ),
    )

    /**
     * The Linux build's engine, which its tarball and Arch package carry as
     * XTA's Linux bundle does: pub.dev's `sherpa_onnx_linux` 1.13.8
     * (`linux/x64`), hashed on 2026-11-04. tool/voice_engine.py refuses to
     * package libraries that differ; it is not downloaded, so it has no archive.
     */
    val LINUX_X64: VoiceEnginePackage = VoiceEnginePackage(
        "linux-x64",
        0,
        libraries(
            27_026_609L to "4b3607aebd1784b26b6f9b20e4bd974c7ab8287043e4d095cb7d2cb40b5e566e",
            5_124_768L to "892443aed68181c59fc2b21c8ece04da7406bb9960f4fb65ed046baf21ae3755",
        ),
    )

    /** The app's JNI shim the engine is reached through (services/src/main/c), as `System.loadLibrary` names it. */
    const val SHIM = "xta_sherpa"

    /** The engine for a process on [abi] (Android's `Build.SUPPORTED_ABIS[0]`), or null when none is published for it. */
    fun forAbi(abi: String): VoiceEnginePackage? = PACKAGES.firstOrNull { it.abi == abi }

    /**
     * Where [engine] downloads from: the release [releaseTag] names, then
     * the latest one, for a local build (which has no release) or a release
     * that does not answer.
     */
    fun urls(engine: VoiceEnginePackage, releaseTag: String): List<String> = listOfNotNull(
        releaseTag.ifEmpty { null }?.let { "https://github.com/$REPOSITORY/releases/download/$it/${engine.archiveName}" },
        "https://github.com/$REPOSITORY/releases/latest/download/${engine.archiveName}",
    )

    /** The folder [engine] is installed in under [root]. */
    fun directory(root: File, engine: VoiceEnginePackage): File = File(File(root, "sherpa-onnx-$VERSION"), engine.abi)

    /** Whether [folder] carries [engine] as the desktop's bundle does: each library at its pinned size, and the shim beside them. */
    fun carriedIn(folder: File, engine: VoiceEnginePackage = LINUX_X64): Boolean =
        File(folder, System.mapLibraryName(SHIM)).isFile && engine.libraries.all { File(folder, it.name).length() == it.bytes }
}
