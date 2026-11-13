package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XVerifiedType
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_verified_account
import com.aimdi.xtanative.ui.generated.resources.posts_verified_government
import com.aimdi.xtanative.ui.generated.resources.posts_verified_organization
import org.jetbrains.compose.resources.stringResource

/**
 * The checks X draws after a name (user_verification.dart): blue, gold for
 * an organisation, grey for a government, and the square badge of the
 * organisation an account belongs to.
 */
@Composable
fun VerifiedBadges(user: XUser, size: Dp, modifier: Modifier = Modifier) {
    if (user.verifiedType == XVerifiedType.NONE && user.affiliation == null) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        when (user.verifiedType) {
            XVerifiedType.NONE -> Unit
            XVerifiedType.BLUE -> Icon(XIcons.Verified, stringResource(Res.string.posts_verified_account), tint = Color.Unspecified, modifier = Modifier.size(size))
            XVerifiedType.BUSINESS -> Icon(GoldCheck, stringResource(Res.string.posts_verified_organization), tint = Color.Unspecified, modifier = Modifier.size(size))
            XVerifiedType.GOVERNMENT -> Icon(GreyCheck, stringResource(Res.string.posts_verified_government), tint = Color.Unspecified, modifier = Modifier.size(size))
        }
        user.affiliation?.let { badge ->
            AsyncImage(
                model = badge.badgeUrl,
                contentDescription = badge.name,
                modifier = Modifier.padding(start = 4.dp).size(size - 2.dp).clip(RoundedCornerShape(3.dp)),
            )
        }
    }
}

private val GoldCheck: ImageVector by lazy { check("GoldCheck", Color(0xFFE2B719)) }
private val GreyCheck: ImageVector by lazy { check("GreyCheck", Color(0xFF829AAB)) }

/** X's badge in [fill] with a white check, drawn untinted. */
private fun check(name: String, fill: Color): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = PathParser().parsePathString(
                "M23,12l-2.44,-2.79l0.34,-3.69l-3.61,-0.82L15.4,1.5L12,2.96L8.6,1.5L6.71,4.69L3.1,5.5L3.44,9.2L1,12l2.44,2.79l-0.34,3.7" +
                    "l3.61,0.82L8.6,22.5l3.4,-1.47l3.4,1.46l1.89,-3.19l3.61,-0.82l-0.34,-3.69L23,12z",
            ).toNodes(),
            fill = SolidColor(fill),
        )
        .addPath(
            pathData = PathParser().parsePathString("M10.09,16.72l-3.8,-3.81l1.48,-1.48l2.32,2.33l5.85,-5.87l1.48,1.48L10.09,16.72z").toNodes(),
            fill = SolidColor(Color.White),
        )
        .build()
