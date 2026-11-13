package com.aimdi.xtanative.store.follows

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The reader's word on one account a group's Discover suggested: more like
 * it (1), less (-1) or hidden (0), with the words of what it was judged by.
 */
data class Judgement(val action: Int, val terms: List<String>) {
    companion object {
        const val MORE = 1
        const val LESS = -1
        const val HIDE = 0
    }
}

/**
 * What the reader said of a group's suggestions (XTA's `discovery:<group>`
 * store), by the account's key, one file per group under `discovery/`:
 * kept until they reset it.
 */
class DiscoveryFeedbackStore(private val dir: StoreDir) {
    suspend fun of(groupId: String): Map<String, Judgement> {
        val text = runCatching { dir.file(fileName(groupId)).read() }.getOrNull() ?: return emptyMap()
        return decode(J.parse(text))
    }

    suspend fun set(groupId: String, feedback: Map<String, Judgement>) {
        runCatching { dir.file(fileName(groupId)).write(encode(feedback).toString()) }
    }

    private companion object {
        const val VERSION = 1

        fun fileName(groupId: String): String = "discovery/" + groupId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64) + ".json"

        fun encode(feedback: Map<String, Judgement>): JsonElement = buildJsonObject {
            put("v", VERSION)
            put(
                "accounts",
                JsonObject(
                    feedback.mapValues { (_, judgement) ->
                        buildJsonObject {
                            put("action", judgement.action)
                            put("terms", JsonArray(judgement.terms.map(::JsonPrimitive)))
                        }
                    },
                ),
            )
        }

        fun decode(root: J): Map<String, Judgement> {
            if (root["v"].int != VERSION) return emptyMap()
            val accounts = root["accounts"]
            return accounts.keys.mapNotNull { key ->
                val entry = accounts[key]
                entry["action"].int?.let { action -> key to Judgement(action, entry["terms"].list.mapNotNull { it.string }) }
            }.toMap()
        }
    }
}
