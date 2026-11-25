// Packs the tables Compose's resource generator writes for strings and plurals.
//
// For every string the generator writes one ResourceItem(...) per language:
// with 29 languages and some 1,350 strings that is 40,000 objects built by
// code, two megabytes of the Android app's dex (native/PERF.md). Here each
// table becomes one line, read back by ui.common.packedItems when the string
// is first asked for. A table that is not the plain shape (a qualifier other
// than language and region, files that differ) is left as the generator wrote
// it, and the build fails if hardly any could be packed: the generator's
// output has then changed and this must follow it.

/** [text], one generated accessor file, with its tables packed; and how many were packed and left. */
fun packResourceAccessors(text: String): Triple<String, Int, Int> {
    val block = Regex("""(StringResource|PluralStringResource)\(("[^"\n]+", "[^"\n]+"), setOf\(\n((?:[ \t]*ResourceItem\([^\n]*\),\n)+)[ \t]*\)\)""")
    val item = Regex("""^[ \t]*ResourceItem\(setOf\((.*?)\), "\$\{MD\}(values[^/"]*)/([^/"]+)", (\d+), (\d+)\),$""")
    val qualifier = Regex("""(\w+)\("([^"]*)"\), """)
    var packed = 0
    var left = 0
    val out = block.replace(text) { match ->
        val entries = match.groupValues[3].trimEnd('\n').split('\n').map { line ->
            val parts = item.find(line) ?: return@replace match.value.also { left++ }
            val qualifiers = qualifier.findAll(parts.groupValues[1]).map { it.groupValues[1] to it.groupValues[2] }.toList()
            val name = when {
                qualifiers.isEmpty() -> ""
                qualifiers.size == 1 && qualifiers[0].first == "LanguageQualifier" -> qualifiers[0].second
                qualifiers.size == 2 && qualifiers[0].first == "LanguageQualifier" && qualifiers[1].first == "RegionQualifier" ->
                    qualifiers[0].second + "-r" + qualifiers[1].second
                else -> return@replace match.value.also { left++ }
            }
            // What is written between the brackets must be all the qualifiers read, and the folder theirs.
            val written = qualifiers.joinToString("") { (kind, value) -> "$kind(\"$value\"), " }
            val folder = if (name.isEmpty()) "values" else "values-$name"
            if (written != parts.groupValues[1] || folder != parts.groupValues[2] || name.any { it == ':' || it == '|' }) return@replace match.value.also { left++ }
            Triple(name, parts.groupValues[3], parts.groupValues[4].toLong().toString(36) + ":" + parts.groupValues[5].toLong().toString(36))
        }
        val file = entries.first().second
        if (entries.any { it.second != file } || entries.map { it.first }.toSet().size != entries.size) return@replace match.value.also { left++ }
        packed++
        val line = entries.joinToString("|") { it.first + ":" + it.third }
        "${match.groupValues[1]}(${match.groupValues[2]}, com.aimdi.xtanative.ui.common.packedItems(MD, \"$file\", \"$line\"))"
    }
    return Triple(out, packed, left)
}

tasks.matching { it.name.startsWith("generateResourceAccessorsFor") }.configureEach {
    doLast {
        var packed = 0
        var left = 0
        outputs.files.asFileTree.matching { include("**/*.kt") }.forEach { file ->
            val (text, done, kept) = packResourceAccessors(file.readText())
            packed += done
            left += kept
            if (done > 0) file.writeText(text)
        }
        logger.lifecycle("pack-resources: $name packed $packed string tables, left $left as written")
        if (packed + left > 0 && packed < left) {
            throw GradleException("pack-resources: only $packed of ${packed + left} string tables could be packed; the generator's output has changed")
        }
    }
}
