package com.aimdi.xtanative.core.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * A step into JSON that never throws, like XTA's `Json` extension type:
 * `J(body)["data"]["user"]["result"].string` is null when any step is
 * missing or of another kind. X's responses are reverse-engineered and move
 * without notice, so the deep path has to be the safe one.
 */
@JvmInline
value class J(val raw: JsonElement?) {
    operator fun get(key: String): J = J((raw as? JsonObject)?.get(key))

    operator fun get(index: Int): J = J((raw as? JsonArray)?.getOrNull(index))

    val exists: Boolean get() = raw != null && raw !is JsonNull

    val string: String? get() = (raw as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** A number, or a string holding one (X sends ids and view counts as strings). */
    val long: Long? get() = (raw as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toLongOrNull() }

    val int: Int? get() = long?.toInt()

    val double: Double? get() = (raw as? JsonPrimitive)?.doubleOrNull

    val boolean: Boolean? get() = (raw as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    val list: List<J> get() = (raw as? JsonArray)?.map { J(it) } ?: emptyList()

    val keys: Set<String> get() = (raw as? JsonObject)?.keys ?: emptySet()

    companion object {
        private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(text: String): J = J(runCatching { lenient.parseToJsonElement(text) }.getOrNull())
    }
}
