package com.aimdi.xtanative.core.x.signing

import java.security.MessageDigest
import java.util.Base64
import kotlin.random.Random
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Signs X's GraphQL requests with the `x-client-transaction-id` header, as
 * XTA's lib/client/x_client_transaction_id does (a port of
 * XClientTransaction). X answers an unsigned or badly signed request with 404.
 *
 * [keyBytes] come from the page's `twitter-site-verification` key and
 * [animationKey] from one of its loading animations; both are derived once by
 * [derive] and reused until X rotates them.
 */
class TransactionSigner(
    val keyBytes: IntArray,
    val animationKey: String,
    private val keyword: String = DEFAULT_KEYWORD,
    private val extraNumber: Int = ADDITIONAL_RANDOM_NUMBER,
) {
    /** The header's value for a request of [method] to [path], at [nowMillis]. */
    fun transactionId(method: String, path: String, nowMillis: Long = System.currentTimeMillis(), random: Random = Random): String {
        val time = (nowMillis - EPOCH_SECONDS * 1000) / 1000
        val timeBytes = IntArray(4) { i -> ((time shr (i * 8)) and 0xFF).toInt() }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$method!$path!$time$keyword$animationKey".toByteArray(Charsets.UTF_8))
        val mask = random.nextInt(256)
        val bytes = keyBytes.toList() + timeBytes.toList() + hash.take(16).map { it.toInt() and 0xFF } + extraNumber
        val out = ByteArray(bytes.size + 1)
        out[0] = mask.toByte()
        bytes.forEachIndexed { i, value -> out[i + 1] = (value xor mask).toByte() }
        return Base64.getEncoder().encodeToString(out).replace("=", "")
    }

    companion object {
        const val DEFAULT_KEYWORD = "obfiowerehiring"
        const val ADDITIONAL_RANDOM_NUMBER = 3
        private const val EPOCH_SECONDS = 1682924400L
        private const val TOTAL_TIME = 4096.0

        private val indicesPattern = Regex("""(\(\w{1}\[(\d{1,2})\],\s*16\))+""", RegexOption.MULTILINE)

        /** The signer for [page], given the text of the signing script it names. */
        fun derive(page: Document, signingScript: String): TransactionSigner {
            val (rowIndex, keyIndices) = indicesOf(signingScript)
            val keyBytes = keyBytesOf(keyOf(page))
            return TransactionSigner(keyBytes, animationKey(keyBytes, rowIndex, keyIndices, page))
        }

        /** How many of the `(a[N], 16)` calls the indices are read from [script] holds. */
        fun indexCallsIn(script: String): Int = indicesPattern.findAll(script).count()

        /** The frame row index, then the key-byte indices, from the script's `(a[N], 16)` calls. */
        fun indicesOf(script: String): Pair<Int, List<Int>> {
            val indices = indicesPattern.findAll(script).map { it.groupValues[2].toInt() }.toList()
            if (indices.isEmpty()) throw IllegalStateException("Couldn't get KEY_BYTE indices")
            return indices.first() to indices.drop(1)
        }

        fun keyOf(page: Document): String =
            page.selectFirst("meta[name=twitter-site-verification]")?.attr("content")
                ?: throw IllegalStateException("Couldn't get [twitter-site-verification] key from the page source")

        fun keyBytesOf(key: String): IntArray = Base64.getDecoder().decode(key).map { it.toInt() and 0xFF }.toIntArray()

        /** Whether [page] has the key and four loading animations the signer reads. */
        fun hasBootstrapData(page: Document): Boolean = missingBootstrapData(page) == null

        /** What [page] lacks of the key and loading animations a signer is derived from; null when it has them. */
        fun missingBootstrapData(page: Document): String? {
            val key = page.selectFirst("meta[name=twitter-site-verification]")?.attr("content")
            if (key.isNullOrEmpty()) return "no verification key"
            val bytes = try {
                keyBytesOf(key)
            } catch (_: IllegalArgumentException) {
                return "an unreadable verification key"
            }
            if (bytes.size < 6) return "a verification key of ${bytes.size} bytes"
            val frames = animationFrames(page)
            if (frames.size < 4) return "${frames.size} loading frames"
            val drawn = frames.take(4).all { frame -> animationPath(frame)?.let { it.length > 9 && 'C' in it } == true }
            return if (drawn) null else "unreadable loading frames"
        }

        private fun animationFrames(page: Document) = page.select("[id^=loading-x-anim]")

        /** The `d` of a loading animation's second path: `<svg><g><path/><path d="…"/></g></svg>`. */
        private fun animationPath(frame: Element): String? {
            val group = frame.children().firstOrNull() ?: return null
            if (group.children().size < 2) return null
            return group.child(1).attr("d").takeIf { group.child(1).hasAttr("d") }
        }

        internal fun animationKey(keyBytes: IntArray, rowIndex: Int, keyIndices: List<Int>, page: Document): String {
            val frameRowIndex = keyBytes[rowIndex] % 16
            val product = keyIndices.fold(1L) { acc, index -> acc * (keyBytes[index] % 16) }
            val frameTime = jsRound(product / 10.0) * 10
            val row = frameRows(keyBytes, page)[frameRowIndex]
            return animate(row, frameTime / TOTAL_TIME)
        }

        /** The chosen animation's path, cut at `C` into rows of numbers. */
        internal fun frameRows(keyBytes: IntArray, page: Document): List<List<Int>> {
            val frame = animationFrames(page)[keyBytes[5] % 4]
            val d = animationPath(frame) ?: throw IllegalStateException("The loading animation has no path")
            return d.substring(9).split('C').mapNotNull { segment ->
                val cleaned = segment.replace(Regex("[^\\d]+"), " ").trim()
                if (cleaned.isEmpty()) null else cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toInt() }
            }
        }

        private fun solve(value: Double, min: Double, max: Double, rounding: Boolean): Double {
            val result = value * (max - min) / 255.0 + min
            return if (rounding) kotlin.math.floor(result) else roundTo2(result)
        }

        internal fun animate(frames: List<Int>, targetTime: Double): String {
            val fromColor = doubleArrayOf(frames[0].toDouble(), frames[1].toDouble(), frames[2].toDouble(), 1.0)
            val toColor = doubleArrayOf(frames[3].toDouble(), frames[4].toDouble(), frames[5].toDouble(), 1.0)
            val toRotation = doubleArrayOf(solve(frames[6].toDouble(), 60.0, 360.0, true))
            val curves = frames.drop(7).mapIndexed { index, value -> solve(value.toDouble(), isOdd(index), 1.0, false) }
            val eased = Cubic(curves.toDoubleArray()).valueAt(targetTime)

            val color = interpolate(fromColor, toColor, eased).map { it.coerceIn(0.0, 255.0) }
            val rotation = interpolate(doubleArrayOf(0.0), toRotation, eased)
            val matrix = rotationMatrix(rotation[0])

            val parts = ArrayList<String>()
            for (i in 0 until color.size - 1) parts.add(dartRound(color[i]).toString(16))
            for (value in matrix) {
                var rounded = roundTo2(value)
                if (rounded < 0) rounded = -rounded
                val hex = floatToHex(rounded)
                parts.add(
                    when {
                        hex.startsWith(".") -> "0$hex".lowercase()
                        hex.isNotEmpty() -> hex
                        else -> "0"
                    },
                )
            }
            parts.add("0")
            parts.add("0")
            return parts.joinToString("").replace(Regex("[.\\-]"), "")
        }
    }
}
