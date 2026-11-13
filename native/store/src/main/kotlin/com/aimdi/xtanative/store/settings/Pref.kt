package com.aimdi.xtanative.store.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A value one of XTA's preferences may take, as XTA stores it. */
interface PrefChoice {
    val stored: String
}

/**
 * One of XTA's preferences: the key it is kept under, what it holds, and what
 * it reads as until someone sets it. Reading forgives the way XTA's own reads
 * do: a stored value of another kind, or outside the choices, reads as the
 * default.
 */
class Pref<T> internal constructor(
    val key: String,
    val default: T,
    /** What XTA writes under [key] at every launch when nothing is there; null when it writes nothing. */
    val seed: PrefValue?,
    private val reader: (PrefValue) -> T?,
    private val writer: (T) -> PrefValue,
) {
    /** [value] as this preference reads it. */
    fun read(value: PrefValue?): T = value?.let(reader) ?: default

    fun write(value: T): PrefValue = writer(value)

    override fun toString(): String = "Pref($key)"
}

/** Preferences XTA keeps one of per something it has many of: a group, a network. */
class PrefFamily<T> internal constructor(val prefix: String, private val make: (String) -> Pref<T>) {
    operator fun get(id: String): Pref<T> = make(prefix + id)

    /** What [key] is kept for, or null when it is not one of this family's keys. */
    fun idOf(key: String): String? = key.removePrefix(prefix).takeIf { key.startsWith(prefix) && it.isNotEmpty() }
}

/**
 * A part of XTA's settings. Each preference a section declares is listed in
 * [prefs], so nothing has to be named twice.
 */
abstract class PrefSection {
    private val declared = ArrayList<Pref<*>>()

    val prefs: List<Pref<*>> get() = declared

    private fun <T> declare(pref: Pref<T>): Pref<T> = pref.also { declared += it }

    protected fun flag(key: String, default: Boolean, seeded: Boolean = true) = declare(flagPref(key, default, seeded))

    protected fun whole(key: String, default: Int, choices: Set<Int>? = null, seeded: Boolean = true) =
        declare(wholePref(key, default, choices, seeded))

    protected fun long(key: String, default: Long, seeded: Boolean = true) =
        declare(Pref(key, default, PrefValue.Whole(default).takeIf { seeded }, { (it as? PrefValue.Whole)?.value }, PrefValue::Whole))

    protected fun real(key: String, default: Double, range: ClosedFloatingPointRange<Double>? = null, choices: Set<Double>? = null, seeded: Boolean = true) =
        declare(realPref(key, default, range, choices, seeded))

    protected fun text(key: String, default: String, seeded: Boolean = true) = declare(textPref(key, default, seeded))

    protected fun texts(key: String, default: List<String>, seeded: Boolean = true) = declare(textsPref(key, default, seeded))

    /** [seedText] is what XTA seeds when it is not [default] as JSON (an empty string, say). */
    protected fun jsonList(key: String, default: List<String> = emptyList(), seeded: Boolean = true, seedText: String? = null) =
        declare(jsonListPref(key, default, seeded, seedText))

    protected fun <E : PrefChoice> choice(
        key: String,
        default: E,
        values: List<E>,
        seeded: Boolean = true,
        parse: (String) -> E? = { raw -> values.firstOrNull { it.stored == raw } },
    ) = declare(Pref(key, default, PrefValue.Text(default.stored).takeIf { seeded }, { (it as? PrefValue.Text)?.value?.let(parse) }, { PrefValue.Text(it.stored) }))
}

internal fun flagPref(key: String, default: Boolean, seeded: Boolean) =
    Pref(key, default, PrefValue.Bool(default).takeIf { seeded }, { (it as? PrefValue.Bool)?.value }, PrefValue::Bool)

internal fun wholePref(key: String, default: Int, choices: Set<Int>?, seeded: Boolean) = Pref(
    key,
    default,
    PrefValue.Whole(default.toLong()).takeIf { seeded },
    { value -> (value as? PrefValue.Whole)?.value?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()?.takeIf { choices == null || it in choices } },
    { PrefValue.Whole(it.toLong()) },
)

/** XTA's own readers take a whole number where a double is kept; out of [range] is brought back into it. */
internal fun realPref(key: String, default: Double, range: ClosedFloatingPointRange<Double>?, choices: Set<Double>?, seeded: Boolean) = Pref(
    key,
    default,
    PrefValue.Real(default).takeIf { seeded },
    { value ->
        val number = when (value) {
            is PrefValue.Real -> value.value
            is PrefValue.Whole -> value.value.toDouble()
            else -> null
        }
        number?.takeIf { it.isFinite() && (choices == null || it in choices) }?.let { if (range == null) it else it.coerceIn(range) }
    },
    PrefValue::Real,
)

internal fun textPref(key: String, default: String, seeded: Boolean) =
    Pref(key, default, PrefValue.Text(default).takeIf { seeded }, { (it as? PrefValue.Text)?.value }, PrefValue::Text)

/** A list XTA keeps as a string list, read as leniently as XTA's `stringListPref`. */
internal fun textsPref(key: String, default: List<String>, seeded: Boolean) =
    Pref(key, default, PrefValue.Texts(default).takeIf { seeded }, ::lenientList, PrefValue::Texts)

/** A list XTA keeps as a JSON array in a string, as its search histories are. */
internal fun jsonListPref(key: String, default: List<String>, seeded: Boolean, seedText: String?): Pref<List<String>> {
    val write = { list: List<String> -> PrefValue.Text(JsonArray(list.map(::JsonPrimitive)).toString()) }
    return Pref(
        key,
        default,
        if (seeded) seedText?.let(PrefValue::Text) ?: write(default) else null,
        { value -> (value as? PrefValue.Text)?.value?.let(::jsonStrings) ?: (value as? PrefValue.Texts)?.value },
        write,
    )
}

/**
 * XTA's `stringListFrom`: a real list, a JSON array in a string, or failing
 * that the string split at commas. JSON that is not a list is no list.
 */
internal fun lenientList(value: PrefValue): List<String>? = when (value) {
    is PrefValue.Texts -> value.value
    is PrefValue.Text -> when (val parsed = strictJson(value.value)) {
        null -> value.value.split(',').map(String::trim).filter(String::isNotEmpty)
        else -> (parsed as? JsonArray)?.strings()
    }
    else -> null
}

internal fun jsonStrings(text: String): List<String>? = (strictJson(text) as? JsonArray)?.strings()

private fun JsonArray.strings(): List<String> = mapNotNull { item -> (item as? JsonPrimitive)?.takeIf { it.isString }?.content }

/**
 * [text] parsed the way Dart's `jsonDecode` parses it, or null where Dart
 * throws: kotlinx takes a bare word such as `feed` for a value, Dart does not.
 */
internal fun strictJson(text: String): JsonElement? =
    runCatching { Json.parseToJsonElement(text) }.getOrNull()?.takeIf(::isStrict)

private val jsonNumber = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

private fun isStrict(element: JsonElement): Boolean = when (element) {
    is JsonArray -> element.all(::isStrict)
    is JsonObject -> element.values.all(::isStrict)
    is JsonPrimitive -> element.isString || element.content in setOf("true", "false", "null") || jsonNumber.matches(element.content)
}
