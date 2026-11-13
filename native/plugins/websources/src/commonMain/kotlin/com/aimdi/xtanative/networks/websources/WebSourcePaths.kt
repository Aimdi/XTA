package com.aimdi.xtanative.networks.websources

import com.aimdi.xtanative.networks.JsSpace
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One step of a JSON path: a key, an index, or `[*]` for every element of a list. */
sealed interface JsonPathStep {
    data class Key(val key: String) : JsonPathStep
    data class Index(val index: Int) : JsonPathStep
    data object Every : JsonPathStep
}

/**
 * The JSON half of a web source's rules, and the templates that join
 * several values. Nothing here throws on a page that changed shape: a path
 * that leads nowhere gives no value.
 */
object WebSourcePaths {
    private val placeholder = Regex("""\{([^{}]+)\}""")
    private const val UNRESERVED = "-_.!~*'()"

    /**
     * Whether [path] is a JSON path: keys joined by `.`, `[n]`, `[*]`, and a
     * leading `$` for the page. Read in one pass, so a rule written to fail
     * slowly cannot hang the app while its file is read.
     */
    fun isJsonPath(path: String): Boolean {
        if (path.isEmpty()) return false
        var i = if (path[0] == '$') 1 else 0
        i = skipKey(path, i)
        while (i < path.length) {
            i = when (path[i]) {
                '.' -> skipKey(path, i + 1).takeIf { it > i + 1 } ?: return false
                '[' -> bracketEnd(path, i)?.let { skipKey(path, it) } ?: return false
                else -> return false
            }
        }
        return true
    }

    /** The steps of [path]; for a path that is not one, the steps that can still be read from it. */
    fun jsonPathSteps(path: String): List<JsonPathStep> {
        val steps = ArrayList<JsonPathStep>()
        var i = if (path.startsWith('$')) 1 else 0
        while (i < path.length) {
            val keyEnd = skipKey(path, i)
            val bracket = if (path[i] == '[') bracketEnd(path, i) else null
            i = when {
                keyEnd > i -> keyEnd.also { steps += JsonPathStep.Key(path.substring(i, keyEnd)) }
                bracket != null -> bracket.also { steps += bracketStep(path.substring(i + 1, bracket - 1)) }
                else -> i + 1
            }
        }
        return steps
    }

    /**
     * Every value at [path] in [root], JSON nulls left out: one value for a
     * plain path, one per element for each `[*]`.
     */
    fun jsonValuesAt(root: JsonElement?, path: String): List<JsonElement> =
        jsonPathSteps(path).fold(listOf(root ?: JsonNull)) { values, step -> values.flatMap { stepInto(it, step) } }

    /** A value as text: strings as they are, numbers and booleans as written; anything else (a map, a list, null) none. */
    fun jsonText(value: JsonElement?): String? = when (value) {
        null, JsonNull -> null
        is JsonPrimitive -> value.content
        else -> null
    }

    /** Whether [rule] is a template: text with `{…}` placeholders. */
    fun isTemplate(rule: String): Boolean = placeholder.containsMatchIn(rule)

    /** The placeholders of [template], in order. */
    fun templatePlaceholders(template: String): List<String> = placeholder.findAll(template).map { it.groupValues[1] }.toList()

    /**
     * [template] with each placeholder replaced by [valueOf] it; null when any
     * placeholder has no value (null, not empty), so a link is never built
     * from half its parts. With [encode], values are escaped for an address,
     * except when one placeholder is the whole template (`{link}`): that
     * value is the address.
     */
    fun fillTemplate(template: String, encode: Boolean = false, valueOf: (String) -> String?): String? {
        val whole = placeholder.matchEntire(template) != null
        var missing = false
        val filled = placeholder.replace(template) { match ->
            val value = valueOf(match.groupValues[1])
            if (value == null) missing = true
            if (encode && !whole) encodeComponent(value.orEmpty()) else value.orEmpty()
        }
        return if (missing) null else filled
    }

    /** [value] escaped as Dart's Uri.encodeComponent escapes it: UTF-8, all but letters, digits and `-_.!~*'()`. */
    fun encodeComponent(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val char = byte.toInt().toChar()
            if (byte >= 0 && (char.isLetterOrDigit() || char in UNRESERVED)) {
                append(char)
            } else {
                append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
    }

    private fun stepInto(value: JsonElement, step: JsonPathStep): List<JsonElement> = when (step) {
        JsonPathStep.Every -> (value as? JsonArray)?.filter { it !is JsonNull }.orEmpty()
        is JsonPathStep.Key -> listOfNotNull((value as? JsonObject)?.get(step.key)?.takeIf { it !is JsonNull })
        is JsonPathStep.Index -> listOfNotNull((value as? JsonArray)?.getOrNull(step.index)?.takeIf { it !is JsonNull })
    }

    /** Past a run of key characters from [start]: anything but `.[]{}@` and spaces. */
    private fun skipKey(path: String, start: Int): Int {
        var i = start
        while (i < path.length && isKeyChar(path[i])) i++
        return i
    }

    /** Past `[digits]` or `[*]` opening at [start]; null when there is none. */
    private fun bracketEnd(path: String, start: Int): Int? {
        var i = start + 1
        if (i < path.length && path[i] == '*') {
            i++
        } else {
            while (i < path.length && path[i] in '0'..'9') i++
            if (i == start + 1) return null
        }
        return if (i < path.length && path[i] == ']') i + 1 else null
    }

    /** An index too long to be one matches nothing. */
    private fun bracketStep(inside: String): JsonPathStep =
        if (inside == "*") JsonPathStep.Every else JsonPathStep.Index(inside.toIntOrNull() ?: Int.MAX_VALUE)

    private fun isKeyChar(c: Char): Boolean = c !in ".[]{}@" && !JsSpace.isSpace(c)
}
