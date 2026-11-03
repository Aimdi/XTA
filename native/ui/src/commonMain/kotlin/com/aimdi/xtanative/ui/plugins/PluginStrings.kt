package com.aimdi.xtanative.ui.plugins

import java.util.Locale

/**
 * A plugin's text in each language it has. The plugins build generates one
 * object for each plugin, `Strings` in the package of its entry class, from
 * its strings/values/strings.xml (the default, English) and
 * strings/values-<language>/strings.xml: each `<string>` becomes a
 * [PluginText] and each `<plurals>` a [PluginPlural], named as in the file.
 *
 * [tables] maps a language tag ("" for the default, "de", "pt-BR") to the
 * texts by name; a plural's forms are named "name#one" and "name#other".
 */
open class PluginStrings(tables: Map<String, Map<String, String>>) {
    private val byTag = tables.mapKeys { normalized(it.key) }

    /** The text named [name] in [language]: in its whole tag, its language alone, then the default; [name] itself when none has it. */
    fun get(name: String, language: String): String = lookup(name, language) ?: name

    /** [get], its placeholders (%1$s, %1$d) filled with [args] as [language] writes them. */
    fun format(name: String, language: String, vararg args: Any): String = fill(get(name, language), language, args)

    /** The plural named [name] for [count], filled with [args]: "one" for 1, as English and German say it, else "other". */
    fun plural(name: String, count: Int, language: String, vararg args: Any): String {
        val form = if (count == 1) "one" else "other"
        val text = lookup("$name#$form", language) ?: lookup("$name#other", language) ?: name
        return fill(text, language, args)
    }

    /** The text named [name], for the generated object's properties. */
    protected fun text(name: String): PluginText = PluginText(this, name)

    /** The plural named [name], for the generated object's properties. */
    protected fun plural(name: String): PluginPlural = PluginPlural(this, name)

    private fun lookup(name: String, language: String): String? {
        val tag = normalized(language)
        return byTag[tag]?.get(name) ?: byTag[tag.substringBefore('-')]?.get(name) ?: byTag[""]?.get(name)
    }

    private fun fill(text: String, language: String, args: Array<out Any>): String =
        if (args.isEmpty()) text else runCatching { String.format(Locale.forLanguageTag(language), text, *args) }.getOrDefault(text)

    private companion object {
        fun normalized(tag: String): String = tag.trim().replace('_', '-').lowercase()
    }
}

/** One of a plugin's texts: [get] it in a language, or [pluginString] it in a composable. */
class PluginText internal constructor(val strings: PluginStrings, val name: String) {
    fun get(language: String, vararg args: Any): String = strings.format(name, language, *args)
}

/** One of a plugin's plurals: [get] it for a count, or [pluginPlural] it in a composable. */
class PluginPlural internal constructor(val strings: PluginStrings, val name: String) {
    fun get(count: Int, language: String, vararg args: Any): String = strings.plural(name, count, language, *args)
}
