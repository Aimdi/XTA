package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.aimdi.xtanative.core.backup.FollowGroup

/** [argb] as a Compose colour. */
fun groupColor(argb: Long): Color = Color(argb.toInt())

/** White on a dark colour, near-black on a pale one, as XTA draws on a group's colour. */
fun onGroupColor(argb: Long): Color = if (GroupLook.isDark(argb)) Color.White else Color.Black.copy(alpha = 0.87f)

/** A size in dp as text that does not grow with the reader's text size, so a letter never outgrows its disc. */
@Composable
private fun fixedSp(size: Dp): TextUnit = with(LocalDensity.current) { (size.toPx() / density).dp.toSp() / fontScale }

/**
 * A group's mark (group_identity.dart's GroupMark): a disc in the group's
 * colour carrying its emoji, its icon or its initial.
 */
@Composable
fun GroupMark(group: FollowGroup, size: Dp, modifier: Modifier = Modifier) {
    GroupMark(group.name, GroupLook.seedColor(group), group.emoji, group.icon, group.markStyle, size, modifier)
}

@Composable
fun GroupMark(name: String, seed: Long, emoji: String?, icon: String?, markStyle: Int, size: Dp, modifier: Modifier = Modifier) {
    val onSeed = onGroupColor(seed)
    Box(modifier.size(size).clip(CircleShape).background(groupColor(seed)), contentAlignment = Alignment.Center) {
        when (GroupLook.markKind(markStyle, emoji, icon)) {
            GroupMarkKind.EMOJI -> Text(GroupLook.firstGrapheme(emoji.orEmpty()), style = TextStyle(fontSize = fixedSp(size * 0.5f)))
            GroupMarkKind.SYMBOL -> GroupIcons.mark(GroupLook.iconKey(icon))?.let {
                Icon(it, contentDescription = null, tint = onSeed, modifier = Modifier.size(size * 0.5f))
            } ?: Initial(name, onSeed, size)
            GroupMarkKind.INITIAL -> Initial(name, onSeed, size)
        }
    }
}

@Composable
private fun Initial(name: String, color: Color, size: Dp) {
    Text(GroupLook.initial(name), color = color, style = TextStyle(fontSize = fixedSp(size * 0.46f), fontWeight = FontWeight.Bold))
}

/** A member's face: their picture, with their initial on a colour of their own while it loads or when there is none. */
@Composable
fun MemberFaceView(face: MemberFace, size: Dp, modifier: Modifier = Modifier) {
    val fallback = @Composable {
        Box(Modifier.size(size).clip(CircleShape).background(groupColor(GroupLook.faceColor(face.id))), contentAlignment = Alignment.Center) {
            Text(
                GroupLook.faceInitial(face.name, face.id),
                color = Color.White,
                style = TextStyle(fontSize = fixedSp(size * 0.46f), fontWeight = FontWeight.Bold),
            )
        }
    }
    if (face.avatarUrl.isNullOrEmpty()) {
        Box(modifier) { fallback() }
        return
    }
    SubcomposeAsyncImage(
        model = face.avatarUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        loading = { fallback() },
        error = { fallback() },
        modifier = modifier.size(size).clip(CircleShape),
    )
}

/**
 * A group's members as its cover (avatar_mosaic.dart): four or more tile into
 * a square, three sit two over one, two overlap, one fills the block, and an
 * empty group shows its own initial on its colour.
 */
@Composable
fun MemberMosaic(faces: List<MemberFace>, groupName: String, groupSeed: Long, ring: Color, extent: Dp, modifier: Modifier = Modifier) {
    val gap = 2.dp
    Box(modifier.size(extent), contentAlignment = Alignment.Center) {
        when (faces.size) {
            0 -> {
                val diameter = extent * 0.78f
                Box(Modifier.size(diameter).clip(CircleShape).background(groupColor(groupSeed)), contentAlignment = Alignment.Center) {
                    Text(
                        GroupLook.faceInitial(groupName, groupName),
                        color = onGroupColor(groupSeed),
                        style = TextStyle(fontSize = fixedSp(diameter * 0.46f), fontWeight = FontWeight.Bold),
                    )
                }
            }
            1 -> MemberFaceView(faces[0], extent * 0.78f)
            2 -> {
                val diameter = extent * 0.58f
                Box(Modifier.size(width = diameter * 2 - 8.dp + 3.dp, height = diameter + 3.dp)) {
                    MemberFaceView(faces[0], diameter, Modifier.border(1.5.dp, ring, CircleShape))
                    MemberFaceView(faces[1], diameter, Modifier.offset(x = diameter - 8.dp).border(1.5.dp, ring, CircleShape))
                }
            }
            3 -> {
                val diameter = (extent - gap) / 2
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        MemberFaceView(faces[0], diameter)
                        MemberFaceView(faces[1], diameter)
                    }
                    MemberFaceView(faces[2], diameter)
                }
            }
            else -> {
                val diameter = (extent - gap) / 2
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    faces.take(4).chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) { pair.forEach { MemberFaceView(it, diameter) } }
                    }
                }
            }
        }
    }
}

/** The mark or the mosaic a tile shows, as large as the space left beside or above its text allows, up to [max]. */
@Composable
fun GroupCover(tile: GroupTile, ring: Color, max: Dp, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopStart) {
        val extent = minOf(minOf(maxWidth, maxHeight), max).coerceAtLeast(0.dp)
        if (GroupLook.hasChosenMark(tile.group)) {
            Box(Modifier.size(extent), contentAlignment = Alignment.Center) { GroupMark(tile.group, extent * 0.78f) }
        } else {
            MemberMosaic(tile.faces, tile.group.name, GroupLook.seedColor(tile.group), ring, extent)
        }
    }
}
