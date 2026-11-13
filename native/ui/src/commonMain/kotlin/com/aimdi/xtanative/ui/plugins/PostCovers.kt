package com.aimdi.xtanative.ui.plugins

/**
 * A post's content warning as its network draws it
 * ([NetworkPostExtras.cover]) in place of the shared row's line: the
 * [warning]'s words, whether it keeps the text back too ([coversText]) or
 * the media alone, whether the reader has opened it ([revealed]), and what
 * opens it or covers it again ([toggle]).
 */
class PostCover(val warning: String, val coversText: Boolean, val revealed: Boolean, val toggle: () -> Unit)

/** What a warning leaves drawn, as XTA's cards decide it. */
object PostCovers {
    /**
     * Whether the media under a warning is drawn: under the shared row's line
     * it waits blurred, a tap away; under a network's own cover ([ownCover])
     * nothing is drawn until the reader opens it, as XTA's covers mount
     * nothing they protect.
     */
    fun drawsMedia(warned: Boolean, revealed: Boolean, ownCover: Boolean): Boolean = !warned || revealed || !ownCover
}
