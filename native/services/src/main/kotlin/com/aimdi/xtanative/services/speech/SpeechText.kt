package com.aimdi.xtanative.services.speech

/**
 * The language a piece of prose is written in, as a short code (`de`, `en`,
 * …), or null when it cannot tell (text_language.dart). Counting the
 * commonest little words is crude, but it is all reading aloud needs: the
 * text's language decides which voice reads it, and a German voice reading
 * English is what made articles sound broken for readers whose app
 * language differs from what they read.
 */
fun detectTextLanguage(text: String, sample: Int = 4000): String? {
    val words = wordPattern.findAll(if (text.length > sample) text.substring(0, sample) else text).map { it.value.lowercase() }.toList()
    if (words.size < 8) return null
    val ranked = stopWords.map { (language, common) -> language to words.count { it in common } }.sortedByDescending { it.second }
    val (best, score) = ranked[0]
    val enough = score >= 3 && score >= words.size * 0.08
    return if (enough && score > ranked[1].second * 1.2) best else null
}

private val wordPattern = Regex("[\\p{L}']+")

/** The twenty or so commonest short words of each language reading aloud is likely to meet. */
private val stopWords: Map<String, Set<String>> = mapOf(
    "en" to "the and of to is that it was for with this are but have not they you be on which",
    "de" to "der die das und ist nicht mit ein eine zu sich auf auch den dem von für sie wir ich",
    "fr" to "le la les et est des une du que pas pour dans qui sur avec nous vous il ce au",
    "es" to "el los las y es que una del por para con no se su como pero más lo al está",
    "it" to "il gli e è che una della per non sono con del di questo anche come ma nel alla più",
    "nl" to "de het een en van is niet dat op zijn met voor ook maar wij ik je dit aan er",
    "pt" to "o os as e é que uma do da não para com em por mais como mas se ao são",
).mapValues { (_, words) -> words.split(' ').toSet() }

/**
 * Text cut into utterances an engine will actually finish (speech_store.dart):
 * the platform silently drops the end of a very long one. Cuts fall between
 * sentences, so a break never lands mid-word; a single sentence longer than
 * [maxChars] is cut where it has to be.
 */
fun chunkForSpeech(text: String, maxChars: Int = 3500): List<String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()
    if (trimmed.length <= maxChars) return listOf(trimmed)
    val chunks = ArrayList<String>()
    val buffer = StringBuilder()
    fun flush() {
        if (buffer.isNotEmpty()) chunks.add(buffer.toString())
        buffer.clear()
    }
    for (sentence in trimmed.split(sentenceBreak)) {
        if (sentence.length > maxChars) {
            flush()
            chunks.addAll(sentence.chunked(maxChars))
            continue
        }
        if (buffer.length + sentence.length + 1 > maxChars) flush()
        if (buffer.isNotEmpty()) buffer.append(' ')
        buffer.append(sentence)
    }
    flush()
    return chunks
}

/** After a sentence's end, any run of space, the ideographic space included. */
private val sentenceBreak = Regex("(?U)(?<=[.!?。！？])\\s+")

/**
 * The rest of [full] from [needle], a paragraph the reader held to start
 * reading there. When the paragraph is not found, its first line, then its
 * first 48 characters, are looked for; failing that the needle itself is
 * what to read, since the page sent the rest from that paragraph.
 */
fun textFromHere(full: String, needle: String): String {
    val clip = needle.trim()
    val hay = full.trim()
    if (clip.isEmpty()) return hay
    if (hay.isEmpty()) return clip
    var at = hay.indexOf(clip)
    if (at < 0) {
        val line = clip.split(Regex("\n+")).first().trim()
        if (line.length >= 12) at = hay.indexOf(line)
    }
    if (at < 0) at = hay.indexOf(clip.take(48))
    return when {
        at == 0 -> hay
        at < 0 -> clip
        else -> hay.substring(at)
    }
}
