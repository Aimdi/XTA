package com.aimdi.xtanative.networks

import com.aimdi.xtanative.core.repairs.RepairSlot
import com.aimdi.xtanative.networks.stocks.TickerClient

/** The desktop browser XTA's Instagram and TikTok reads present themselves as, as XTA ships it. */
const val DESKTOP_CHROME =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/**
 * Every value of the networks here that XTA's published `repairs.json` may
 * replace, each declared beside the client that sends it. A client takes its
 * values from the published entries with its slot's `valueIn` (Stocks). A
 * network that became a plugin keeps its slots, and their check, in the plugin.
 */
object NetworkRepairs {
    val SLOTS: List<RepairSlot> =
        TickerClient.REPAIR_SLOTS
}
