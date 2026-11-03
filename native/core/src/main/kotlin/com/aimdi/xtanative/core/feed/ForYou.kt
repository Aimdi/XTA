package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The For you read: X's home timeline as one account sees it. */
fun interface HomeReads {
    suspend fun home(account: XAccount, cursor: String?, count: Int): XTimelinePage
}

/** A page of For you, and where it goes on: a plain cursor for one account, every account's for several. */
data class ForYouPage(val threads: List<XThread>, val next: String?)

/**
 * Which X accounts the home feeds use (XTA's home feed account filter): For
 * you merges the timelines of the ones left on, and the Following feed's
 * searches prefer them.
 */
object HomeAccounts {
    /** The accounts left on; never none while accounts exist. */
    fun enabled(accounts: List<XAccount>, disabledIds: Set<String>): List<XAccount> {
        if (disabledIds.isEmpty()) return accounts
        return accounts.filter { it.id !in disabledIds }.ifEmpty { accounts }
    }

    /** False when turning [accountId] off would leave no account on. */
    fun canDisable(accountId: String, accounts: List<XAccount>, disabledIds: Set<String>): Boolean =
        accounts.any { it.id !in disabledIds && it.id != accountId }

    /** XTA keeps the ids as a JSON list; anything else is none. */
    fun decodeIds(raw: String?): List<String> =
        if (raw.isNullOrEmpty()) emptyList() else J.parse(raw).list.mapNotNull { it.string?.takeIf(String::isNotEmpty) }

    fun encodeIds(ids: Collection<String>): String = JsonArray(ids.map(::JsonPrimitive)).toString()
}

/** For you as XTA reads it: every enabled account's home timeline, merged. */
object ForYou {
    /** Timelines read at once. */
    const val CONCURRENCY = 2

    /**
     * One page. A single account pages on X's own cursor; several page on
     * each account's, kept together in [ForYouPage.next], and an account whose
     * page failed keeps its cursor for the next page. Posts by turned-off
     * accounts and [excludedAuthorIds] (the members of groups kept off the
     * home feeds) are left out. The failure is thrown only when nothing was read.
     */
    suspend fun page(
        accounts: List<XAccount>,
        disabledIds: Set<String>,
        cursor: String?,
        reads: HomeReads,
        count: Int = 20,
        excludedAuthorIds: Set<String> = emptySet(),
    ): ForYouPage {
        val sources = HomeAccounts.enabled(accounts, disabledIds)
        val excluded = disabledIds + excludedAuthorIds
        if (sources.isEmpty()) return ForYouPage(emptyList(), null)
        if (sources.size == 1) {
            val page = reads.home(sources.single(), cursor, count)
            return ForYouPage(dropFromAuthors(page.threads, excluded), page.bottomCursor)
        }
        val previous = if (cursor == null) emptyMap() else decodeCursors(cursor)
        val lastError = AtomicReference<Throwable?>()
        val pages = mapWithConcurrency(sources, CONCURRENCY) { account ->
            val accountCursor = previous[account.id]
            when {
                // This account's timeline ran out on an earlier page.
                cursor != null && accountCursor == null -> AccountPage(account.id, emptyList(), null)
                else -> try {
                    val page = reads.home(account, accountCursor, count)
                    AccountPage(account.id, page.threads, page.bottomCursor)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    lastError.set(error)
                    AccountPage(account.id, emptyList(), accountCursor)
                }
            }
        }
        val threads = dropFromAuthors(merge(pages.map { it.threads }), excluded)
        lastError.get()?.takeIf { threads.isEmpty() }?.let { throw it }
        return ForYouPage(threads, encodeCursors(pages.mapNotNull { page -> page.next?.takeIf(String::isNotEmpty)?.let { page.accountId to it } }.toMap()))
    }

    private class AccountPage(val accountId: String, val threads: List<XThread>, val next: String?)

    /** Newest first, each thread once, the first copy kept. */
    fun merge(batches: List<List<XThread>>): List<XThread> =
        batches.flatten().distinctBy { it.id }.sortedByDescending(::sortTime)

    private fun sortTime(thread: XThread): Long = thread.posts.firstNotNullOfOrNull { it.createdAt }?.toEpochMilli() ?: 0

    fun hasAuthor(thread: XThread, authorIds: Set<String>): Boolean = thread.posts.any { it.author.id in authorIds }

    fun dropFromAuthors(threads: List<XThread>, authorIds: Set<String>): List<XThread> =
        if (authorIds.isEmpty()) threads else threads.filterNot { hasAuthor(it, authorIds) }

    /** Every account's cursor, as one JSON object. */
    fun decodeCursors(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty() || !raw.startsWith("{")) return emptyMap()
        val root = J.parse(raw)
        return root.keys.mapNotNull { key -> root[key].string?.takeIf(String::isNotEmpty)?.let { key to it } }.toMap()
    }

    fun encodeCursors(cursors: Map<String, String>): String? =
        if (cursors.isEmpty()) null else JsonObject(cursors.mapValues { JsonPrimitive(it.value) }).toString()
}
