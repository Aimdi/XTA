package com.aimdi.xta

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import com.ryanheise.audioservice.AudioServiceActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : AudioServiceActivity() {
    private val CHANNEL = "browser_resolver"
    private val REQUEST_PICK_DIRECTORY = 0xD17

    // Set while the document-tree picker is open, so its result can be handed
    // back to the Dart call that opened it.
    private var pendingDirectoryResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "scanMediaFile" -> scanMediaFile(call, result)
                    "getDefaultBrowser" -> getDefaultBrowser(result)
                    "listBrowsers" -> listBrowsers(result)
                    "pickDownloadDirectory" -> pickDownloadDirectory(result)
                    "hasDownloadDirectoryAccess" -> hasDownloadDirectoryAccess(call, result)
                    "saveToDownloadDirectory" -> saveToDownloadDirectory(call, result)
                    "enterPictureInPicture" -> enterPictureInPicture(call, result)
                    else -> result.notImplemented()
                }
            }
    }

    /**
     * Shrinks the activity into a floating window, shaped like the video.
     *
     * Android clamps the aspect ratio to roughly between 1:2.39 and 2.39:1 and
     * throws outside that, so an extreme clip is nudged inside the range rather
     * than taking the whole call down with it.
     */
    private fun enterPictureInPicture(call: MethodCall, result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            result.success(false)
            return
        }

        val ratio = (call.argument<Double>("aspectRatio") ?: (16.0 / 9.0))
            .coerceIn(0.42, 2.39)
        val numerator = (ratio * 1000).toInt()

        return try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(numerator, 1000))
                .build()
            result.success(enterPictureInPictureMode(params))
        } catch (e: IllegalStateException) {
            // Picture-in-picture can be switched off per app in system settings,
            // and some devices refuse it outright.
            result.success(false)
        } catch (e: IllegalArgumentException) {
            result.success(false)
        }
    }

    private fun scanMediaFile(call: MethodCall, result: MethodChannel.Result) {
        val path = call.argument<String>("path")
        if (path == null) {
            result.error("INVALID_ARGUMENT", "Path is null", null)
            return
        }
        MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ ->
            result.success(null)
        }
    }

    private fun getDefaultBrowser(result: MethodChannel.Result) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://")
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        result.success(resolveInfo?.activityInfo?.packageName)
    }

    /**
     * Every app that can open an https link, so the reader can name one.
     *
     * The system default is whatever Android was last told; someone who keeps
     * a hardened browser for links off a feed had no way to say so short of
     * changing that default for everything.
     *
     * MATCH_ALL plus CATEGORY_BROWSABLE is the full installed list. The
     * previous MATCH_DEFAULT_ONLY query could come back with only the current
     * default, which made the picker look empty.
     *
     * Visible because the manifest already declares the matching <queries>
     * entries; without those, package visibility would hide all of them.
     */
    private fun listBrowsers(result: MethodChannel.Result) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val flags = PackageManager.MATCH_ALL
        val browsers = packageManager
            .queryIntentActivities(intent, flags)
            .map {
                mapOf(
                    "package" to it.activityInfo.packageName,
                    "label" to it.loadLabel(packageManager).toString(),
                )
            }
            .filter { it["package"] != packageName }
            // One app can offer several matching activities; the reader is
            // choosing an app, not an activity.
            .distinctBy { it["package"] }
            .sortedBy { it["label"]?.lowercase() }

        result.success(browsers)
    }

    /**
     * Asks the user for a folder and keeps the grant across restarts.
     *
     * Since Android 11 an app cannot write to a shared-storage path it merely
     * knows the name of, which is why saving to a picked folder failed with
     * EPERM. A document tree plus a persisted permission is the supported way,
     * and it survives reboots, so auto-saving still works days later.
     */
    private fun pickDownloadDirectory(result: MethodChannel.Result) {
        if (pendingDirectoryResult != null) {
            result.error("ALREADY_PICKING", "A folder picker is already open", null)
            return
        }
        pendingDirectoryResult = result

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }

        try {
            startActivityForResult(intent, REQUEST_PICK_DIRECTORY)
        } catch (e: Exception) {
            pendingDirectoryResult = null
            result.error("NO_PICKER", "No folder picker available: ${e.message}", null)
        }
    }

    /** Whether the stored folder is still writable by this app. */
    private fun hasDownloadDirectoryAccess(call: MethodCall, result: MethodChannel.Result) {
        val treeUri = call.argument<String>("treeUri")
        if (treeUri.isNullOrEmpty()) {
            result.success(false)
            return
        }
        val granted = contentResolver.persistedUriPermissions.any {
            it.uri.toString() == treeUri && it.isWritePermission
        }
        result.success(granted)
    }

    private fun saveToDownloadDirectory(call: MethodCall, result: MethodChannel.Result) {
        val treeUri = call.argument<String>("treeUri")
        val fileName = call.argument<String>("fileName")
        val bytes = call.argument<ByteArray>("bytes")
        val mimeType = call.argument<String>("mimeType") ?: "application/octet-stream"

        if (treeUri.isNullOrEmpty() || fileName.isNullOrEmpty() || bytes == null) {
            result.error("INVALID_ARGUMENT", "treeUri, fileName and bytes are required", null)
            return
        }

        try {
            val tree = Uri.parse(treeUri)
            val documentId = DocumentsContract.getTreeDocumentId(tree)
            val directory = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)

            // Android renames rather than overwrites when the name is taken.
            val file = DocumentsContract.createDocument(contentResolver, directory, mimeType, fileName)
            if (file == null) {
                result.error("CREATE_FAILED", "Could not create $fileName in the chosen folder", null)
                return
            }

            val stream = contentResolver.openOutputStream(file)
            if (stream == null) {
                result.error("OPEN_FAILED", "Could not open $fileName for writing", null)
                return
            }
            stream.use { it.write(bytes) }

            result.success(file.toString())
        } catch (e: SecurityException) {
            // The grant is gone: the folder was deleted, or the user revoked it.
            result.error("PERMISSION_LOST", e.message, null)
        } catch (e: Exception) {
            result.error("SAVE_FAILED", e.message, null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_PICK_DIRECTORY) {
            val result = pendingDirectoryResult
            pendingDirectoryResult = null

            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    result?.success(uri.toString())
                } catch (e: Exception) {
                    result?.error("PERMISSION_FAILED", e.message, null)
                }
            } else {
                // Cancelled; null tells Dart to keep whatever was configured.
                result?.success(null)
            }
            return
        }

        super.onActivityResult(requestCode, resultCode, data)
    }
}
