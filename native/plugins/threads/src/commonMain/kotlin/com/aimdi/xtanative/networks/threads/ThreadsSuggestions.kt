package com.aimdi.xtanative.networks.threads

/** Something to finish a search with while it is typed, found on the device: nothing is asked of Meta per key. */
sealed interface ThreadsSuggestion {
    /** The text the search field takes. */
    val text: String

    /** A search run before. */
    data class Recent(override val text: String) : ThreadsSuggestion

    /** An account followed here. */
    data class Account(val handle: String, val name: String, val avatarUrl: String?) : ThreadsSuggestion {
        override val text: String get() = "@$handle"
    }

    /** A topic seen on posts read in this session. */
    data class Topic(val name: String) : ThreadsSuggestion {
        override val text: String get() = "#$name"
    }
}

object ThreadsSuggestions {
    const val LIMIT = 8

    /**
     * What [typed] may be finishing, best first: recent searches, accounts and
     * topics whose words start with what was typed, then those holding it.
     * An `@` asks for accounts only, a `#` for topics only. Nothing for a
     * blank field, and nothing that only repeats what is typed.
     */
    fun of(
        typed: String,
        recent: List<String>,
        accounts: List<ThreadsSuggestion.Account>,
        topics: List<String>,
        limit: Int = LIMIT,
    ): List<ThreadsSuggestion> {
        val text = typed.trim()
        val needle = text.removePrefix("@").removePrefix("#").trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val wanted = when {
            text.startsWith("@") -> accounts
            text.startsWith("#") -> topics.map(ThreadsSuggestion::Topic) + recent.filter { it.startsWith("#") }.map(ThreadsSuggestion::Recent)
            else -> recent.map(ThreadsSuggestion::Recent) + accounts + topics.map(ThreadsSuggestion::Topic)
        }
        return wanted
            .mapNotNull { suggestion -> rank(suggestion, needle)?.let { suggestion to it } }
            .filter { (suggestion, _) -> !suggestion.text.equals(text, ignoreCase = true) }
            .sortedBy { it.second }
            .map { it.first }
            .distinctBy { it.text.lowercase() }
            .take(limit)
    }

    /** 0 when a word of [suggestion] starts with [needle], 1 when it only holds it, null when it does not. */
    private fun rank(suggestion: ThreadsSuggestion, needle: String): Int? {
        val names = when (suggestion) {
            is ThreadsSuggestion.Account -> listOf(suggestion.handle, suggestion.name)
            is ThreadsSuggestion.Topic -> listOf(suggestion.name)
            is ThreadsSuggestion.Recent -> listOf(suggestion.text.removePrefix("@").removePrefix("#"))
        }.map { it.lowercase() }
        return when {
            names.any { name -> name.startsWith(needle) || name.split(' ', '_', '.').any { it.startsWith(needle) } } -> 0
            names.any { it.contains(needle) } -> 1
            else -> null
        }
    }
}
