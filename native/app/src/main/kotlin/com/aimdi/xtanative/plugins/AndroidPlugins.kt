package com.aimdi.xtanative.plugins

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.aimdi.xtanative.pluginhost.HostPlatform
import com.aimdi.xtanative.pluginhost.PluginClassLoaderFactory
import com.aimdi.xtanative.pluginhost.PluginPlatform
import com.aimdi.xtanative.pluginhost.PluginVerifier
import com.aimdi.xtanative.pluginhost.Verdict
import dalvik.system.DexClassLoader
import java.io.File
import java.security.MessageDigest

/** Downloadable plugins on Android (PLUGINS.md): code-only APKs signed with the app's own key, loaded with a DexClassLoader. */
fun androidPlugins(context: Context): HostPlatform =
    HostPlatform(PluginPlatform.ANDROID, ApkSignatureVerifier(context), DexClassLoaderFactory, checkNotNull(context.classLoader))

/**
 * Android's [PluginVerifier]: the plugin APK has to be signed by exactly the
 * certificates that signed the app itself, as PackageManager reads them from
 * the archive (it checks the APK's signature while it reads them). An APK it
 * cannot read, or one signed by anyone else, is refused.
 */
class ApkSignatureVerifier(context: Context) : PluginVerifier {
    private val packages = context.packageManager
    private val packageName = context.packageName

    override fun verify(file: File): Verdict {
        val plugin = packages.archiveInfo(file) ?: return Verdict.Refused("Android cannot read it as an APK")
        val theirs = signers(plugin)
        val ours = signers(packages.installedInfo(packageName))
        return when {
            theirs.isEmpty() -> Verdict.Refused("it carries no signature Android accepts")
            ours.isEmpty() -> Verdict.Refused("the app's own signature could not be read")
            theirs != ours -> Verdict.Refused("it is signed by ${theirs.joinToString()}, the app by ${ours.joinToString()}")
            else -> Verdict.Trusted
        }
    }

    private companion object {
        // Android 9 reads an archive's certificates only when GET_SIGNATURES is asked for too.
        @Suppress("DEPRECATION")
        val FLAGS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        } else {
            PackageManager.GET_SIGNATURES
        }

        @Suppress("DEPRECATION")
        fun PackageManager.archiveInfo(file: File): PackageInfo? = getPackageArchiveInfo(file.path, FLAGS)

        @Suppress("DEPRECATION")
        fun PackageManager.installedInfo(name: String): PackageInfo? = runCatching { getPackageInfo(name, FLAGS) }.getOrNull()

        /** The SHA-256 of each certificate that signs [info] now (the current signer, not its rotation history). */
        @Suppress("DEPRECATION")
        fun signers(info: PackageInfo?): Set<String> {
            info ?: return emptySet()
            val certificates: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signing = info.signingInfo ?: return emptySet()
                if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory?.takeLast(1)?.toTypedArray()
            } else {
                info.signatures
            }
            return certificates.orEmpty().map { sha256(it.toByteArray()) }.toSet()
        }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Android's [PluginClassLoaderFactory]: a DexClassLoader over the plugin's APK,
 * with the app's class loader as parent. The APK is read-only already, as
 * Android 14 requires of code it loads; since Android 8 the system keeps the
 * optimized code itself, beside the file.
 */
object DexClassLoaderFactory : PluginClassLoaderFactory {
    override fun create(file: File, parent: ClassLoader): ClassLoader = DexClassLoader(file.path, null, null, parent)
}
