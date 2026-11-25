package com.aimdi.xtanative.ui.common

import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.LanguageQualifier
import org.jetbrains.compose.resources.Qualifier
import org.jetbrains.compose.resources.RegionQualifier
import org.jetbrains.compose.resources.ResourceItem

/**
 * Where a string stands in each language's file, read back from one packed
 * line. Compose's generator writes that table out as code, an object made
 * per language per string: with 29 languages that was two megabytes of the
 * app. The build packs each table into a line instead
 * (`gradle/pack-resources.gradle.kts`), and this reads it when the string is
 * first asked for.
 *
 * [packed] is `qualifier:offset:size` joined by `|`: the qualifier as its
 * folder names it after `values-` (`de`, `pt-rBR`, empty for the default),
 * the two numbers in base 36. Every entry is [file] in [root]'s folder for
 * its qualifier.
 */
@OptIn(InternalResourceApi::class)
internal fun packedItems(root: String, file: String, packed: String): Set<ResourceItem> =
    packed.split('|').mapTo(LinkedHashSet()) { entry ->
        val (qualifier, offset, size) = entry.split(':')
        val folder = if (qualifier.isEmpty()) "values" else "values-$qualifier"
        ResourceItem(PackedResources.qualifiers(qualifier), "$root$folder/$file", offset.toLong(PackedResources.RADIX), size.toLong(PackedResources.RADIX))
    }

/** The packing's rules, shared with what tests it. */
internal object PackedResources {
    const val RADIX = 36

    /** The qualifiers a folder's name after `values-` stands for: a language, or a language and `-r` its region. */
    @OptIn(InternalResourceApi::class)
    fun qualifiers(name: String): Set<Qualifier> = when {
        name.isEmpty() -> emptySet()
        "-r" in name -> linkedSetOf(LanguageQualifier(name.substringBefore("-r")), RegionQualifier(name.substringAfter("-r")))
        else -> setOf(LanguageQualifier(name))
    }
}
