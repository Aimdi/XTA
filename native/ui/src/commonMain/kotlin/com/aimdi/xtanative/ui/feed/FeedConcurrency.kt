package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.core.feed.ChunkConcurrency
import com.aimdi.xtanative.data.AppGraph

/**
 * How many of its searches a feed sends at once: about two per X account
 * left on for the feeds, as XTA sizes it (feed_chunk_concurrency.dart), the
 * accounts turned off counting only while every one is.
 */
fun AppGraph.feedConcurrency(): Int =
    ChunkConcurrency.forAccounts(ChunkConcurrency.usableAccounts(sessions.state.value?.accounts.orEmpty(), accountsOff))
