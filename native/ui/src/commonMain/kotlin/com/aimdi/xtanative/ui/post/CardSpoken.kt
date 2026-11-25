package com.aimdi.xtanative.ui.post

/**
 * What a screen reader offers on a post's card beside opening it, as the
 * card's actions: what its gestures do (a double-tap likes, a long press
 * opens the ⋯ menu) and what its buttons do, so a reader need not walk to
 * each button of each post. In the order the card shows them: the author at
 * the top, the heart, the bookmark and Share under the post, the menu last.
 */
enum class CardSpoken {
    AUTHOR,
    LIKE,
    SAVE,
    SHARE,
    MENU,
    ;

    companion object {
        /** The actions of a card; the author's profile only where the card [showsAuthor] (not in non-confirmation-bias mode, nor on their own profile). */
        fun of(showsAuthor: Boolean): List<CardSpoken> = entries.filter { it != AUTHOR || showsAuthor }
    }
}
