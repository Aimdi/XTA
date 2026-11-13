package com.aimdi.xtanative.core.x.signing

import com.aimdi.xtanative.core.json.J
import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A derived signer and when it was derived. */
class StoredSigner(val signer: TransactionSigner, val derivedAt: Long)

/**
 * Where derived signers outlive the process, so a cold start signs its first
 * request at once instead of fetching x.com's pages again. Keyed by a digest
 * of the session, never the session itself; what is kept comes from x.com's
 * public pages.
 */
interface SignerStore {
    fun read(): Map<String, StoredSigner>
    fun write(signers: Map<String, StoredSigner>)
}

/** A [SignerStore] in one small JSON file. */
class FileSignerStore(private val file: File) : SignerStore {
    override fun read(): Map<String, StoredSigner> {
        val root = runCatching { if (file.exists()) J.parse(file.readText()) else null }.getOrNull() ?: return emptyMap()
        if (root["v"].int != VERSION) return emptyMap()
        val signers = root["signers"]
        return signers.keys.mapNotNull { key ->
            val entry = signers[key]
            val bytes = entry["keyBytes"].list.mapNotNull { it.int }
            val animation = entry["animationKey"].string ?: return@mapNotNull null
            val at = entry["derivedAt"].long ?: return@mapNotNull null
            if (bytes.isEmpty()) null else key to StoredSigner(TransactionSigner(bytes.toIntArray(), animation), at)
        }.toMap()
    }

    override fun write(signers: Map<String, StoredSigner>) {
        val text = buildJsonObject {
            put("v", VERSION)
            put(
                "signers",
                buildJsonObject {
                    signers.forEach { (key, stored) ->
                        put(
                            key,
                            buildJsonObject {
                                put("keyBytes", JsonArray(stored.signer.keyBytes.map { JsonPrimitive(it) }))
                                put("animationKey", stored.signer.animationKey)
                                put("derivedAt", stored.derivedAt)
                            },
                        )
                    }
                },
            )
        }.toString()
        runCatching {
            val partial = File(file.parentFile, file.name + ".part")
            partial.writeText(text)
            if (!partial.renameTo(file)) {
                file.delete()
                partial.renameTo(file)
            }
        }
    }

    private companion object {
        const val VERSION = 1
    }
}
