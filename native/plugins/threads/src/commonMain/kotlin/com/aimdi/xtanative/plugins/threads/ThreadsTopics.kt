package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsCommunities
import com.aimdi.xtanative.networks.threads.ThreadsDiscovery
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsTopic
import com.aimdi.xtanative.store.StoreFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The topics and communities followed on this device, newest first, in the
 * plugin's folder: their posts come to Home and lead For you's topics.
 * Nothing is followed on Threads. Null until the file has been read.
 */
internal class ThreadsTopics(private val file: StoreFile) {
    private val mutable = MutableStateFlow<List<ThreadsTopic>?>(null)
    val state: StateFlow<List<ThreadsTopic>?> = mutable.asStateFlow()

    private val mutex = Mutex()

    suspend fun load(): List<ThreadsTopic> = mutex.withLock { loaded() }

    /** Follows [topic] here, or keeps its newer mark when it is followed already. */
    suspend fun follow(topic: ThreadsTopic) = change { with(it, topic) }

    suspend fun unfollow(name: String) = change { without(it, name) }

    private suspend fun change(edit: (List<ThreadsTopic>) -> List<ThreadsTopic>) = mutex.withLock {
        val next = edit(loaded())
        file.write(encode(next))
        mutable.value = next
    }

    private suspend fun loaded(): List<ThreadsTopic> = mutable.value ?: decode(file.read()).also { mutable.value = it }

    companion object {
        /** At most this many are kept: each is a page Home reads now and then. */
        const val CAP = 50

        fun key(name: String): String = name.trim().removePrefix("#").trim().lowercase()

        fun follows(topics: List<ThreadsTopic>?, name: String): Boolean = topics.orEmpty().any { it.key == key(name) }

        /** [topics] with [topic] first; one followed already keeps its place and takes the marks it lacked. */
        fun with(topics: List<ThreadsTopic>, topic: ThreadsTopic): List<ThreadsTopic> {
            val key = key(topic.name)
            if (key.isEmpty()) return topics
            val held = topics.firstOrNull { it.key == key }
                ?: return (listOf(topic.copy(name = topic.name.trim().removePrefix("#"))) + topics).take(CAP)
            val merged = held.copy(
                emoji = topic.emoji ?: held.emoji,
                iconUrl = topic.iconUrl ?: held.iconUrl,
                community = topic.community || held.community,
                url = topic.url ?: held.url,
            )
            return topics.map { if (it.key == key) merged else it }
        }

        fun without(topics: List<ThreadsTopic>, name: String): List<ThreadsTopic> = topics.filterNot { it.key == key(name) }

        /** The topic or community [post] was posted in, as its card names it; null for a post in none. */
        fun of(post: ThreadsPost): ThreadsTopic? = ThreadsDiscovery.topicOf(post)

        fun encode(topics: List<ThreadsTopic>): String = JsonArray(
            topics.map { topic ->
                JsonObject(
                    mapOf(
                        "name" to JsonPrimitive(topic.name),
                        "emoji" to (topic.emoji?.let(::JsonPrimitive) ?: JsonNull),
                        "iconUrl" to (topic.iconUrl?.let(::JsonPrimitive) ?: JsonNull),
                        "community" to JsonPrimitive(topic.community),
                        "url" to (topic.url?.let(::JsonPrimitive) ?: JsonNull),
                    ),
                )
            },
        ).toString()

        /** What [text] keeps; anything unreadable is nothing followed. */
        fun decode(text: String?): List<ThreadsTopic> = J.parse(text.orEmpty()).list.mapNotNull { row ->
            val name = row["name"].string?.trim()?.ifEmpty { null } ?: return@mapNotNull null
            ThreadsTopic(name, row["emoji"].string, row["iconUrl"].string?.takeIf { it.startsWith("https://") }, row["community"].boolean == true, row["url"].string?.let(ThreadsCommunities::threadsPage))
        }.distinctBy { it.key }.take(CAP)
    }
}
