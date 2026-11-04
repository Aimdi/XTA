package com.aimdi.xtanative.core.x

import com.aimdi.xtanative.core.json.J
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * [XAccountHealth] kept in one small file, as XTA keeps each account's
 * refusals and flag in its accounts table: an account whose sign-in looks
 * broken still rests after a restart. An account goes by a digest of its id,
 * which is its session's `ct0`, so the file holds no session. Read once,
 * then kept in memory; written whenever an account's health changes.
 */
class FileAccountHealthStore(private val file: File) : XAccountHealthStore {
    private val kept: ConcurrentHashMap<String, XAccountHealth> by lazy { ConcurrentHashMap(load()) }

    override fun read(accountId: String): XAccountHealth = kept[digest(accountId)] ?: XAccountHealth()

    @Synchronized
    override fun write(accountId: String, health: XAccountHealth) {
        val key = digest(accountId)
        if ((kept[key] ?: XAccountHealth()) == health) return
        if (health.isClean) kept.remove(key) else kept[key] = health
        save()
    }

    private fun load(): Map<String, XAccountHealth> {
        val root = runCatching { if (file.isFile) J.parse(file.readText()) else null }.getOrNull() ?: return emptyMap()
        if (root["v"].int != VERSION) return emptyMap()
        val accounts = root["accounts"]
        return accounts.keys.associateWith { key ->
            val row = accounts[key]
            XAccountHealth(row["refusals"].int ?: 0, row["flaggedAt"].long)
        }.filterValues { !it.isClean }
    }

    private fun save() {
        val text = buildJsonObject {
            put("v", VERSION)
            put(
                "accounts",
                buildJsonObject {
                    kept.forEach { (key, health) ->
                        put(key, buildJsonObject {
                            put("refusals", health.consecutiveNotFound)
                            health.notFoundAt?.let { put("flaggedAt", it) }
                        })
                    }
                },
            )
        }.toString()
        runCatching {
            file.parentFile?.mkdirs()
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

        fun digest(accountId: String): String =
            MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
    }
}
