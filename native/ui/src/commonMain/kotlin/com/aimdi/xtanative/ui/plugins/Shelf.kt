package com.aimdi.xtanative.ui.plugins

import java.util.UUID

/**
 * What a plugin's screen hands the next one it opens (the post as its list
 * had it, the pages already read), the last [capacity] of them: a route is
 * text and cannot carry them, so it names them by the key [put] gives. Keys
 * are never given twice, not even after the app was started again, when
 * what they named is gone and a screen reads what its route names afresh.
 */
class PluginShelf(private val capacity: Int = 32) {
    private val run = UUID.randomUUID().toString().substringBefore('-')
    private val kept = object : LinkedHashMap<String, Any>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>?): Boolean = size > capacity
    }
    private var last = 0

    /** Keeps [value] and answers its key. */
    @Synchronized
    fun put(value: Any): String = "$run-${++last}".also { kept[it] = value }

    /** What [key] names; null once it was let go, or for no key. */
    @Synchronized
    fun get(key: String?): Any? = key?.let { kept[it] }

    /** What [key] names when it is a [T]. */
    inline fun <reified T> take(key: String?): T? = get(key) as? T
}
