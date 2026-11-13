package com.aimdi.xtanative.networks

/** The two address families readers paste from X: EVM chains share one `0x` format, Solana mints are base58. */
enum class CryptoAddressKind { EVM, SOLANA }

/**
 * A pasted string shaped like a contract address: EVM lower-cased (its
 * checksum casing carries no identity), Solana as written (base58 is
 * case-sensitive).
 */
data class CryptoAddressInput(val kind: CryptoAddressKind, val address: String)

/** A piece of a post's text: words as they are, or a contract address to show cut in the middle. */
sealed interface AddressPiece {
    data class Text(val text: String) : AddressPiece

    data class Address(val address: String) : AddressPiece
}

/**
 * Recognising contract addresses, comparing and shortening them, and naming
 * the networks they live on, as XTA's crypto_address.dart does: Stocks'
 * search and watchlist read them, and so does the app's text of an X post,
 * which shows an address cut in the middle (rich_text.dart).
 */
object CryptoAddresses {
    private val evm = Regex("^0x[0-9a-fA-F]{40}$")
    private val base58 = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")
    private val scheme = Regex("^[A-Za-z][A-Za-z0-9+.\\-]*:")

    /**
     * A mention's or a hashtag's word (which keeps an address inside it a
     * word), or a word shaped like an address, which no entity of X marks.
     */
    private val inText = Regex("(#|(?<=\\W|^)@)\\w+|(?<!\\w)(0x[0-9a-fA-F]{40}|[1-9A-HJ-NP-Za-km-z]{32,44})(?!\\w)")

    /** Network names as their projects write them; an unknown id is shown as the price host spells it. */
    val CHAIN_NAMES: Map<String, String> = linkedMapOf(
        "ethereum" to "Ethereum",
        "base" to "Base",
        "bsc" to "BNB Chain",
        "arbitrum" to "Arbitrum",
        "polygon" to "Polygon",
        "optimism" to "Optimism",
        "avalanche" to "Avalanche",
        "solana" to "Solana",
        "blast" to "Blast",
        "linea" to "Linea",
        "zksync" to "zkSync",
        "scroll" to "Scroll",
        "mantle" to "Mantle",
        "sonic" to "Sonic",
        "abstract" to "Abstract",
        "unichain" to "Unichain",
        "hyperevm" to "HyperEVM",
    )

    /** Whether [address] is an EVM contract (`0x` and 40 hex digits), whose casing carries no identity. */
    fun isEvm(address: String): Boolean = evm.matches(address)

    /**
     * The address in [raw]: bare, `$`-prefixed, or the last path segment of
     * an explorer or DEX link that holds one; null when there is none.
     */
    fun detect(raw: String): CryptoAddressInput? {
        val text = raw.trim().removePrefix("$")
        return candidates(text).firstNotNullOfOrNull(::addressIn)
    }

    /** True when [a] and [b] name the same contract: EVM case-insensitively, anything else exactly. */
    fun same(a: String, b: String): Boolean = if (isEvm(a) && isEvm(b)) a.equals(b, ignoreCase = true) else a == b

    /** `0x6982…1933`, `EKpQ…zcjm`: enough to recognise an address at a glance. */
    fun short(address: String): String {
        val head = if (isEvm(address)) 6 else 4
        return if (address.length <= head + 5) address else "${address.take(head)}…${address.takeLast(4)}"
    }

    /** The name [chain] goes by in [CHAIN_NAMES], else the price host's id for it. */
    fun chainName(chain: String): String = CHAIN_NAMES[chain.lowercase()] ?: chain

    /**
     * [text] (a plain run of a post, outside X's entities) cut around the
     * contract addresses in it. A long word without a digit stays a word, and
     * so does an address inside a #hashtag or @mention.
     */
    fun pieces(text: String): List<AddressPiece> {
        val pieces = ArrayList<AddressPiece>()
        var cursor = 0
        for (match in inText.findAll(text)) {
            val word = match.groups[2]?.value ?: continue
            if (detect(word) == null) continue
            if (match.range.first > cursor) pieces.add(AddressPiece.Text(text.substring(cursor, match.range.first)))
            pieces.add(AddressPiece.Address(word))
            cursor = match.range.last + 1
        }
        if (cursor < text.length) pieces.add(AddressPiece.Text(text.substring(cursor)))
        return pieces
    }

    /**
     * How a post shows [address]: cut in the middle as one unbreakable unit
     * (a word joiner after the cut), led by its [network] when the token is
     * watched: `Base · 0x23a2…fe51`.
     */
    fun postLabel(address: String, network: String? = null): String {
        val cut = short(address).replaceFirst("…", "…⁠")
        return if (network == null) cut else "$network · $cut"
    }

    /** The network [networks] names for [address] (contract to network, as a plugin watching tokens gives them), in any EVM casing; null when none does. */
    fun networkIn(address: String, networks: Map<String, String>): String? =
        networks.entries.firstOrNull { same(it.key, address) }?.value

    /** The path segments of a link, last first, or the text itself when it is not a link. */
    private fun candidates(text: String): List<String> {
        val prefix = scheme.find(text) ?: return listOf(text)
        val rest = text.substring(prefix.range.last + 1)
        val path = if (rest.startsWith("//")) rest.drop(2).let { it.substring(it.indexOf('/').takeIf { at -> at >= 0 } ?: it.length) } else rest
        return path.substringBefore('?').substringBefore('#').split('/').reversed()
    }

    private fun addressIn(text: String): CryptoAddressInput? = when {
        isEvm(text) -> CryptoAddressInput(CryptoAddressKind.EVM, text.lowercase())
        // Real mints contain digits; a long run of letters is far more likely a name being typed.
        base58.matches(text) && text.any { it in '0'..'9' } -> CryptoAddressInput(CryptoAddressKind.SOLANA, text)
        else -> null
    }
}
