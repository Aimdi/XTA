package com.aimdi.xtanative.pluginhost

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What fetching a plugin gave. */
internal sealed interface Fetched {
    /** [file] is [entry]'s file, checked against the catalogue and the app's key, and [manifest] is what it says of itself. */
    data class Ready(val entry: CatalogueEntry, val artifact: PluginArtifact, val file: File, val manifest: PluginManifest) : Fetched

    data class Failed(val reason: PluginFailure) : Fetched
}

/**
 * The steps of an install, apart from the store's state: fetch a plugin's
 * file, check it, keep it. A file that fails a check is deleted and never
 * loaded; one whose transfer broke off stays, to be resumed.
 */
internal class PluginInstaller(
    private val files: PluginFiles,
    private val downloader: PluginDownloader,
    private val verifier: PluginVerifier,
    private val platform: PluginPlatform,
    private val api: PluginApi,
) {
    /**
     * Downloads [entry]'s file and checks it: its size and SHA-256 against the
     * catalogue, then its signature, then its xta-plugin.json, which has to
     * name the same plugin and version and an API this app loads. [onChecking]
     * hears when the download is over and the checks begin.
     */
    suspend fun fetch(entry: CatalogueEntry, onProgress: (Long, Long) -> Unit, onChecking: () -> Unit): Fetched {
        val artifact = entry.artifact(platform) ?: return Fetched.Failed(PluginFailure.NotOffered)
        api.refusal(entry.apiVersion)?.let { return Fetched.Failed(it) }
        val part = withContext(Dispatchers.IO) { files.downloadOf(entry.id, entry.version, platform) }
        downloaded(artifact, part, onProgress, onChecking, mayRestart = true)?.let { return Fetched.Failed(it) }
        return withContext(Dispatchers.IO) { inspect(entry, artifact, part).also { if (it is Fetched.Failed) part.delete() } }
    }

    /** Moves a checked file to its place and says what is now installed, as of [at]. */
    suspend fun keep(ready: Fetched.Ready, at: Long): InstalledPlugin = withContext(Dispatchers.IO) {
        val entry = ready.entry
        val fileName = platform.fileName(entry.id, entry.version)
        files.keep(ready.file, entry.id, entry.version, fileName)
        InstalledPlugin(
            id = entry.id,
            version = entry.version,
            apiVersion = ready.manifest.apiVersion,
            entryClass = ready.manifest.entryClass,
            fileName = fileName,
            sha256 = ready.artifact.sha256,
            size = ready.artifact.size,
            installedAt = at,
            category = entry.category,
            name = entry.name,
            description = entry.description,
        )
    }

    /** Downloads [artifact] into [part] and checks its size and digest: null when it is the file the catalogue describes. */
    private suspend fun downloaded(
        artifact: PluginArtifact,
        part: File,
        onProgress: (Long, Long) -> Unit,
        onChecking: () -> Unit,
        mayRestart: Boolean,
    ): PluginFailure? {
        val download = downloader.download(artifact, part, onProgress)
        if (download is Downloaded.Failed) return download.reason
        onChecking()
        val problem = withContext(Dispatchers.IO) { checkArtifact(part, artifact)?.also { part.delete() } } ?: return null
        // What an earlier attempt left may have been the start of another file: fetched whole, once.
        return if (download == Downloaded.Resumed && mayRestart) downloaded(artifact, part, onProgress, onChecking, mayRestart = false) else problem
    }

    private fun inspect(entry: CatalogueEntry, artifact: PluginArtifact, file: File): Fetched {
        val verdict = try {
            verifier.verify(file)
        } catch (e: Exception) {
            Verdict.Refused("${e.javaClass.simpleName}: ${e.message}")
        }
        if (verdict is Verdict.Refused) return Fetched.Failed(PluginFailure.BadSignature(verdict.reason))
        val manifest = PluginManifest.read(file) ?: return Fetched.Failed(PluginFailure.BadManifest("no readable ${PluginManifest.NAME}"))
        val refusal = when {
            manifest.id != entry.id || manifest.version != entry.version ->
                PluginFailure.BadManifest("the file is ${manifest.id} ${manifest.version}, the catalogue's ${entry.id} ${entry.version}")
            else -> api.refusal(manifest.apiVersion)
        }
        return refusal?.let(Fetched::Failed) ?: Fetched.Ready(entry, artifact, file, manifest)
    }
}
