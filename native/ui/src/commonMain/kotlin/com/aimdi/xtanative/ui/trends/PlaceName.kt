package com.aimdi.xtanative.ui.trends

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.core.x.model.XTrendPlace
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.discover_trends_worldwide
import org.jetbrains.compose.resources.stringResource

/** [place]'s name as the trends show it: the world in the reader's language ([TrendPlacesLogic.isWorld]), any other place as X names it. */
@Composable
internal fun placeName(place: XTrendPlace): String =
    if (TrendPlacesLogic.isWorld(place)) stringResource(Res.string.discover_trends_worldwide) else place.name
