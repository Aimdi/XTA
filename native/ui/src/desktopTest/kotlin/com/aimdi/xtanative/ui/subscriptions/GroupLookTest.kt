package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupLookTest {
    @Test
    fun aGroupWithoutAColourGetsTheSameOneFromItsNameAsInXta() {
        assertEquals(0xFF5961C0, GroupLook.fallbackColor("Art"))
        assertEquals(0xFFC05998, GroupLook.fallbackColor("News"))
        assertEquals(0xFFC0598E, GroupLook.fallbackColor("Space and astronomy"), "a long name wraps round as Dart's integers do")
        assertEquals(0xFFC05959, GroupLook.fallbackColor(""))
        val chosen = FollowGroup("g", "Art", emptyList(), color = 0xFF00BA7C)
        assertEquals(0xFF00BA7C, GroupLook.seedColor(chosen))
        assertEquals(0xFF5961C0, GroupLook.seedColor(chosen.copy(color = null)))
    }

    @Test
    fun theMarkIsTheEmojiTheIconOrTheInitialAsTheStyleAndWhatIsSetAllow() {
        val star = GroupLook.iconJson("star")
        assertEquals(GroupMarkKind.EMOJI, GroupLook.markKind(GroupMarkStyle.AUTO, "🚀", null))
        assertEquals(GroupMarkKind.INITIAL, GroupLook.markKind(GroupMarkStyle.AUTO, " ", star), "automatic shows no icon")
        assertEquals(GroupMarkKind.SYMBOL, GroupLook.markKind(GroupMarkStyle.SYMBOL, "🚀", star))
        assertEquals(GroupMarkKind.INITIAL, GroupLook.markKind(GroupMarkStyle.SYMBOL, null, GroupLook.DEFAULT_ICON), "XTA's default icon is no icon")
        assertEquals(GroupMarkKind.INITIAL, GroupLook.markKind(GroupMarkStyle.EMOJI, null, star))
        assertEquals(GroupMarkKind.INITIAL, GroupLook.markKind(GroupMarkStyle.GENERATED, "🚀", star))
        assertEquals(GroupMarkKind.EMOJI, GroupLook.markKind(42, "🚀", null), "a style XTA does not know reads as automatic")
    }

    @Test
    fun onlyAMarkTheReaderPickedTakesThePlaceOfTheMembersFaces() {
        val group = FollowGroup("g", "Art", emptyList())
        assertFalse(GroupLook.hasChosenMark(group))
        assertTrue(GroupLook.hasChosenMark(group.copy(emoji = "🎨")))
        assertTrue(GroupLook.hasChosenMark(group.copy(markStyle = GroupMarkStyle.SYMBOL, icon = GroupLook.iconJson("palette"))))
        assertFalse(GroupLook.hasChosenMark(group.copy(markStyle = GroupMarkStyle.SYMBOL)))
    }

    @Test
    fun theInitialIsTheFirstLetterWithItsAccentsElseTheFirstGrapheme() {
        assertEquals("A", GroupLook.initial("  ✨ art"))
        assertEquals("É", GroupLook.initial("été"))
        assertEquals("1", GroupLook.initial("123"))
        assertEquals("?", GroupLook.initial("   "))
        assertEquals("イ", GroupLook.initial("イラスト"))
        assertEquals("👍🏽", GroupLook.firstGrapheme("👍🏽 and more"), "a skin tone stays with its emoji")
    }

    @Test
    fun anIconIsReadFromXtasJsonOrAsABareKey() {
        assertEquals("star", GroupLook.iconKey("""{"pack":"custom","key":"star","iconData":{"codePoint":1}}"""))
        assertEquals("rocket", GroupLook.iconKey("rocket"))
        assertNull(GroupLook.iconKey("  "))
        assertNull(GroupLook.iconKey("""{"pack":"custom"}"""))
        assertEquals("star", GroupLook.iconKey(GroupLook.iconJson("star")))
    }

    @Test
    fun aFaceWithoutAPictureGetsAColourFromItsIdAndALetterFromItsName() {
        assertEquals(GroupLook.FACE_COLORS[1], GroupLook.faceColor("12"))
        assertEquals(GroupLook.FACE_COLORS[3], GroupLook.faceColor("783214"))
        assertEquals("N", GroupLook.faceInitial("  nasa", "1"))
        assertEquals("7", GroupLook.faceInitial("…", "7x"))
        assertEquals("#", GroupLook.faceInitial("…", "!"))
    }

    @Test
    fun whiteGoesOnDarkColoursAndBlackOnPaleOnes() {
        assertTrue(GroupLook.isDark(0xFF000000))
        assertTrue(GroupLook.isDark(0xFF7856FF))
        assertFalse(GroupLook.isDark(0xFFFFFFFF))
        assertFalse(GroupLook.isDark(0xFFFFD400))
        assertFalse(GroupLook.isDark(0xFFE1E8ED))
    }

    @Test
    fun everyColourOnOfferIsOpaqueAndOffersAreXtas() {
        assertEquals(14, GroupLook.COLOR_CHOICES.size)
        assertEquals(94, GroupLook.ICON_KEYS.size)
        assertTrue(GroupLook.COLOR_CHOICES.all { it ushr 24 == 0xFFL })
        assertEquals(GroupLook.ICON_KEYS.size, GroupLook.ICON_KEYS.distinct().size)
    }

    @Test
    fun aFacesColourIsStableNeverOutOfRangeAndNeverGrey() {
        assertEquals(GroupLook.faceColor("user-42"), GroupLook.faceColor("user-42"))
        assertTrue(listOf("alice", "bob", "carol", "dave").map(GroupLook::faceColor).toSet().size > 1)
        listOf("a", "a very long screen name that overflows the hash badly", "1234567890", "").forEach { GroupLook.faceColor(it) }
        GroupLook.FACE_COLORS.forEach { colour ->
            val r = (colour shr 16 and 0xFF) / 255.0
            val g = (colour shr 8 and 0xFF) / 255.0
            val b = (colour and 0xFF) / 255.0
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val light = (max + min) / 2
            val saturation = if (max == min) 0.0 else (max - min) / (1 - kotlin.math.abs(2 * light - 1))
            assertTrue(saturation > 0.2, "a missing picture must look deliberate: ${colour.toString(16)}")
        }
    }

    @Test
    fun aFacesInitialIsTheFirstLetterOrDigitOfItsNameElseItsSeedElseHash() {
        assertEquals("A", GroupLook.faceInitial("Anime", "x"))
        assertEquals("E", GroupLook.faceInitial("  \", elon", "x"))
        assertEquals("Z", GroupLook.faceInitial("", "zebra"))
        assertEquals("#", GroupLook.faceInitial("", ""))
        assertEquals("4", GroupLook.faceInitial("42 things", "x"))
    }
}
