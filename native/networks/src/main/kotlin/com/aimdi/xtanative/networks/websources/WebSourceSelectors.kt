package com.aimdi.xtanative.networks.websources

import com.aimdi.xtanative.networks.rss.JsSpace
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Collector
import org.jsoup.select.Evaluator
import org.jsoup.select.QueryParser

/** What an HTML rule reads: a selector inside the post (empty: the post itself) and the attribute to take (none: its text; `html`: its markup). */
data class HtmlRule(val selector: String, val attribute: String?)

/**
 * The HTML half of a web source's rules: CSS selectors into a page, read with
 * jsoup. A selector that matches nothing, or that cannot be read, gives no
 * value: a page that changed shape gives fewer posts, never a throw.
 *
 * Sources are shared with XTA, so only the CSS XTA reads is accepted (see
 * docs/web-sources.md): tags, classes, ids, attributes with `=`, `^=`, `$=`
 * and `*=`, the combinators, `:first-child`, `:last-child`, `:only-child`,
 * `:empty`, `:not(…)` and `:nth-child(n)`. jsoup reads more (`:has()`,
 * `:contains()`, regular expressions in `~=` and `:matches()`), and a
 * source using it is refused, as XTA refuses it: no source can make the app
 * run a pattern. XTA's own reader also took `~=` and `|=` as CSS has them,
 * and `:link`, `:visited`, `:blank` and `:lang()`, none of which the guide
 * names: jsoup has no such `|=` and makes `~=` a pattern, so they are
 * refused here. XTA tried a selector on a sample page and so accepted an
 * unsupported part the sample never reached; here every part is checked.
 * Selectors are matched as XTA matches them, ancestors outside the post
 * included, with jsoup's own semantics within: `:nth-child` counts elements
 * from 1, and classes and attribute values match without regard to case.
 */
object WebSourceSelectors {
    private val attributeName = Regex("^[A-Za-z_:][-A-Za-z0-9_:.]*$")
    private val pseudoName = Regex("[-A-Za-z0-9_]+")
    private val nthArgument = Regex("""\(\s*[+-]?\d+\s*\)""")
    private val plainPseudoClasses = setOf("first-child", "last-child", "only-child", "empty")

    /** [rule] as `selector`, `selector@attribute`, `@attribute` or `selector@html`; an `@` inside a selector stays in it. */
    fun htmlRule(rule: String): HtmlRule {
        val at = rule.lastIndexOf('@')
        if (at < 0 || !attributeName.matches(rule.substring(at + 1))) return HtmlRule(rule.trim(), null)
        return HtmlRule(rule.substring(0, at).trim(), rule.substring(at + 1))
    }

    /** Whether [selector] is CSS a source may use; empty means the post itself. */
    fun isCssSelector(selector: String): Boolean = selector.isEmpty() || evaluator(selector) != null

    /** [selector] read by jsoup, or null when it is not CSS a source may use. */
    internal fun evaluator(selector: String): Evaluator? =
        if (withinXtasCss(selector)) runCatching { QueryParser.parse(selector) }.getOrNull() else null

    /** The elements under [root] matching [selector], in page order; none when it cannot be read. */
    fun selectAll(root: Element, selector: String): List<Element> = HtmlQuery(root.root() as? Element ?: root).selectAll(root, selector)

    /** Every value [rule] reads inside [item], in page order. */
    fun valuesAt(item: Element, rule: String): List<String> = HtmlQuery(item.root() as? Element ?: item).valuesAt(item, rule)

    /**
     * Whether [selector] keeps to the CSS XTA reads. Quoted text and attribute
     * values are skipped; what is left must name only known pseudo-classes,
     * no namespace, no jsoup-only attribute test, and no leading combinator.
     */
    private fun withinXtasCss(selector: String): Boolean {
        var i = 0
        var groupStart = true
        while (i < selector.length) {
            val c = selector[i]
            if (groupStart && !JsSpace.isSpace(c)) {
                if (c in ">+~") return false
                groupStart = false
            }
            i = when (c) {
                '\\' -> i + 2
                '"', '\'' -> selector.indexOf(c, i + 1).takeIf { it > 0 }?.plus(1) ?: return false
                '[' -> attributeEnd(selector, i) ?: return false
                ':' -> pseudoEnd(selector, i) ?: return false
                '|' -> return false
                ',' -> {
                    groupStart = true
                    i + 1
                }
                else -> i + 1
            }
        }
        return true
    }

    /** Past the attribute test opening at [start], or null when it uses an operator XTA does not read (`~=`, `|=`, `!=`, `[^…]`). */
    private fun attributeEnd(selector: String, start: Int): Int? {
        var i = start + 1
        var quote: Char? = null
        while (i < selector.length) {
            val c = selector[i]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == ']' -> break
            }
            i++
        }
        if (i >= selector.length) return null
        val inside = selector.substring(start + 1, i)
        val operator = inside.indexOf('=').takeIf { it > 0 }?.let { inside[it - 1] }
        val refused = inside.trimStart().startsWith('^') || operator == '~' || operator == '|' || operator == '!'
        return if (refused) null else i + 1
    }

    /** Past the pseudo-class at [start], or null when it is one XTA does not read. */
    private fun pseudoEnd(selector: String, start: Int): Int? {
        val name = pseudoName.matchAt(selector, start + 1)?.value?.lowercase() ?: return null
        val end = start + 1 + name.length
        return when (name) {
            in plainPseudoClasses -> end
            "not" -> end.takeIf { selector.getOrNull(end) == '(' }
            "nth-child" -> nthArgument.matchAt(selector, end)?.let { end + it.value.length }
            else -> null
        }
    }
}

/**
 * The selectors of one page, each run once over the whole document and its
 * matches kept: a page of a hundred posts reads every field rule once, not
 * once per post. Matches are then taken from inside each post.
 */
internal class HtmlQuery(private val document: Element) {
    private val matched = HashMap<String, Set<Element>>()

    init {
        (document as? Document)?.outputSettings()?.prettyPrint(false)
    }

    /** The elements inside [scope] (not [scope] itself) matching [selector], in page order. */
    fun selectAll(scope: Element, selector: String): List<Element> {
        val hits = matched.getOrPut(selector) { evaluate(selector) }
        return if (hits.isEmpty()) emptyList() else scope.allElements.drop(1).filter { it in hits }
    }

    /** Every value [rule] reads inside [item]: text with spaces collapsed, an attribute, or `html` markup. */
    fun valuesAt(item: Element, rule: String): List<String> {
        val (selector, attribute) = WebSourceSelectors.htmlRule(rule)
        val elements = if (selector.isEmpty()) listOf(item) else selectAll(item, selector)
        return elements.mapNotNull { value(it, attribute)?.ifEmpty { null } }
    }

    private fun value(element: Element, attribute: String?): String? = when (attribute) {
        null -> JsSpace.collapse(element.text())
        "html" -> element.html().trim()
        else -> if (element.hasAttr(attribute)) element.attr(attribute).trim() else null
    }

    private fun evaluate(selector: String): Set<Element> {
        val evaluator = WebSourceSelectors.evaluator(selector) ?: return emptySet()
        return runCatching { Collector.collect(evaluator, document).toSet() }.getOrDefault(emptySet())
    }
}
