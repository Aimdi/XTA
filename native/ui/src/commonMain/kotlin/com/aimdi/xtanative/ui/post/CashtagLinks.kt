package com.aimdi.xtanative.ui.post

/** A cashtag in a post's text as its link is tagged (`$AAPL`), and as its sheet names it. */
internal object CashtagLinks {
    /** The link's tag for [symbol], without its `$`. */
    fun tag(symbol: String): String = "$$symbol"

    /** The symbol a link's [tag] names; null for a link that is no cashtag (a mention's `@`, a hashtag's `#`, an address). */
    fun symbolOf(tag: String?): String? = tag?.takeIf { it.length > 1 && it.startsWith('$') }?.substring(1)

    /** The sheet's first entry, as XTA's names it: the cashtag upper-cased. */
    fun title(symbol: String): String = tag(symbol.uppercase())
}
