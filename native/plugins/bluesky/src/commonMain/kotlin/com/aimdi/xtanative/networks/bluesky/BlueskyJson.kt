package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** The string trimmed, or null when it is missing or blank. */
internal fun J.nonBlank(): String? = string?.trim()?.ifEmpty { null }

/** A whole number as XTA's `Json.integer` reads one: a number (a fraction cut off), or a string of digits. */
internal val J.integer: Long?
    get() = (raw as? JsonPrimitive)?.let { value ->
        when {
            value.isString -> value.content.trim().toLongOrNull()
            else -> value.longOrNull ?: value.doubleOrNull?.takeIf { it.isFinite() }?.toLong()
        }
    }

/** A JSON number (not a string holding one), when finite. */
internal val J.number: Double?
    get() = (raw as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }

/** Width over height of an `aspectRatio` object, or its precomputed `aspect`; null when either is missing. */
internal fun J.aspect(): Double? {
    if (raw !is JsonObject) return null
    this["aspect"].number?.takeIf { it > 0 }?.let { return it }
    val width = this["width"].number
    val height = this["height"].number
    return if (width != null && height != null && width > 0 && height > 0) width / height else null
}
