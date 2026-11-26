package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.keys_post_link
import com.aimdi.xtanative.ui.generated.resources.keys_post_save
import com.aimdi.xtanative.ui.post.MenuHint
import kotlin.test.Test
import kotlin.test.assertTrue

/** The keys the post menu shows at its entries are keys the desktop's list (F1) has for them. */
class MenuHintTest {
    private fun listed(hint: MenuHint, does: org.jetbrains.compose.resources.StringResource): Boolean {
        val combo = hint.held.map { KeyCap.Named(it) } + KeyCap.Sign(hint.key)
        return DesktopShortcuts.all.single { it.does == does }.keys.contains(combo)
    }

    @Test
    fun saveIsB() = assertTrue(listed(MenuHint.SAVE, Res.string.keys_post_save))

    @Test
    fun theLinkIsCtrlShiftC() = assertTrue(listed(MenuHint.COPY_LINK, Res.string.keys_post_link))
}
