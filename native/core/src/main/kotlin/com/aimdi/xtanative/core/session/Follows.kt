package com.aimdi.xtanative.core.session

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.feed.FeedMember
import com.aimdi.xtanative.core.x.XRateLimitedException
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** A search followed here, by its query, from [followedAt] (epoch milliseconds); a group reads it once it is put in one. */
data class SavedSearch(val query: String, val followedAt: Long) {
    val member: FeedMember get() = FeedMember.search(query, followedAt)
}

/** X handles read from a paste (XTA's import of handles): `@name`, bare names, or x.com and twitter.com profile links. */
object ImportHandles {
    private val handle = Regex("^@?([A-Za-z0-9_]{1,15})$")
    private val bare = Regex("^[A-Za-z0-9_]{1,15}$")
    private val hosts = setOf("x.com", "www.x.com", "twitter.com", "www.twitter.com")

    /** Paths on X that are not profiles. */
    private val reserved = setOf(
        "i", "home", "search", "explore", "settings", "intent", "compose", "messages",
        "notifications", "login", "signup", "hashtag", "share", "tos", "privacy",
    )

    /** Every handle in [raw], separated by spaces, line breaks, commas or semicolons; each once, whatever its case. */
    fun parse(raw: String): List<String> =
        raw.split(Regex("[\\s,;]+")).mapNotNull(::fromToken).distinctBy { it.lowercase() }

    fun fromToken(raw: String): String? {
        val token = raw.trim()
        if (token.isEmpty()) return null
        return fromProfileUrl(token) ?: handle.matchEntire(token)?.groupValues?.get(1)
    }

    private fun fromProfileUrl(raw: String): String? {
        val uri = runCatching { URI(if ("://" in raw) raw else "https://$raw") }.getOrNull() ?: return null
        if (uri.host?.lowercase() !in hosts) return null
        val first = uri.path.orEmpty().split('/').firstOrNull { it.isNotEmpty() }?.replace("@", "") ?: return null
        return first.takeIf { bare.matches(it) && it.lowercase() !in reserved }
    }
}

/** A page of a list's members. */
data class MemberPage(val users: List<XUser>, val next: String?)

/** What importing an X list reads: its name, then its members page by page. */
interface ListReads {
    /** The list's name; null when X has no such list (deleted, or private). */
    suspend fun listName(listId: String): String?

    suspend fun listMembers(listId: String, cursor: String?): MemberPage
}

/** X has no list by that id, or will not show it. */
class ListNotFoundException(val listId: String) : Exception("No list $listId")

/** An X list as read for importing: its name and members, each once. */
data class ImportedList(val id: String, val name: String, val members: List<XUser>)

/** Importing an X list as a group (XTA's list import): read only; nothing is followed on X. */
object ListImport {
    private val digits = Regex("^\\d+$")

    /** The id in what the reader typed: a bare id, or an `x.com/i/lists/<id>` link. */
    fun listId(input: String): String? {
        val trimmed = input.trim()
        if (digits.matches(trimmed)) return trimmed
        val path = runCatching { URI(trimmed).path }.getOrNull() ?: return null
        val parts = path.split('/').filter { it.isNotEmpty() }
        return parts.takeIf { it.size == 3 && it[0] == "i" && it[1] == "lists" && digits.matches(it[2]) }?.get(2)
    }

    /**
     * The list's name and members, page by page, telling [onProgress] how many
     * so far. Paging ends on a missing, `0` or repeated cursor, or a page that
     * brought nobody new.
     */
    suspend fun read(listId: String, reads: ListReads, onProgress: (Int) -> Unit = {}): ImportedList {
        val name = reads.listName(listId) ?: throw ListNotFoundException(listId)
        val members = LinkedHashMap<String, XUser>()
        var cursor: String? = null
        while (true) {
            val page = reads.listMembers(listId, cursor)
            val fresh = page.users.filter { it.id.isNotBlank() && it.screenName.isNotBlank() && members.putIfAbsent(it.id, it) == null }
            if (fresh.isNotEmpty()) onProgress(members.size)
            val next = page.next
            if (next.isNullOrEmpty() || next == "0" || next == cursor || fresh.isEmpty()) break
            cursor = next
        }
        return ImportedList(listId, name, members.values.toList())
    }
}

/** What X says of an account looked up by handle or by id. */
sealed interface AccountLookup<out T> {
    data class Found<T>(val profile: T) : AccountLookup<T>

    data object Missing : AccountLookup<Nothing>

    data object Suspended : AccountLookup<Nothing>
}

/** How a followed account stands on X, for cleaning up follows (XTA's broken subscriptions). */
enum class FollowHealth {
    EXISTS,
    RENAMED,
    MISSING,
    SUSPENDED,
    UNREACHABLE,
    RATE_LIMITED,
    ;

    /** Only what X confirmed gone is offered for removal; a failure to ask never is. */
    val removable: Boolean get() = this == MISSING || this == SUSPENDED
}

data class HealthCheck<T>(val health: FollowHealth, val profile: T? = null)

/** Checking a follow: by its handle, and by its id when the handle is gone (a rename). */
object FollowCheck {
    suspend fun <T> check(
        byName: suspend () -> AccountLookup<T>,
        byId: suspend () -> AccountLookup<T>,
        timeoutMillis: Long = 15_000,
    ): HealthCheck<T> {
        val name = lookup(byName, timeoutMillis)
        if (name.health != FollowHealth.MISSING) return name
        val id = lookup(byId, timeoutMillis)
        return if (id.health == FollowHealth.EXISTS) id.copy(health = FollowHealth.RENAMED) else id
    }

    private suspend fun <T> lookup(read: suspend () -> AccountLookup<T>, timeoutMillis: Long): HealthCheck<T> = try {
        when (val found = withTimeoutOrNull(timeoutMillis) { read() }) {
            is AccountLookup.Found -> HealthCheck(FollowHealth.EXISTS, found.profile)
            AccountLookup.Missing -> HealthCheck(FollowHealth.MISSING)
            AccountLookup.Suspended -> HealthCheck(FollowHealth.SUSPENDED)
            null -> HealthCheck(FollowHealth.UNREACHABLE)
        }
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        HealthCheck(if (error is XRateLimitedException) FollowHealth.RATE_LIMITED else FollowHealth.UNREACHABLE)
    }
}

/**
 * Followed accounts as X shows them now: every page of posts names the
 * accounts behind it with their current handle, name and picture.
 */
object ProfileRefresh {
    private val pictureSize = Regex("_(?:normal|bigger|mini|reasonably_small|\\d+x\\d+)(?=\\.\\w+$)")

    /** The accounts behind [threads]: each post's author and the authors of what it reposts and quotes, and of what those quote. */
    fun postAuthors(threads: List<XThread>): List<XUser> = threads.flatMap { it.posts }.flatMap(::shown).map { it.author }

    private fun shown(post: XPost?): List<XPost> =
        if (post == null) emptyList() else listOf(post) + shown(post.reposted) + shown(post.quoted)

    /**
     * The followed accounts among [users] whose handle, name or picture differ,
     * by id; what X left out stays as it was, and the same picture at another
     * size is no change.
     */
    fun refreshed(following: List<FollowedAccount>, users: Iterable<XUser>): Map<String, FollowedAccount> {
        val byId = users.associateBy { it.id }
        return following.mapNotNull { stored -> byId[stored.id]?.let { refreshedOne(stored, it) }?.let { stored.id to it } }.toMap()
    }

    /** Whether [fresh] is [stored] under another handle, which the feeds search it by; a change of case is the same handle. */
    fun renamed(stored: FollowedAccount, fresh: FollowedAccount): Boolean = !stored.screenName.equals(fresh.screenName, ignoreCase = true)

    private fun refreshedOne(stored: FollowedAccount, user: XUser): FollowedAccount? {
        val screenName = user.screenName.ifBlank { stored.screenName }
        val name = user.name.ifBlank { stored.name }
        val picture = user.avatarUrl?.takeIf { it.isNotBlank() } ?: stored.avatarUrl
        val same = screenName == stored.screenName && name == stored.name && pictureKey(picture) == pictureKey(stored.avatarUrl)
        return if (same) null else stored.copy(screenName = screenName, name = name, avatarUrl = picture)
    }

    private fun pictureKey(url: String?): String? = url?.let { pictureSize.replaceFirst(it, "") }
}
