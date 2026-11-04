package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.store.follows.Judgement

/** Members read so far, over the members every source together covers: the header's "12 of 40 members". */
data class DiscoveryCoverage(val read: Int, val total: Int)

/**
 * What a group's Discover has read at one moment (group_discovery.dart's
 * GroupDiscoveryStore): each source's latest answer, how many of its members
 * it read, which failed and why, what an earlier scan found (kept while
 * "Scan more" reads on), and the order the reader's AI gave, once asked.
 */
data class DiscoveryBoard(
    val scanned: Map<String, List<Candidate>> = emptyMap(),
    val read: Map<String, Int> = emptyMap(),
    val failures: Map<String, Throwable> = emptyMap(),
    val kept: List<Candidate> = emptyList(),
    val aiOrder: List<String>? = null,
    val aiFailed: Boolean = false,
) {
    /** Everything any source answered, unmerged. */
    val found: List<Candidate> get() = kept + scanned.values.flatten()

    /** A new scan: "Scan more" keeps what was found ([ranked]) and how far each source read; a load starts over. */
    fun begun(keep: Boolean, ranked: List<Candidate>): DiscoveryBoard =
        DiscoveryBoard(read = if (keep) read else emptyMap(), kept = if (keep) ranked else emptyList())

    /** [source]'s answer so far; a later, smaller sample never makes the group look less read. */
    fun absorbed(source: String, batch: DiscoveryBatch): DiscoveryBoard = copy(
        scanned = scanned + (source to batch.candidates),
        read = read + (source to maxOf(read[source] ?: 0, batch.read)),
        failures = batch.error?.let { failures + (source to it) } ?: failures,
    )

    /** [source] failed or ran out of time, keeping what it already handed over. */
    fun failed(source: String, error: Throwable): DiscoveryBoard = copy(failures = failures + (source to error))

    /** The AI's [order] (null when it gave nothing usable, [failed] when it was asked). */
    fun aiRanked(order: List<String>?, failed: Boolean): DiscoveryBoard = copy(aiOrder = order, aiFailed = failed)

    /** Everything found, merged and ranked, without what [followed] holds. */
    fun ranked(followed: Set<String>, groupName: String, feedback: Map<String, Judgement>): List<Candidate> =
        Discovery.ranked(found, followed, groupName, feedback)

    /** What the list shows of [ranked]: in the AI's order once it gave one, the reader's feedback on top; the hidden and the followed left out. */
    fun visible(ranked: List<Candidate>, followed: Set<String>, feedback: Map<String, Judgement>): List<Candidate> {
        val ordered = aiOrder?.let { Discovery.feedbackOrdered(DiscoveryAi.arranged(ranked, it), feedback) } ?: ranked
        return Discovery.visible(ordered, feedback, followed)
    }

    /** How many of the members each source speaks for ([totals]) have been read. */
    fun coverage(totals: Map<String, Int>): DiscoveryCoverage {
        val total = totals.values.sum()
        return DiscoveryCoverage(read.filterKeys { it in totals }.values.sum().coerceIn(0, total), total)
    }
}
