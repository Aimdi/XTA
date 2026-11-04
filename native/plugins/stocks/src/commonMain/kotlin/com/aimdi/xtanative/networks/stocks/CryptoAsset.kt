package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.CryptoAddresses
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A token followed by its contract: a network and an address identify it, a
 * ticker is only a label. Kept as XTA keeps it in the watchlist's symbol
 * column ([encode]), so a backup round-trips byte for byte.
 */
data class CryptoAsset(
    val chain: String,
    val address: String,
    val symbol: String,
    val name: String,
    /** Whether the posts feed also searches the `$SYMBOL` cashtag. Off by default: unrelated tokens share symbols, never contracts. */
    val includeCashtag: Boolean = false,
) {
    /** `crypto:{network}:{contract}`, the identity a watchlist row and a group member go by. */
    val id: String get() = "crypto:${chain.lowercase()}:${canonicalAddress(address)}"

    /**
     * A token read without a symbol or name has its address in their place.
     * Compared as addresses, so a checksum-cased contract still counts as
     * "no symbol" (XTA compared exactly).
     */
    private val hasSymbol: Boolean get() = !CryptoAddresses.same(symbol, address)
    private val hasName: Boolean get() = !CryptoAddresses.same(name, address)

    /** `$PEPE`; without a symbol its name, else its address cut in the middle, never the whole address. */
    val label: String get() = if (hasSymbol) "$$symbol" else nameOrAddress

    val nameOrAddress: String get() = if (hasName) name else shortAddress

    /** [label] without the cashtag's `$`: `PEPE`, as a row or a strip entry titles it. */
    val bareLabel: String get() = if (hasSymbol) symbol else nameOrAddress

    /** [label] with the name beside it when that adds something: `$PEPE · Pepe`. */
    val fullLabel: String get() = if (hasSymbol && hasName) "$label · $name" else label

    val chainName: String get() = CryptoAddresses.chainName(chain)

    val shortAddress: String get() = CryptoAddresses.short(address)

    /** `Ethereum · 0x6982…1933`. */
    val subtitle: String get() = "$chainName · $shortAddress"

    /** `$PEPE`, or null when the symbol cannot be written as an X cashtag. */
    val cashtag: String? get() = if (cashtagSymbol.matches(symbol)) "$${symbol.uppercase()}" else null

    /** X search terms for its posts: always the quoted contract, the cashtag too when the reader asked for it. */
    val searchTerms: List<String> get() = listOfNotNull("\"${canonicalAddress(address)}\"", cashtag?.takeIf { includeCashtag })

    /** `"0x…"`, or `("0x…" OR $PEPE)`. */
    val searchQuery: String get() = searchTerms.singleOrNull() ?: searchTerms.joinToString(" OR ", "(", ")")

    fun withCashtag(include: Boolean): CryptoAsset = copy(includeCashtag = include)

    /** `crypto:v1:{json}`; the `cashtag` key only when on, so rows saved before it stay byte-identical. */
    fun encode(): String = PREFIX + buildJsonObject {
        put("chain", chain)
        put("address", address)
        put("symbol", symbol)
        put("name", name)
        if (includeCashtag) put("cashtag", true)
    }

    companion object {
        const val PREFIX = "crypto:v1:"
        private val cashtagSymbol = Regex("^[A-Za-z][A-Za-z0-9]{0,9}$")
        private val chainPattern = Regex("^[a-z0-9-]+$")
        private val addressPattern = Regex("^[a-zA-Z0-9_-]{16,128}$")

        /** The token in a watchlist row's [raw] symbol; null for a plain ticker or a row that does not read. */
        fun decode(raw: String): CryptoAsset? {
            if (!raw.startsWith(PREFIX)) return null
            return PriceHttp.strictJson(raw.removePrefix(PREFIX))?.let(::fromJson)
        }

        /** A token from `{chain, address, symbol, name, cashtag}`; null when the network or contract is not one. */
        fun fromJson(json: J): CryptoAsset? =
            of(json["chain"].string, json["address"].string, json["symbol"].string, json["name"].string, json["cashtag"].boolean ?: false)

        /**
         * A token from its parts, as XTA's fromJson checks them: a network id
         * of lower-case letters, digits and `-`, a contract of 16 to 128 safe
         * characters (EVM lower-cased). A missing symbol or name is its address.
         */
        fun of(chain: String?, address: String?, symbol: String? = null, name: String? = null, includeCashtag: Boolean = false): CryptoAsset? {
            val network = chain?.trim()?.lowercase() ?: return null
            val contract = address?.trim() ?: return null
            if (!chainPattern.matches(network) || !addressPattern.matches(contract)) return null
            return CryptoAsset(
                chain = network,
                address = canonicalAddress(contract),
                symbol = symbol?.trim()?.takeIf { it.isNotEmpty() } ?: contract,
                name = name?.trim()?.takeIf { it.isNotEmpty() } ?: contract,
                includeCashtag = includeCashtag,
            )
        }

        fun canonicalAddress(address: String): String = if (CryptoAddresses.isEvm(address)) address.lowercase() else address

        /** The contract of a watchlist id (`crypto:base:0x…`); null for a ticker. */
        fun contractForId(id: String): String? {
            val parts = id.split(':')
            if (parts.size != 3 || parts[0] != "crypto") return null
            return of(parts[1], parts[2])?.address
        }
    }
}

/** A token's best market: price, moves and size, all in USD. */
data class CryptoMarket(
    val asset: CryptoAsset,
    val pairAddress: String? = null,
    val price: Double? = null,
    /** The 24-hour move, the figure DEX pages lead with. */
    val changePercent: Double? = null,
    val change1h: Double? = null,
    val change6h: Double? = null,
    val liquidity: Double = 0.0,
    val volume24h: Double? = null,
    val marketCap: Double? = null,
    val fdv: Double? = null,
) {
    val priced: Boolean get() = price != null

    /** The market as a row's quote: measured over 24 hours, its reference worked back from the move. */
    val quote: TickerQuote
        get() = TickerQuote(
            symbol = asset.id,
            currency = "USD",
            price = price,
            previousClose = if (price != null && changePercent != null && changePercent > -100) price / (1 + changePercent / 100) else null,
            points = emptyList(),
            shortName = asset.name,
            volume = volume24h,
            tradesAroundTheClock = true,
            changeWindow = ChangeWindow.ROLLING_24H,
        )
}

/** [value] when it is a real figure, at least [atLeast]: price hosts send infinities, negatives and gaps alike. */
fun finiteFigure(value: Double?, atLeast: Double? = null): Double? =
    value?.takeIf { it.isFinite() && (atLeast == null || it >= atLeast) }
