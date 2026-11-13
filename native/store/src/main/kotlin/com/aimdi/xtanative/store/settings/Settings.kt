package com.aimdi.xtanative.store.settings

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * Every preference at one moment: what was set, read through XTA's
 * defaults. Keys this build does not declare (a later XTA's, a network's
 * own) are kept as they are, so they travel through a backup untouched.
 */
data class Settings(val values: Map<String, PrefValue> = emptyMap()) {
    operator fun <T> get(pref: Pref<T>): T = pref.read(values[pref.key])

    fun isSet(pref: Pref<*>): Boolean = pref.key in values

    fun <T> with(pref: Pref<T>, value: T): Settings = with(pref.key, pref.write(value))

    /** [key] holding [value] as it is; null takes the key away, as XTA's preferences do. */
    fun with(key: String, value: PrefValue?): Settings =
        copy(values = if (value == null) values - key else values + (key to value))

    /** [pref] back to its default. */
    fun without(pref: Pref<*>): Settings = with(pref.key, null)

    /**
     * What XTA's own preferences would hold: the defaults it writes at every
     * launch, under what was set. Like XTA's launch, a stored value of
     * another kind than its seeded default gives way to the default.
     */
    fun preferences(): Map<String, PrefValue> {
        val seeds = XtaPrefs.all.mapNotNull { pref -> pref.seed?.let { pref.key to it } }.toMap()
        val stored = values.mapValues { (key, value) -> seeds[key]?.takeIf { it::class != value::class } ?: value }
        return seeds + stored
    }

    /**
     * The settings section of an XTA backup: [preferences] without
     * [SecretPrefs]. A subscriptions-only export carries just the preferences
     * that hold followed accounts; null when there is nothing to carry.
     */
    fun exported(includeSettings: Boolean = true, includeSubscriptions: Boolean = true): Map<String, JsonElement>? {
        val safe = preferences().filterKeys { !SecretPrefs.isSecret(it) }.mapValues { it.value.toJson() }
        val subscriptionKeys = XtaPrefs.subscriptionPrefs.map { it.key }.toSet()
        return when {
            includeSettings -> safe
            includeSubscriptions -> safe.filterKeys { it in subscriptionKeys }.takeIf { it.isNotEmpty() }
            else -> null
        }
    }

    /**
     * [preferences] from a backup applied the way XTA's import applies them:
     * credentials are skipped so another device's cannot pair with this one's
     * state, a list keeps its strings, null takes a key away, and anything
     * preferences cannot hold is left out.
     */
    fun restoring(preferences: Map<String, JsonElement>): Settings =
        preferences.entries.filterNot { SecretPrefs.isSecret(it.key) }.fold(this) { settings, (key, element) ->
            when (element) {
                JsonNull -> settings.with(key, null)
                else -> PrefValue.fromJson(element)?.let { settings.with(key, it) } ?: settings
            }
        }
}
