package com.aimdi.xtanative.store.backup

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** An object written with its keys in [fields]' order and every null spelt out, as Dart's `jsonEncode` writes a row. */
internal fun jsonRow(vararg fields: Pair<String, Any?>): JsonObject = JsonObject(fields.associate { (key, value) -> key to jsonOf(value) })

internal fun jsonOf(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.map(::jsonOf))
    else -> error("No JSON for ${value::class}")
}

/** XTA's tables keep a switch as 1 or 0. */
internal val Boolean.bit: Int get() = if (this) 1 else 0

/** A switch as XTA reads it back: 1 is on, any other number off, and [missing] when there is none. */
internal fun J.bit(missing: Boolean): Boolean = long?.let { it == 1L } ?: boolean ?: missing

/** A section's rows, or null when the file does not carry the section: restore nothing then, rather than an empty table. */
internal fun <T> J.rows(read: (J) -> T?): List<T>? =
    (raw as? JsonArray)?.mapNotNull { row -> (row as? JsonObject)?.let { read(J(it)) } }

internal fun <T> List<T>?.json(write: (T) -> JsonElement): JsonElement = this?.let { rows -> JsonArray(rows.map(write)) } ?: JsonNull
