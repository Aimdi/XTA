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

    /**
     * How deep a reader walks a tree: Meta nests a page's posts a few dozen
     * levels down, and a walk that went on would end the app, not the read.
     */
    const val MAX_DEPTH = 160

    /** [text] as JSON, or null when it is not JSON at all or nests deeper than [MAX_DEPTH]. */
    fun decode(text: String): JsonElement? = runCatching { Json.parseToJsonElement(text) }.getOrNull()?.takeIf { wellFormed(it, 0) }

    private fun wellFormed(element: JsonElement, depth: Int): Boolean = when (element) {
        is JsonObject -> depth < MAX_DEPTH && element.values.all { wellFormed(it, depth + 1) }
        is JsonArray -> depth < MAX_DEPTH && element.all { wellFormed(it, depth + 1) }
        is JsonNull -> true
        is JsonPrimitive -> element.isString || element.content == "true" || element.content == "false" || number.matches(element.content)
    }
}
