package com.aimdi.xtanative.core.net

import java.net.InetAddress

/**
 * The hosts that go straight past a proxy, written as curl reads NO_PROXY:
 * names separated by commas or spaces, "*" for every host, a name that
 * also covers the names under it ("example.com" and ".example.com" both take
 * "a.example.com", never "badexample.com"; "*.example.com" too, as desktop
 * settings write it), an IP address, or a block of them ("10.0.0.0/8",
 * "fd00::/8"). A ":port" after a name is ignored. Nothing here asks DNS.
 */
class NoProxyRules private constructor(private val all: Boolean, private val names: List<String>, private val blocks: List<Block>) {
    /** Whether [host] (a name or an IP address, bracketed or not) goes straight. */
    fun matches(host: String): Boolean {
        if (all) return true
        val name = normalized(host)
        if (name.isEmpty()) return false
        val address = literal(name)
        return names.any { name == it || name.endsWith(".$it") } || (address != null && blocks.any { it.contains(address) })
    }

    /** An address and how many of its leading bits must match. */
    private class Block(private val address: ByteArray, private val bits: Int) {
        fun contains(other: ByteArray): Boolean {
            if (other.size != address.size) return false
            val whole = bits / 8
            if ((0 until whole).any { other[it] != address[it] }) return false
            val rest = bits % 8
            if (rest == 0) return true
            val mask = (0xFF shl (8 - rest)) and 0xFF
            return (other[whole].toInt() and mask) == (address[whole].toInt() and mask)
        }
    }

    companion object {
        /** No host goes straight. */
        val NONE = NoProxyRules(false, emptyList(), emptyList())

        /** [text] as rules; whatever entry cannot be read is left out. */
        fun parse(text: String?): NoProxyRules {
            val entries = text.orEmpty().split(',', ' ', '\t', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }
            if (entries.isEmpty()) return NONE
            if ("*" in entries) return NoProxyRules(true, emptyList(), emptyList())
            val blocks = entries.mapNotNull(::block)
            val names = entries.filter { block(it) == null }.map(::name).filter { it.isNotEmpty() }
            return NoProxyRules(false, names, blocks)
        }

        private fun block(entry: String): Block? {
            val (addressText, bitsText) = entry.split('/', limit = 2).let { it[0] to it.getOrNull(1) }
            val address = literal(normalized(addressText)) ?: return null
            val bits = bitsText?.toIntOrNull()?.takeIf { it in 0..address.size * 8 } ?: if (bitsText == null) address.size * 8 else return null
            return Block(address, bits)
        }

        private fun name(entry: String): String {
            val bare = entry.removePrefix("*").removePrefix(".")
            // "host:port": one colon only, so an IPv6 address is never cut.
            val host = if (bare.count { it == ':' } == 1) bare.substringBefore(':') else bare
            return normalized(host)
        }

        internal fun normalized(host: String): String = host.trim().removePrefix("[").removeSuffix("]").trimEnd('.').lowercase()

        private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
        private val IPV6 = Regex("""[0-9a-f:.]+""")

        /** [host] as an IP address's bytes when it is written as one; null for a name, without asking DNS. */
        internal fun literal(host: String): ByteArray? = when {
            IPV4.matches(host) -> host.split('.').map { it.toInt() }.takeIf { parts -> parts.all { it in 0..255 } }?.map { it.toByte() }?.toByteArray()
            // With a colon, Java reads the text as an IPv6 address and never as a name to look up.
            ':' in host && IPV6.matches(host) -> runCatching { InetAddress.getByName(host).address }.getOrNull()
            else -> null
        }
    }
}
