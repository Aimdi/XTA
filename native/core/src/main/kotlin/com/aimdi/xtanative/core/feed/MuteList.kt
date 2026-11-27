package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * An account muted on this device, by its handle (matched in any case) and,
 * once known, its id, which still finds it after a rename. [network] is
 * where the handle lives: X's unless a plugin's network is named.
 */
data class MutedAccount(val handle: String, val id: String? = null, val until: Instant? = null, val network: String = X) {
    fun activeAt(now: Instant): Boolean = until == null || !until.isBefore(now)

    /** Whether this is who [handle] (with or without its @) and [id] name on [network]. */
    fun names(handle: String?, id: String?, network: String = X): Boolean {
        if (network != this.network) return false
        if (id != null && id.isNotEmpty() && id == this.id) return true
        return handle != null && MuteList.handleKey(handle) == MuteList.handleKey(this.handle)
    }

    companion object {
        const val X = "x"
    }
}

/**
 * The reader's app-wide mute list: words and phrases, matched as a group's
 * muted words are ([MutedKeywords.matches]), and accounts. It never hides a
 * post silently: what it matches is folded behind why ([reason]), so the
 * reader can still open it. It stays on the device, and goes with the
 * settings into a backup.
 */
data class MuteList(val words: List<MutedKeyword> = emptyList(), val accounts: List<MutedAccount> = emptyList()) {
    val isEmpty: Boolean get() = words.isEmpty() && accounts.isEmpty()

    val size: Int get() = words.size + accounts.size

    /**
     * Why [thread] is muted at [now]: "@handle" for a muted account among its
     * posts' authors (and those of what they repost and quote), else the
     * first muted word its text has; null when nothing mutes it.
     * [exceptHandle] is the profile on screen: its own posts are what the
     * reader opened it for, so a mute of that account is set aside there.
     */
    fun reason(thread: XThread, now: Instant, exceptHandle: String? = null): String? {
        if (isEmpty) return null
        val active = accounts.filter { it.activeAt(now) && (exceptHandle == null || handleKey(it.handle) != handleKey(exceptHandle)) }
        if (active.isNotEmpty()) {
            val authors = thread.posts.flatMap(::authorsOf)
            active.firstOrNull { account -> authors.any { account.names(it.author.screenName, it.author.id) } }?.let { return "@" + it.handle }
        }
        return wordIn(FeedRules.searchText(thread), now)
    }

    /** Why a post of another network is muted: a muted account of [network], or a muted word of [text]; null when nothing mutes it. */
    fun reason(network: String, handle: String?, authorId: String?, text: String, now: Instant): String? {
        if (isEmpty) return null
        accounts.firstOrNull { it.activeAt(now) && it.names(handle, authorId, network) }?.let { return "@" + it.handle }
        return wordIn(text, now)
    }

    private fun wordIn(text: String, now: Instant): String? =
        words.firstOrNull { it.activeAt(now) && MutedKeywords.matches(text, it.term) }?.term

    /** Whether [handle] is muted (for good or for now) on X. */
    fun mutes(handle: String, id: String?, now: Instant): Boolean = accounts.any { it.activeAt(now) && it.names(handle, id) }

    /** [term] muted too, until [until]; a term already there, in any case, takes the new time. */
    fun withWord(term: String, until: Instant? = null): MuteList {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return this
        val kept = words.filterNot { it.term.equals(trimmed, ignoreCase = true) }
        return copy(words = kept + MutedKeyword(trimmed, until, KeywordAction.FOLD))
    }

    /** [handle] muted too, until [until]; one already there takes the new time and keeps or gains its id. */
    fun withAccount(handle: String, id: String? = null, until: Instant? = null, network: String = MutedAccount.X): MuteList {
        val bare = bareHandle(handle)
        if (bare.isEmpty()) return this
        val old = accounts.firstOrNull { it.names(bare, id, network) }
        val kept = accounts.filterNot { it === old }
        return copy(accounts = kept + MutedAccount(bare, id?.takeIf { it.isNotEmpty() } ?: old?.id, until, network))
    }

    fun withoutWord(term: String): MuteList = copy(words = words.filterNot { it.term == term })

    fun withoutAccount(account: MutedAccount): MuteList = copy(accounts = accounts.filterNot { it == account })

    /** Every mute of the X account [handle] or [id] taken out. */
    fun unmuting(handle: String, id: String?): MuteList = copy(accounts = accounts.filterNot { it.names(handle, id) })

    /** [typed] as the settings' field takes it: "@name" is an account, anything else a word or phrase. */
    fun withTyped(typed: String, until: Instant? = null): MuteList {
        val trimmed = typed.trim()
        return if (isHandle(trimmed)) withAccount(trimmed, until = until) else withWord(trimmed, until)
    }

    /** What has run out by [now] taken out: the list as it is shown. */
    fun activeAt(now: Instant): MuteList = copy(words = words.filter { it.activeAt(now) }, accounts = accounts.filter { it.activeAt(now) })

    /** The stored form; empty when nothing is muted. */
    fun encode(): String = if (isEmpty) "" else buildJsonObject {
        put("words", MutedKeywords.json(words))
        put(
            "accounts",
            JsonArray(
                accounts.map { account ->
                    buildJsonObject {
                        put("handle", account.handle)
                        account.id?.let { put("id", it) }
                        account.until?.let { put("until", it.toString()) }
                        if (account.network != MutedAccount.X) put("network", account.network)
                    }
                },
            ),
        )
    }.toString()

    companion object {
        private val handlePattern = Regex("^@[A-Za-z0-9_]{1,50}$")

        /** Whether [typed] names an account: an @ and a handle's letters, digits and underscores. */
        fun isHandle(typed: String): Boolean = handlePattern.matches(typed.trim())

        internal fun bareHandle(handle: String): String = handle.trim().removePrefix("@").trim()

        internal fun handleKey(handle: String): String = bareHandle(handle).lowercase()

        private fun authorsOf(post: XPost): List<XPost> = listOfNotNull(post, post.reposted, post.quoted, post.reposted?.quoted)

        /** The stored form read back; nothing muted for an empty or unreadable one. */
        fun parse(raw: String?): MuteList {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return MuteList()
            val root = runCatching { J.parse(text) }.getOrNull() ?: return MuteList()
            return MuteList(
                words = root["words"].list.mapNotNull(MutedKeywords::fromJson).map { it.copy(action = KeywordAction.FOLD) },
                accounts = root["accounts"].list.mapNotNull(::account),
            )
        }

        private fun account(row: J): MutedAccount? {
            val handle = row["handle"].string?.let(::bareHandle)?.takeIf { it.isNotEmpty() } ?: return null
            return MutedAccount(handle, row["id"].string?.takeIf { it.isNotEmpty() }, MutedKeywords.instantOf(row["until"].string), row["network"].string ?: MutedAccount.X)
        }
    }
}
