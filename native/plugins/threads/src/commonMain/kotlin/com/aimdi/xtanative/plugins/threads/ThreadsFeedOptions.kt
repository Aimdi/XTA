package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.feed.MutedKeywords
import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Which posts of the merged feed the tab shows. */
internal enum class ThreadsFeedContent { ALL, MEDIA, LINKS }

/**
 * What the reader chose to see in the tab (threads_feed_options.dart),
 * applied to what is loaded: nothing here changes what Meta is asked. Kept
 * in XTA's `plugin.threads.feed_options` as XTA writes it, with the words
 * muted on this device beside XTA's keys when there are some. The muted
 * words hide a post in Home and in For you alike, matched as the groups'
 * muted words are (a single word whole, a phrase anywhere). So do the
 * accounts muted here ([mutedAccounts], their posts and what they repost)
 * and the posts the reader hid one by one ([hiddenPosts]), which a
 * conversation's replies and the app's timelines leave out too. All of it
 * stays on this device: nothing is muted, hidden or reported on Threads.
 */
internal data class ThreadsFeedOptions(
    val content: ThreadsFeedContent = ThreadsFeedContent.ALL,
    val hideReplies: Boolean = false,
    val hideReposts: Boolean = false,
    val mutedWords: List<String> = emptyList(),
    /** The handles muted on this device, lower case, in the order they were muted. */
    val mutedAccounts: List<String> = emptyList(),
    /** The ids of the posts hidden one by one, the newest last; at most [HIDDEN_LIMIT]. */
    val hiddenPosts: List<String> = emptyList(),
) {
    private val hiddenIds: Set<String> by lazy { hiddenPosts.toHashSet() }


    /** How many choices differ from showing everything, for the filter's badge. */
    val activeCount: Int get() = listOf(content != ThreadsFeedContent.ALL, hideReplies, hideReposts, mutedWords.isNotEmpty(), mutedAccounts.isNotEmpty()).count { it }

    val filtered: Boolean get() = activeCount > 0

    /** Whether something but the muted words and accounts is chosen, which Reset filters puts back. */
    val resettable: Boolean get() = copy(mutedWords = emptyList(), mutedAccounts = emptyList()).filtered

    /** These choices shown everything again, the muted words and accounts and the hidden posts kept: they are lists the reader made, not switches. */
    fun reset(): ThreadsFeedOptions = ThreadsFeedOptions(mutedWords = mutedWords, mutedAccounts = mutedAccounts, hiddenPosts = hiddenPosts)

    /** [posts] as these choices show them. */
    fun shown(posts: List<ThreadsPost>): List<ThreadsPost> = if (!filtered && hiddenPosts.isEmpty()) posts else posts.filter { post ->
        !(hideReplies && post.isReply) && !(hideReposts && post.isRepost) && matches(post) && !muted(post) && !hides(post)
    }

    /** [posts] without those a muted word, a muted account or the reader hides, as For you shows them. */
    fun unmuted(posts: List<ThreadsPost>): List<ThreadsPost> =
        if (mutedWords.isEmpty() && !hidesSome) posts else posts.filterNot { muted(it) || hides(it) }

    /** Whether an account is muted or a post hidden here. */
    val hidesSome: Boolean get() = mutedAccounts.isNotEmpty() || hiddenPosts.isNotEmpty()

    /** Whether [handle] is muted on this device. */
    fun mutes(handle: String?): Boolean = mutedAccounts.isNotEmpty() && normalised(handle) in mutedAccounts

    /**
     * Whether the reader hid [post] itself, or muted who wrote it or who
     * reposted it. A post that only quotes a muted account stays: its author
     * is someone the reader still reads.
     */
    fun hides(post: ThreadsPost): Boolean =
        hidesSome && (post.id in hiddenIds || mutes(post.handle) || (post.isRepost && mutes(post.repostedByHandle)))

    /** [posts] without the hidden ones and the muted accounts', the muted words left alone: what the app's timelines and a list beside a post take. */
    fun unhidden(posts: List<ThreadsPost>): List<ThreadsPost> = if (!hidesSome) posts else posts.filterNot(::hides)

    /**
     * A conversation's reply [threads] as a reader who muted or hid some
     * reads them: a thread that starts with one is left out, and one that
     * holds one further down ends above it, since what follows answers it.
     */
    fun replies(threads: List<List<ThreadsPost>>): List<List<ThreadsPost>> =
        if (!hidesSome) threads else threads.mapNotNull { chain -> chain.takeWhile { !hides(it) }.ifEmpty { null } }

    /** [handle] muted here too; one already muted, or none, changes nothing. */
    fun mutingAccount(handle: String): ThreadsFeedOptions {
        val account = normalised(handle)
        return if (account.isEmpty() || account in mutedAccounts) this else copy(mutedAccounts = mutedAccounts + account)
    }

    fun unmutingAccount(handle: String): ThreadsFeedOptions = copy(mutedAccounts = mutedAccounts - normalised(handle))

    /** The post [id] hidden here too, the oldest hidden ones forgotten past [HIDDEN_LIMIT]: by then they are far down every feed. */
    fun hiding(id: String): ThreadsFeedOptions =
        if (id.isBlank() || id in hiddenIds) this else copy(hiddenPosts = (hiddenPosts + id).takeLast(HIDDEN_LIMIT))

    /** Every post hidden one by one shown again. */
    fun showingHidden(): ThreadsFeedOptions = copy(hiddenPosts = emptyList())

    /** [typed] muted too, split at commas and line breaks; a word already muted, in any case, is not added twice. */
    fun muting(typed: String): ThreadsFeedOptions {
        val known = mutedWords.map { it.lowercase() }.toMutableSet()
        return copy(mutedWords = mutedWords + MutedKeywords.parseTyped(typed).filter { known.add(it.lowercase()) })
    }

    fun unmuting(word: String): ThreadsFeedOptions = copy(mutedWords = mutedWords - word)

    fun encode(): String = JsonObject(
        mapOf("content" to JsonPrimitive(content.name.lowercase()), "hideReplies" to JsonPrimitive(hideReplies), "hideReposts" to JsonPrimitive(hideReposts)) +
            (if (mutedWords.isEmpty()) emptyMap() else mapOf("mutedWords" to JsonArray(mutedWords.map(::JsonPrimitive)))) +
            (if (mutedAccounts.isEmpty()) emptyMap() else mapOf("mutedAccounts" to JsonArray(mutedAccounts.map(::JsonPrimitive)))) +
            (if (hiddenPosts.isEmpty()) emptyMap() else mapOf("hiddenPosts" to JsonArray(hiddenPosts.map(::JsonPrimitive)))),
    ).toString()

    private fun matches(post: ThreadsPost): Boolean = when (content) {
        ThreadsFeedContent.ALL -> true
        ThreadsFeedContent.MEDIA -> post.hasMedia || post.quoted?.hasMedia == true
        ThreadsFeedContent.LINKS -> hasLink(post)
    }

    private fun muted(post: ThreadsPost): Boolean {
        if (mutedWords.isEmpty()) return false
        val text = readable(post)
        return mutedWords.any { MutedKeywords.matches(text, it) }
    }

    companion object {
        /** How many hidden posts are remembered. */
        const val HIDDEN_LIMIT = 500

        /** A handle as the muted accounts keep it: without its @, trimmed, lower case. */
        fun normalised(handle: String?): String = handle.orEmpty().trim().removePrefix("@").lowercase()

        private val address = Regex("https?://", RegexOption.IGNORE_CASE)

        /** The choices XTA wrote; anything unreadable is the default, everything shown. */
        fun decode(text: String): ThreadsFeedOptions {
            val root = J.parse(text)
            if (root.raw !is JsonObject) return ThreadsFeedOptions()
            return ThreadsFeedOptions(
                content = ThreadsFeedContent.entries.firstOrNull { it.name.lowercase() == root["content"].string } ?: ThreadsFeedContent.ALL,
                hideReplies = root["hideReplies"].boolean == true,
                hideReposts = root["hideReposts"].boolean == true,
                mutedWords = root["mutedWords"].list.mapNotNull { it.string?.trim()?.ifEmpty { null } }.distinct(),
                mutedAccounts = root["mutedAccounts"].list.mapNotNull { normalised(it.string).ifEmpty { null } }.distinct(),
                hiddenPosts = root["hiddenPosts"].list.mapNotNull { it.string?.trim()?.ifEmpty { null } }.distinct().takeLast(HIDDEN_LIMIT),
            )
        }

        /** Whether [post] points off Threads: a preview card, a link Meta resolved in the caption, or a bare address. */
        fun hasLink(post: ThreadsPost): Boolean =
            post.linkCard != null || post.fragments.any { it.kind == ThreadsTextFragment.Kind.LINK } || address.containsMatchIn(post.text)

        /** What a muted word is looked for in: the words on screen, the quote's and the link card's included, and the topic. */
        private fun readable(post: ThreadsPost): String = listOfNotNull(
            post.text,
            post.topicTag,
            post.linkCard?.title,
            post.linkCard?.description,
            post.quoted?.text,
        ).joinToString("\n")
    }
}
