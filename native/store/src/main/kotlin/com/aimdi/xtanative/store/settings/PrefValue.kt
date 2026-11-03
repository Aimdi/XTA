package com.aimdi.xtanative.store.settings

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What one of XTA's preferences holds: the five kinds Android's shared
 * preferences keep, which is where XTA stores its settings. Kept apart so a
 * value read from XTA's backup goes back out as it came in: `1.0` stays a
 * double and `1` a whole number, as Dart tells them apart.
 */
sealed interface PrefValue {
    data class Bool(val value: Boolean) : PrefValue

    /** Dart's `int`. */
    data class Whole(val value: Long) : PrefValue

    /** Dart's `double`. */
    data class Real(val value: Double) : PrefValue

    data class Text(val value: String) : PrefValue

    data class Texts(val value: List<String>) : PrefValue

    /** The value as XTA's backup writes it. */
    fun toJson(): JsonElement = when (this) {
        is Bool -> JsonPrimitive(value)
        is Whole -> JsonPrimitive(value)
        is Real -> JsonPrimitive(value)
        is Text -> JsonPrimitive(value)
        is Texts -> JsonArray(value.map(::JsonPrimitive))
    }

    companion object {
        /**
         * [element] as XTA's import stores it: a list keeps only its strings,
         * and null or an object is nothing preferences can hold.
         */
        fun fromJson(element: JsonElement?): PrefValue? = when (element) {
            null, JsonNull, is JsonObject -> null
            is JsonArray -> Texts(element.mapNotNull { item -> (item as? JsonPrimitive)?.takeIf { it.isString }?.content })
            is JsonPrimitive -> primitive(element)
        }

        private fun primitive(value: JsonPrimitive): PrefValue? = when {
            value.isString -> Text(value.content)
            value.content == "true" -> Bool(true)
            value.content == "false" -> Bool(false)
            else -> number(value.content)
        }

        /** Dart reads `1.0` and `1e3` as doubles and `1` as an int. */
        private fun number(text: String): PrefValue? = when {
            text.any { it == '.' || it == 'e' || it == 'E' } -> text.toDoubleOrNull()?.let(::Real)
            else -> text.toLongOrNull()?.let(::Whole) ?: text.toDoubleOrNull()?.let(::Real)
        }
    }
}
