package com.aimdi.xtanative.ui.plugins

/** How the shared rows put a post's lines together. */
object PostLines {
    /** The time a row shows, with what the network says after it: "5m · Edited". */
    fun time(ago: String, note: String?): String = listOf(ago, note.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
}
