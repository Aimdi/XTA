package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.LanguageQualifier
import org.jetbrains.compose.resources.RegionQualifier
import org.jetbrains.compose.resources.ResourceItem

@OptIn(InternalResourceApi::class)
class PackedResourcesTest {
    private val root = "composeResources/app/"

    @Test
    fun aPackedLineIsTheTableTheGeneratorWrote() {
        val written = setOf(
            ResourceItem(setOf(LanguageQualifier("ar")), "${root}values-ar/strings.commonMain.cvr", 451, 29),
            ResourceItem(setOf(LanguageQualifier("be"), RegionQualifier("XL")), "${root}values-be-rXL/strings.commonMain.cvr", 257, 29),
            ResourceItem(setOf(LanguageQualifier("pt"), RegionQualifier("BR")), "${root}values-pt-rBR/strings.commonMain.cvr", 143, 25),
            ResourceItem(setOf(), "${root}values/strings.commonMain.cvr", 139, 25),
        )
        val packed = packedItems(root, "strings.commonMain.cvr", "ar:cj:t|be-rXL:75:t|pt-rBR:3z:p|:3v:p")
        assertEquals(written, packed)
        assertEquals(written.toList(), packed.toList(), "in the order written")
    }

    @Test
    fun numbersBeyondTwoDigitsComeBackWhole() {
        val item = packedItems(root, "strings_settings.commonMain.cvr", "zh-rTW:${123_456L.toString(36)}:${7_890L.toString(36)}").single()
        assertEquals(ResourceItem(setOf(LanguageQualifier("zh"), RegionQualifier("TW")), "${root}values-zh-rTW/strings_settings.commonMain.cvr", 123_456, 7_890), item)
    }
}
