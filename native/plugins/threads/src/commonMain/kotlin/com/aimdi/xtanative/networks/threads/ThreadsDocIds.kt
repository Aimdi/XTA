package com.aimdi.xtanative.networks.threads

/** A read threads.com's client names and the doc id its bundle asks it by. */
data class ThreadsOperation(val name: String, val docId: String)

/**
 * Finds the doc ids of threads.com's Relay queries in the text of its
 * JavaScript bundles. A page's HTML names a doc id only for the queries it
 * preloads ([ThreadsNextPages]); every other read (deeper replies, About
 * this profile, trending topics) is asked by an id that lives in a bundle,
 * in one of the shapes Meta's build writes:
 *
 * - a module of the id alone: `__d("BarcelonaXQuery_threadsRelayOperation",[],(function(…){e.exports="123…"}),null)`,
 *   whatever follows the name (`_instagramRelayOperation`, `$Parameters`);
 * - the request's parameters: `params:{id:"123…",metadata:{},name:"BarcelonaXQuery",operationKind:"query"}`.
 *
 * Only queries are read. A mutation or a subscription is never returned,
 * under any shape: XTA reads Threads and writes nothing to it. Nothing here
 * throws; text in another shape has no operations.
 */
object ThreadsDocIds {
    private const val MODULE = "__d(\""

    /** How far a module is read for what it exports: an id module is a line, a parameters module a few. */
    private const val MODULE_REACH = 1_500

    /** How far back from a request's name its id is looked for: `id` comes first in `params`, `metadata` between. */
    private const val PARAMS_REACH = 800

    /** How much of a query's own module is read for the variables it declares. */
    private const val DEFINITION_REACH = 120_000
    private const val MOST_NAMES = 40

    private val queryName = Regex("Barcelona[A-Za-z0-9]*Query")
    private val moduleName = Regex("""__d\("(Barcelona[A-Za-z0-9]*)([_$.][A-Za-z0-9_$.]*)?"""")
    private val exported = Regex("""exports\s*=\s*"(\d{6,32})"""")
    private val named = Regex(""""?name"?\s*:\s*"(Barcelona[A-Za-z0-9]*)"\s*,\s*"?operationKind"?\s*:\s*"([a-z]+)"""")
    private val paramsId = Regex("""(?<![A-Za-z0-9_$])"?id"?\s*:\s*"(\d{6,32})"""")
    private val write = Regex(""""?operationKind"?\s*:\s*"(?:mutation|subscription)"""")
    private val argument = Regex(""""?kind"?\s*:\s*"LocalArgument"\s*,\s*"?name"?\s*:\s*"([A-Za-z_][A-Za-z0-9_]{0,80})"""")
    private val provided = Regex("""__relay_internal__pv__([A-Za-z0-9_]{1,120})""")
    private val linkedField = Regex(""""?kind"?\s*:\s*"LinkedField"\s*,\s*"?name"?\s*:\s*"([A-Za-z_][A-Za-z0-9_]{0,120})"""")
    private val scriptSrc = Regex("""<(?:script|link)\b[^>]{0,2000}?\b(?:src|href)\s*=\s*"([^"]{8,2000})"""", RegexOption.IGNORE_CASE)
    private val resource = Regex(""""([A-Za-z0-9_+\-]{2,40})"\s*:\s*\{[^{}]{0,400}?"src"\s*:\s*"([^"]{8,2000})"""")
    private val component = Regex(""""(Barcelona[A-Za-z0-9_.$]{1,160})"\s*:\s*\{\s*"r"\s*:\s*\[([^\]]{0,4000})]""")
    private val quoted = Regex(""""([^"]{1,60})"""")
    private val bundleHosts = setOf("static.cdninstagram.com", "static.threads.com", "static.threads.net")

    /** Every query [js] names with a doc id, each (name, id) pair once, in the order found; never a mutation. */
    fun operations(js: String): List<ThreadsOperation> = (idModules(js) + requestParams(js)).distinct().toList()

    /** [operations] by name; where a name is given two ids the first found is kept. */
    fun ids(js: String): Map<String, String> = operations(js).groupBy({ it.name }, { it.docId }).mapValues { it.value.first() }

    /** The variables the query [name] declares, where [js] holds its definition; names only, each once. */
    fun variables(js: String, name: String): List<String> = namesIn(js, name, argument)

    /** The variables Relay provides the query [name] itself (`__relay_internal__pv__…`), where [js] holds its definition. */
    fun providedVariables(js: String, name: String): List<String> = namesIn(js, name, provided)

    /** The linked fields the query [name] selects, in the order its definition writes them, where [js] holds it; names only. */
    fun fields(js: String, name: String): List<String> = namesIn(js, name, linkedField)

    /**
     * The JavaScript bundles a threads.com page names, as addresses on Meta's
     * static hosts and nowhere else, each once: first those its bootloader
     * lists for a component whose name holds one of [wanted] (a query's name
     * without `Query`), then the scripts the page itself loads, then the
     * rest of its resource map (`rsrcMap`).
     */
    fun bundles(html: String, wanted: Collection<String> = emptyList()): List<String> {
        val resources = resource.findAll(html).mapNotNull { match -> bundle(match.groupValues[2])?.let { match.groupValues[1] to it } }.toList()
        val byHash = resources.toMap()
        val stems = wanted.map { it.removeSuffix("Query") }.filter { it.isNotEmpty() }
        val forWanted = component.findAll(html)
            .filter { match -> stems.any { it in match.groupValues[1] } }
            .flatMap { match -> quoted.findAll(match.groupValues[2]).mapNotNull { byHash[it.groupValues[1]] } }
        val loaded = scriptSrc.findAll(html).mapNotNull { bundle(it.groupValues[1]) }
        return (forWanted + loaded + resources.asSequence().map { it.second }).distinct().toList()
    }

    /** Modules that export one id: the module's name starts with the query's. */
    private fun idModules(js: String): Sequence<ThreadsOperation> = moduleName.findAll(js).mapNotNull { match ->
        val name = match.groupValues[1].takeIf(::isQuery) ?: return@mapNotNull null
        val body = moduleBody(js, match.range.first, MODULE_REACH)
        if (write.containsMatchIn(body)) return@mapNotNull null
        exported.find(body)?.let { ThreadsOperation(name, it.groupValues[1]) }
    }

    /** Requests written out: the last `id:"…"` before a query's `name:"…",operationKind:"query"`, inside its own `params`. */
    private fun requestParams(js: String): Sequence<ThreadsOperation> = named.findAll(js).mapNotNull { match ->
        val name = match.groupValues[1].takeIf { isQuery(it) && match.groupValues[2] == "query" } ?: return@mapNotNull null
        val before = js.substring(maxOf(0, match.range.first - PARAMS_REACH), match.range.first)
        val params = before.lastIndexOf("params").takeIf { it >= 0 && it > before.lastIndexOf(MODULE) } ?: return@mapNotNull null
        paramsId.findAll(before.substring(params)).lastOrNull()?.let { ThreadsOperation(name, it.groupValues[1]) }
    }

    /** A query's name and nothing else: `…Query`, with no word of a write in it. */
    private fun isQuery(name: String): Boolean = queryName.matches(name) && "Mutation" !in name && "Subscription" !in name

    private fun namesIn(js: String, name: String, shape: Regex): List<String> =
        definitions(js, name).flatMap { shape.findAll(it).map { match -> match.groupValues[1] } }.distinct().take(MOST_NAMES).toList()

    /** The text of the modules of [js] that define the query [name]: its `.graphql` module, else the one writing its request out. */
    private fun definitions(js: String, name: String): Sequence<String> {
        if (!isQuery(name)) return emptySequence()
        val graphql = starts(js, "$MODULE$name.graphql\"")
        val written = Regex(""""?name"?\s*:\s*"${Regex.escape(name)}"\s*,\s*"?operationKind"?\s*:\s*"query"""").findAll(js)
            .map { js.lastIndexOf(MODULE, it.range.first).coerceAtLeast(0) }
        return (graphql + written).distinct().map { moduleBody(js, it, DEFINITION_REACH) }
    }

    private fun starts(text: String, marker: String): Sequence<Int> =
        generateSequence(text.indexOf(marker).takeIf { it >= 0 }) { at -> text.indexOf(marker, at + 1).takeIf { it >= 0 } }

    /** The module that starts at [from], up to the next module or [reach] characters. */
    private fun moduleBody(js: String, from: Int, reach: Int): String {
        val end = js.indexOf(MODULE, from + MODULE.length).let { if (it < 0) js.length else it }
        return js.substring(from, minOf(end, from + reach))
    }

    /** [address] as a bundle's: https, on one of Meta's static hosts, a `.js` file; null for anything else. */
    private fun bundle(address: String): String? {
        val plain = address.replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&")
        if (!plain.startsWith("https://") || plain.any { it.isWhitespace() || it == '"' || it == '\\' || it == '<' }) return null
        val rest = plain.removePrefix("https://")
        val host = rest.substringBefore('/').lowercase()
        val path = rest.substringAfter('/', "").substringBefore('?').substringBefore('#')
        return plain.takeIf { host in bundleHosts && path.endsWith(".js") }
    }
}
