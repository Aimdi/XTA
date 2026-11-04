package com.aimdi.xtanative.networks.threads

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * JSON as XTA's `jsonDecode` reads it. kotlinx's tree reader takes any bare
 * word for a literal, so `<html>Error</html>` would pass for JSON; Dart
 * refuses it, and an error page answered with 200 is no answer.
 */
internal object ThreadsJson {
    private val number = Regex("""-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?""")

    /** [text] as JSON, or null when it is not JSON at all. */
    fun decode(text: String): JsonElement? = runCatching { Json.parseToJsonElement(text) }.getOrNull()?.takeIf(::wellFormed)

    private fun wellFormed(element: JsonElement): Boolean = when (element) {
        is JsonObject -> element.values.all(::wellFormed)
        is JsonArray -> element.all(::wellFormed)
        is JsonNull -> true
        is JsonPrimitive -> element.isString || element.content == "true" || element.content == "false" || number.matches(element.content)
    }
}
