package com.aimdi.xtanative.ui

import java.net.URLEncoder

/**
 * Going forward again, as a browser does: the screens left by going back
 * (Alt+←, Esc, the mouse's back button, a card dragged away, the arrow in
 * the bar), newest last, for Alt+→ and the mouse's forward button to open
 * again one at a time. Going anywhere else (a post clicked, Alt+Home's jump
 * over several screens) forgets them, as a browser forgets its forward
 * history on a new page.
 *
 * It follows the screen on top ([shown]): one back is the entry below the
 * last becoming the top. A screen is kept as a [Step], its route with its
 * arguments filled in ([routeOf]) and the route pattern it lands on, which
 * the screen forward opens must show again.
 */
class ForwardRoutes(private val limit: Int = LIMIT) {
    /** A screen to open again: [route] to navigate to, [pattern] the destination it lands on. */
    data class Step(val route: String, val pattern: String)

    /** The entry on top: its [id], its [pattern], what opens it again ([step]; null where nothing can), and the entry [below] it. */
    private data class Top(val id: String, val pattern: String, val step: Step?, val below: String?)

    private val steps = ArrayDeque<Step>()

    private var top: Top? = null

    /** The pattern of the screen forward is opening, until it is shown. */
    private var opening: String? = null

    val canGoForward: Boolean get() = steps.isNotEmpty() && opening == null

    /** The screen to open going forward, or null when there is none (or one is still opening). */
    fun next(): Step? {
        if (!canGoForward) return null
        return steps.removeLast().also { opening = it.pattern }
    }

    /**
     * The entry [id], on the destination [pattern], is the one on top now,
     * over the entry [below]; [step] opens it again.
     */
    fun shown(id: String, pattern: String, step: Step?, below: String?) {
        val was = top
        top = Top(id, pattern, step, below)
        when {
            was == null || id == was.id -> Unit
            // One back: the screen left is the next forward, unless it cannot be opened again.
            id == was.below -> {
                opening = null
                if (was.step == null) steps.clear() else steps.addLast(was.step)
                while (steps.size > limit) steps.removeFirst()
            }
            opening != null && pattern == opening && below == was.id -> opening = null
            else -> clear()
        }
    }

    /** Forgets every screen to go forward to. */
    fun clear() {
        steps.clear()
        opening = null
    }

    companion object {
        /** More than anyone goes back in a row. */
        const val LIMIT = 50

        /**
         * The route that opens [pattern] ("post/{id}?media={media}") with
         * [args] again: each placeholder of the path filled in, each of the
         * query only where it has a value. Null when the path needs an
         * argument that is missing.
         */
        fun routeOf(pattern: String, args: Map<String, Any?>): String? {
            val path = pattern.substringBefore('?')
            val query = pattern.substringAfter('?', "")
            val filledPath = StringBuilder()
            var at = 0
            while (true) {
                val open = path.indexOf('{', at)
                if (open < 0) break
                val close = path.indexOf('}', open)
                if (close < 0) break
                filledPath.append(path, at, open)
                val value = args[path.substring(open + 1, close)] ?: return null
                filledPath.append(encoded(value.toString()))
                at = close + 1
            }
            filledPath.append(path.substring(at))
            val params = query.split('&').filter { it.isNotEmpty() }.mapNotNull { param ->
                val name = param.substringBefore('=')
                val value = param.substringAfter('=', "")
                if (value.startsWith('{') && value.endsWith('}')) {
                    args[value.substring(1, value.length - 1)]?.let { "$name=" + encoded(it.toString()) }
                } else {
                    param
                }
            }
            return filledPath.toString() + if (params.isEmpty()) "" else "?" + params.joinToString("&")
        }

        private fun encoded(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
    }
}
