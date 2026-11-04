package com.aimdi.xtanative.ui.links

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.services.links.LinkBrowserSession
import com.aimdi.xtanative.services.links.LinkLaunch
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_link_opened_outside
import org.jetbrains.compose.resources.stringResource

/** The desktop has no web view: the page opens in the system browser, and the screen says so. */
@Composable
internal actual fun LinkWebPage(url: String, session: LinkBrowserSession, guard: (String) -> Boolean, control: LinkPageControl, modifier: Modifier) {
    LaunchedEffect(url) {
        DesktopLinks.launch(LinkLaunch.Desktop(url))
        session.finished(url)
    }
    control.reload = { DesktopLinks.launch(LinkLaunch.Desktop(url)) }
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(Res.string.reader_link_opened_outside),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
