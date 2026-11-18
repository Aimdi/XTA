package com.aimdi.xtanative.ui.offline

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.store.offline.OfflineAvailability
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.offline_article_detail
import com.aimdi.xtanative.ui.generated.resources.offline_available
import com.aimdi.xtanative.ui.generated.resources.offline_media_count
import com.aimdi.xtanative.ui.generated.resources.offline_partial
import com.aimdi.xtanative.ui.generated.resources.offline_unavailable
import org.jetbrains.compose.resources.stringResource

/** Available offline, partly, or not at all. */
@Composable
fun statusText(availability: OfflineAvailability): String = when (availability) {
    OfflineAvailability.AVAILABLE -> stringResource(Res.string.offline_available)
    OfflineAvailability.PARTIAL -> stringResource(Res.string.offline_partial)
    OfflineAvailability.UNAVAILABLE -> stringResource(Res.string.offline_unavailable)
}

/** How much of an entry was kept: an article's text and pictures, or a post's media files. */
@Composable
fun detailText(detail: OfflineDetail): String =
    if (detail.article) stringResource(Res.string.offline_article_detail, detail.saved, detail.total)
    else stringResource(Res.string.offline_media_count, detail.saved, detail.total)
