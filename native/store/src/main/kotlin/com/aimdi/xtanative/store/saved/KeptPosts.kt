package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.replaceWith
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What [KeptPosts] needs to know of one store's value [T]: its posts [P], in
 * order, and whatever else it keeps beside them [X] (the folders of Saved).
 */
internal interface KeptShape<T : Any, P : Any, X> {
    val empty: T
    fun posts(value: T): List<P>
    fun extra(value: T): X
    fun of(posts: List<P>, extra: X): T
    fun id(post: P): String
    fun content(post: P): JsonElement?
    fun withContent(post: P, content: JsonElement?): P

    /** [post] as a row of the index: everything but its content. */
    fun row(post: P): JsonObject
    fun post(row: J): P?
    fun extraJson(extra: X): JsonElement
    fun extraOf(json: J): X

    /** Two values' extras as one, [first]'s winning where both have the same. */
    fun mergeExtra(first: X, second: X): X

    /** The single file an earlier build kept everything in; null when it cannot be read. */
    fun legacy(root: J): T?
}

/**
 * What one change makes of the posts: [fresh] is the id of the post whose
 * content it sets, to what that post now carries; [alsoFresh] the ids of
 * more such posts (those an undo puts back).
 */
internal class Edit<T, R>(val next: T, val result: R, val fresh: String? = null, val alsoFresh: Collection<String> = emptyList())

/**
 * Posts kept on the device so that one like, save, note or move writes about
 * as much as that one post, however many are kept. Under `saved/`:
 *
 * - `<name>.index.json`: every post without its content, in order, and what
 *   is kept beside them. Rewritten whole only now and then.
 * - `<name>.log`: the changes since the index was written, one line each,
 *   appended and flushed. It belongs to the index of the same `gen`; a line
 *   a crash cut short is a change that never happened.
 * - `<name>/<post>.json`: one post's content, written before the row that
 *   says it is there and deleted after the row is gone.
 *
 * `<name>.json`, the one file earlier builds kept everything in, is read
 * when found, written out as the above and only then deleted, so a crash at
 * any point leaves either it or a whole index. What it holds wins over an
 * index found beside it: only an older build can have written it since.
 *
 * [bare] is read at launch and holds no content; [whole] reads every
 * content file the first time a screen asks, and both follow each change.
 * Given a [scope], [whole] lets go of the content once nothing has watched
 * it for [releaseAfterMillis], and reads it again as soon as something
 * watches it again (null in between, as before the first read). A
 * change is on the disk before it is shown, and a failed write leaves both
 * as they were. An index that cannot be read is kept aside with its
 * contents (`.unreadable`) before anything replaces it.
 */
internal class KeptPosts<T : Any, P : Any, X>(
    dir: StoreDir,
    private val name: String,
    private val shape: KeptShape<T, P, X>,
    scope: CoroutineScope? = null,
    private val releaseAfterMillis: Long = RELEASE_AFTER_MILLIS,
) {
    private class Kept<P, X>(val posts: List<P>, val extra: X, val stored: Set<String>)

    private sealed interface Op<out P, out X> {
        class Top<P>(val post: P, val stored: Boolean) : Op<P, Nothing>
        class Put<P>(val post: P, val stored: Boolean) : Op<P, Nothing>
        class Del(val id: String) : Op<Nothing, Nothing>
        class Extra<X>(val extra: X) : Op<Nothing, X>
    }

    private val lock = Mutex()
    private val folder = File(dir.root, "saved")
    private val legacyFile = File(folder, "$name.json")
    private val indexFile = File(folder, "$name.index.json")
    private val logFile = File(folder, "$name.log")
    private val contentDir = File(folder, name)

    // All below are read and written under the lock.
    private var kept: Kept<P, X>? = null
    private var gen = 0L
    private var logged = 0
    /** Whether a change may be appended to the log: the index on the disk is this [gen]'s and the log is whole. */
    private var journal = false
    /** The earlier build's file is still the only whole copy on the disk: every change writes everything out. */
    private var legacyPending = false

    private val bareState = MutableStateFlow<T?>(null)
    private val wholeState = MutableStateFlow<T?>(null)

    /** Every post without its content; null until first read. */
    val bare: StateFlow<T?> = bareState.asStateFlow()

    /** Every post with its content; null until [whole] first reads them, and while let go unwatched. */
    val whole: StateFlow<T?> = wholeState.asStateFlow()

    /** [whole] was let go while unwatched: the next watcher has it read again. */
    @Volatile
    private var released = false

    init {
        scope?.launch { releaseWhileUnwatched() }
    }

    private suspend fun releaseWhileUnwatched() {
        wholeState.subscriptionCount.map { it > 0 }.distinctUntilChanged().collectLatest { watched ->
            when {
                !watched -> {
                    delay(releaseAfterMillis)
                    locked { release() }
                }
                released -> try {
                    whole()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Unreadable now: the watcher sees null, as before a first read.
                }
            }
        }
    }

    /** Drops the content, unless the earlier build's file is still the only copy of it. */
    private fun release() {
        if (legacyPending || wholeState.value == null) return
        released = true
        wholeState.value = null
    }

    suspend fun bare(): T = locked { loaded(); bareState.value!! }

    suspend fun whole(): T = locked { wholeNow() }

    /** The content kept of post [id], read from its own file; null when none is. */
    suspend fun content(id: String): JsonElement? = locked { contentNow(id) }

    private fun contentNow(id: String): JsonElement? {
        val now = loaded()
        val known = wholeState.value?.let { all -> shape.posts(all).firstOrNull { shape.id(it) == id } }
        return when {
            known != null -> shape.content(known)
            id in now.stored -> readContent(id)
            else -> null
        }
    }

    /**
     * A change to the rows: [change] gets the posts without content and gives
     * them back changed, the same instance for no change. Only [Edit.fresh]
     * may carry content; a post it leaves out loses the content kept of it.
     */
    suspend fun <R> edit(change: (T) -> Edit<T, R>): R = locked {
        val old = loaded()
        val before = bareState.value!!
        val edit = change(before)
        if (edit.next !== before) commit(old, edit)
        edit.result
    }

    /**
     * [edit], where [change] may also read the content kept of a post (each
     * read from its own file, as [content]): what a removal takes for its
     * undo, without reading every post's.
     */
    suspend fun <R> editReading(change: (T, content: (String) -> JsonElement?) -> Edit<T, R>): R = locked {
        val old = loaded()
        val before = bareState.value!!
        val edit = change(before, ::contentNow)
        if (edit.next !== before) commit(old, edit)
        edit.result
    }

    /** A change that needs or replaces everything: [merge] gets every post with its content and gives what to keep, null for no change. */
    suspend fun rewrite(merge: (T) -> T?) = locked {
        val before = wholeNow()
        val next = merge(before)
        if (next != null && next !== before) {
            publish(persistWhole(next, before), next)
        }
    }

    private suspend fun <R> locked(block: () -> R): R = lock.withLock { withContext(Dispatchers.IO) { block() } }

    private fun <R> commit(old: Kept<P, X>, edit: Edit<T, R>) {
        val given = shape.posts(edit.next)
        val freshIds = (listOfNotNull(edit.fresh) + edit.alsoFresh).toSet()
        // The fresh posts that are there, each with the content it now carries (null: none kept).
        val fresh: Map<String, JsonElement?> = given.filter { shape.id(it) in freshIds }.associate { shape.id(it) to shape.content(it) }
        val posts = given.map { if (shape.content(it) == null) it else shape.withContent(it, null) }
        val ids = posts.mapTo(HashSet()) { shape.id(it) }
        val stored = old.stored.filterTo(HashSet()) { it in ids }
        fresh.forEach { (id, content) -> if (content != null) stored += id else stored -= id }
        val next = Kept(posts, shape.extra(edit.next), stored)
        val wholeNext = wholeState.value?.let { all -> wholeAfter(old, all, next, fresh) }
        if (legacyPending) {
            persistWhole(wholeNext ?: wholeOf(next), null)
        } else {
            fresh.forEach { (id, content) -> if (content != null) contentFile(id).replaceWith(content.toString()) }
            persist(old, next)
            old.stored.filter { it !in stored }.forEach { contentFile(it).delete() }
        }
        publish(next, wholeNext)
    }

    private fun publish(next: Kept<P, X>, wholeNext: T?) {
        kept = next
        bareState.value = shape.of(next.posts, next.extra)
        if (wholeNext != null) wholeState.value = wholeNext
    }

    /** [all] after a change, keeping the instance of every post the change left alone, so what was worked out of it stays. */
    private fun wholeAfter(old: Kept<P, X>, all: T, next: Kept<P, X>, fresh: Map<String, JsonElement?>): T {
        val was = old.posts.zip(shape.posts(all)).associateBy { shape.id(it.first) }
        return shape.of(next.posts.map { post ->
            val id = shape.id(post)
            val pair = was[id]
            when {
                id in fresh -> shape.withContent(post, fresh[id])
                pair == null -> post
                pair.first === post -> pair.second
                else -> shape.withContent(post, shape.content(pair.second))
            }
        }, next.extra)
    }

    private fun loaded(): Kept<P, X> = kept ?: load()

    private fun wholeNow(): T {
        val now = loaded()
        return wholeState.value ?: wholeOf(now).also {
            released = false
            wholeState.value = it
        }
    }

    private fun wholeOf(state: Kept<P, X>): T =
        shape.of(state.posts.map { post -> shape.id(post).let { if (it in state.stored) shape.withContent(post, readContent(it)) else post } }, state.extra)

    // Reading what is on the disk.

    private fun load(): Kept<P, X> {
        val indexed = readIndex()
        val legacyText = legacyFile.takeIf { it.isFile }?.readText()
        if (legacyText == null) return (indexed ?: bareOf(shape.empty)).also { publish(it, null) }
        val legacy = shape.legacy(J.parse(legacyText)) ?: shape.empty.also { File(folder, "$name.json.unreadable").replaceWith(legacyText) }
        val all = if (indexed == null) legacy else merged(legacy, wholeOf(indexed))
        legacyPending = true
        // Kept in memory whole until it is written out: until then the old file is the only copy.
        val written = runCatching { persistWhole(all, null) }.getOrNull()
        return written?.also { publish(it, null) } ?: bareOf(all).also { publish(it, all) }
    }

    /** The index with its log applied; null when there is none or it cannot be read. */
    private fun readIndex(): Kept<P, X>? {
        val text = indexFile.takeIf { it.isFile }?.readText()
        val root = text?.let(J::parse)
        if (root == null || root["v"].int != INDEX_VERSION) {
            if (text != null) setAside(text)
            logFile.delete()
            return null
        }
        gen = root["gen"].long ?: 0
        val rows = root["posts"].list.mapNotNull(::row).distinctBy { shape.id(it.first) }
        val index = Kept(rows.map { it.first }, shape.extraOf(root["extra"]), rows.filter { it.second }.mapTo(HashSet()) { shape.id(it.first) })
        val lines = logFile.takeIf { it.isFile }?.readLines()?.filter { it.isNotBlank() }
        if (lines == null) return index.also { journal = !logFile.exists() }
        if (lines.firstOrNull()?.let { J.parse(it)["gen"].long } != gen) {
            // The log of an index since replaced: what it says is in the index already.
            logFile.delete()
            return index.also { journal = !logFile.exists() }
        }
        val batches = lines.drop(1).map(::batch)
        val state = batches.filterNotNull().fold(index, ::applied)
        logged = batches.size
        journal = batches.none { it == null }
        // A line cut short by a crash would garble the next one appended.
        if (!journal) runCatching { compact(state) }
        return state
    }

    /** Keeps an index this build cannot read, and the contents it lists, out of the way of what is written next. */
    private fun setAside(text: String) {
        File(folder, "$name.index.json.unreadable").replaceWith(text)
        if (!contentDir.exists()) return
        val aside = generateSequence(0) { it + 1 }
            .map { File(folder, if (it == 0) "$name.unreadable" else "$name.unreadable.$it") }
            .first { !it.exists() }
        contentDir.renameTo(aside)
    }

    private fun readContent(id: String): JsonElement? =
        contentFile(id).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }?.let { J.parse(it).raw }

    /** [legacy] with what [index] holds besides: the posts and extras only the index has follow. */
    private fun merged(legacy: T, index: T): T {
        val ids = shape.posts(legacy).mapTo(HashSet()) { shape.id(it) }
        val rest = shape.posts(index).filter { shape.id(it) !in ids }
        return shape.of(shape.posts(legacy) + rest, shape.mergeExtra(shape.extra(legacy), shape.extra(index)))
    }

    // Writing.

    private fun bareOf(all: T): Kept<P, X> {
        val posts = shape.posts(all)
        return Kept(
            posts.map { if (shape.content(it) == null) it else shape.withContent(it, null) },
            shape.extra(all),
            posts.filter { shape.content(it) != null }.mapTo(HashSet()) { shape.id(it) },
        )
    }

    /**
     * Writes [all] out whole: the contents that differ from [before]'s first,
     * then the index, then the earlier build's file goes. Until the index is
     * replaced the disk still reads as it did.
     */
    private fun persistWhole(all: T, before: T?): Kept<P, X> {
        val next = bareOf(all)
        val had = before?.let { shape.posts(it) }.orEmpty().associateBy { shape.id(it) }
        val changed = shape.posts(all).mapNotNull { post ->
            val content = shape.content(post) ?: return@mapNotNull null
            val id = shape.id(post)
            if (had[id]?.let(shape::content) == content && contentFile(id).isFile) null else contentFile(id) to content.toString()
        }
        writeAll(changed)
        compact(next)
        runCatching { legacyFile.delete() }
        legacyPending = false
        return next
    }

    /** Writes every file, flushes every one, then gives each its name: far fewer waits for the disk than one flush per write. */
    private fun writeAll(files: List<Pair<File, String>>) {
        if (files.isEmpty()) return
        contentDir.mkdirs()
        val parts = files.map { (file, text) -> File(contentDir, file.name + PART).also { it.writeText(text) } to file }
        parts.forEach { (part, _) -> FileChannel.open(part.toPath(), StandardOpenOption.WRITE).use { it.force(true) } }
        parts.forEach { (part, file) ->
            if (!part.renameTo(file)) {
                file.delete()
                part.renameTo(file)
            }
        }
    }

    private fun persist(old: Kept<P, X>, next: Kept<P, X>) {
        val ops = if (journal && logged < MAX_BATCHES) changes(old, next) else null
        when {
            ops == null -> compact(next)
            ops.isNotEmpty() -> append(ops)
        }
    }

    private fun append(ops: List<Op<P, X>>) {
        val head = if (logFile.exists()) "" else buildJsonObject { put("gen", gen) }.toString()
        val line = buildJsonObject { put("ops", JsonArray(ops.map(::opJson))) }.toString()
        try {
            FileOutputStream(logFile, true).use {
                it.write("$head\n$line\n".toByteArray())
                it.fd.sync()
            }
            logged++
        } catch (failure: Exception) {
            journal = false
            throw failure
        }
    }

    /** Writes the index whole as the next `gen`, which retires the log, then clears away content files no row names. */
    private fun compact(next: Kept<P, X>) {
        val following = gen + 1
        indexFile.replaceWith(buildJsonObject {
            put("v", INDEX_VERSION)
            put("gen", following)
            put("extra", shape.extraJson(next.extra))
            put("posts", JsonArray(next.posts.map { rowJson(it, shape.id(it) in next.stored) }))
        }.toString())
        gen = following
        logged = 0
        logFile.delete()
        journal = !logFile.exists()
        runCatching {
            val named = next.stored.mapTo(HashSet()) { fileName(it) }
            contentDir.listFiles()?.filter { it.isFile && it.name !in named }?.forEach { it.delete() }
        }
    }

    // The log's lines.

    /** What turns [old] into [next] in a few lines of log; empty when the rows are the same, null when it takes the whole index. */
    private fun changes(old: Kept<P, X>, next: Kept<P, X>): List<Op<P, X>>? {
        if (same(old, next)) return emptyList()
        val ops = ArrayList<Op<P, X>>()
        if (old.extra != next.extra) ops += Op.Extra(next.extra)
        val ids = next.posts.mapTo(HashSet()) { shape.id(it) }
        old.posts.forEach { if (shape.id(it) !in ids) ops += Op.Del(shape.id(it)) }
        if (ops.size > MAX_OPS) return null
        val before = old.posts.associateBy { shape.id(it) }
        val first = next.posts.firstOrNull()
        val moved = first != null && old.posts.firstOrNull { shape.id(it) in ids }?.let { shape.id(it) } != shape.id(first)
        if (first != null && moved) ops += Op.Top(first, shape.id(first) in next.stored)
        next.posts.drop(if (moved) 1 else 0).forEach { post ->
            val id = shape.id(post)
            val was = before[id] ?: return null
            if (was != post || (id in old.stored) != (id in next.stored)) ops += Op.Put(post, id in next.stored)
            if (ops.size > MAX_OPS) return null
        }
        return ops.takeIf { same(applied(old, it), next) }
    }

    private fun same(one: Kept<P, X>, other: Kept<P, X>) = one.posts == other.posts && one.extra == other.extra && one.stored == other.stored

    private fun applied(state: Kept<P, X>, ops: List<Op<P, X>>): Kept<P, X> = ops.fold(state) { now, op ->
        when (op) {
            is Op.Extra -> Kept(now.posts, op.extra, now.stored)
            is Op.Del -> Kept(now.posts.filterNot { shape.id(it) == op.id }, now.extra, now.stored - op.id)
            is Op.Top -> shape.id(op.post).let { id ->
                Kept(listOf(op.post) + now.posts.filterNot { shape.id(it) == id }, now.extra, if (op.stored) now.stored + id else now.stored - id)
            }
            is Op.Put -> shape.id(op.post).let { id ->
                if (now.posts.none { shape.id(it) == id }) now
                else Kept(now.posts.map { if (shape.id(it) == id) op.post else it }, now.extra, if (op.stored) now.stored + id else now.stored - id)
            }
        }
    }

    private fun rowJson(post: P, stored: Boolean): JsonObject =
        if (stored) JsonObject(shape.row(post) + (HAS_CONTENT to JsonPrimitive(true))) else shape.row(post)

    private fun row(json: J): Pair<P, Boolean>? = shape.post(json)?.let { it to (json[HAS_CONTENT].boolean == true) }

    private fun opJson(op: Op<P, X>): JsonObject = buildJsonObject {
        when (op) {
            is Op.Top -> put("top", rowJson(op.post, op.stored))
            is Op.Put -> put("put", rowJson(op.post, op.stored))
            is Op.Del -> put("del", op.id)
            is Op.Extra -> put("extra", shape.extraJson(op.extra))
        }
    }

    /** One line of the log; null when it was cut short or is not one this build writes. */
    private fun batch(line: String): List<Op<P, X>>? {
        val root = J.parse(line)
        if (root["ops"].raw !is JsonArray) return null
        return root["ops"].list.map { op ->
            when {
                op["top"].raw != null -> row(op["top"])?.let { Op.Top(it.first, it.second) }
                op["put"].raw != null -> row(op["put"])?.let { Op.Put(it.first, it.second) }
                op["del"].string != null -> Op.Del(op["del"].string!!)
                op["extra"].raw != null -> Op.Extra(shape.extraOf(op["extra"]))
                else -> null
            } ?: return null
        }
    }

    private fun contentFile(id: String) = File(contentDir, fileName(id))

    /** A file name no two ids share and none can leave the folder with: X's numeric ids as they are, any other as its hash. */
    private fun fileName(id: String): String = (if (id.length <= 40 && id.isNotEmpty() && id.all { it in '0'..'9' }) id else "x" + sha256(id)) + ".json"

    private companion object {
        const val INDEX_VERSION = 2
        const val HAS_CONTENT = "c"

        /** A minute: long enough to go to a post and come back, short enough not to keep every saved post in memory in the tray. */
        const val RELEASE_AFTER_MILLIS = 60_000L
        const val PART = ".part"

        /** How many changes the log takes before the index is written whole again. */
        const val MAX_BATCHES = 128

        /** A change of more rows than this writes the index whole instead of a long line. */
        const val MAX_OPS = 16
    }
}
