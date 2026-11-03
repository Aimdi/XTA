package com.aimdi.xtanative.core.repairs

import com.aimdi.xtanative.core.json.J

/**
 * What a repaired value may look like (XTA's RepairKind). No kind can hold
 * a host, an address or a path, so a published repair can change what a
 * request carries, never where it goes.
 */
enum class RepairKind(private val shape: Regex) {
    /** Request document ids, app ids. */
    DIGITS(Regex("[0-9]{1,32}")),

    /** Request names, app client ids: letters, digits, `_` and `-`. */
    TOKEN(Regex("[A-Za-z0-9_-]{1,128}")),

    /** App versions: numbers joined by dots. */
    VERSION(Regex("[0-9]{1,6}(?:\\.[0-9]{1,6}){0,5}")),

    /** Header text such as a user agent: printable ASCII on one line, no space at either end. */
    HEADER(Regex("[\\x21-\\x7E](?:[\\x20-\\x7E]{0,398}[\\x21-\\x7E])?")),
    ;

    fun fits(value: String): Boolean = shape.matches(value)
}

/**
 * One value a network may change (a doc id, an app id, a user agent), under
 * its key in XTA's published `repairs.json`, with the value this build ships.
 */
data class RepairSlot(val key: String, val shipped: String, val kind: RepairKind) {
    /** [published]'s value for this slot when it has one of the slot's kind, else the shipped one. */
    fun valueIn(published: Map<String, String>): String = published[key]?.takeIf(kind::fits) ?: shipped
}

/**
 * XTA's published repairs (docs/specs/published-repairs.md in the Flutter
 * app): one file every install reads once a day, whose entries replace
 * values a network may change, so a change on a service is repaired without a
 * release. Both apps read the same file. Each network declares its slots
 * beside its client and picks its values with [RepairSlot.valueIn], so an
 * entry for a key no installed network knows, or of the wrong kind, changes
 * nothing.
 */
object Repairs {
    /** The entries of [body], a published repairs file; none when it does not read as one. */
    fun parse(body: String): Map<String, String> {
        val repairs = J.parse(body)["repairs"]
        return repairs.keys.mapNotNull { key -> repairs[key].string?.let { key to it } }.toMap()
    }

    /** The entries of [published] that fit one of [slots]: what XTA's PluginRepairs.apply keeps. */
    fun inForce(published: Map<String, String>, slots: Collection<RepairSlot>): Map<String, String> =
        slots.mapNotNull { slot -> published[slot.key]?.takeIf(slot.kind::fits)?.let { slot.key to it } }.toMap()
}
