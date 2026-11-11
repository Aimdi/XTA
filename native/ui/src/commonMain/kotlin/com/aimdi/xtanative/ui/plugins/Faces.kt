package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.home.LetterMark
import com.aimdi.xtanative.ui.home.Monogram

/** What stands in for someone's missing picture (XTA's FallbackAvatar): one letter on a colour of its own. */
data class PluginFallbackFace(val letter: String, val colour: Color) {
    companion object {
        /**
         * The first letter or digit of [name] (else of [seed]) on the colour
         * [seed] picks from XTA's palette: seeded by the account's id, so a
         * rename keeps the colour and two accounts of one name differ.
         */
        fun of(name: String, seed: String): PluginFallbackFace = PluginFallbackFace(Monogram.initial(name, seed), Color(Monogram.colour(seed)))
    }
}

/**
 * Someone's round picture, or, when [url] is blank, their initial on their
 * colour ([PluginFallbackFace]), the same size and shape, as XTA draws an
 * account without a picture.
 */
@Composable
fun PluginAvatar(url: String?, name: String, seed: String, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    if (!url.isNullOrBlank()) {
        Avatar(url, size, modifier, onClick)
        return
    }
    val face = PluginFallbackFace.of(name, seed)
    val tap = if (onClick != null) Modifier.clip(CircleShape).clickable(onClick = onClick) else Modifier
    LetterMark(face.letter, face.colour, size, modifier.then(tap))
}
