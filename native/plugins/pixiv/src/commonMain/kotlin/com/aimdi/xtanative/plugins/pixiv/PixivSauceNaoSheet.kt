package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivFailure
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivSauceMatch
import com.aimdi.xtanative.networks.pixiv.PixivSauceNao
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberImageChooser
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The search by image the search screen keeps while it is on the back stack, so a work opened from its matches comes back to them. */
@Composable
internal fun rememberSauceSearch(): PixivSauceModel = rememberKept("pixiv-saucenao") { scope ->
    PixivSauceModel(scope, search = { PixivSauceNao.search(it) }, prepare = { picture -> withContext(Dispatchers.Default) { sauceNaoPicture(picture) } })
}

/** [picture] decoded and written again as a PNG no wider than SauceNAO needs, so any format the device reads goes in one it takes (XTA's pixivSauceImage). */
private fun sauceNaoPicture(picture: ByteArray): ByteArray? =
    decodePixels(picture, PixivSauceModel.MAX_WIDTH)?.scaledToWidth(PixivSauceModel.MAX_WIDTH)?.let(PixivPng::encode)

/**
 * Search by image (pixiv_saucenao_sheet.dart): what it sends where, then
 * Choose an image, the search under way, why it failed with Retry, or the
 * Pixiv works found, each opening its work, with Choose another image.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SauceNaoSheet(model: PixivSauceModel, onClose: () -> Unit) {
    val state by model.state.collectAsState()
    val choose = rememberImageChooser(pluginString(Strings.saucenao_pick)) { read -> model.chosen(read) }
    ModalBottomSheet(onDismissRequest = onClose) {
        BoxWithConstraints {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.85f).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Text(pluginString(Strings.saucenao_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(pluginString(Strings.saucenao_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                when (val shown = state) {
                    PixivSauceState.Idle -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PickButton(first = true, choose) }
                    PixivSauceState.Searching -> Searching()
                    is PixivSauceState.Failed -> Failed(shown.error, onRetry = model::retry, choose)
                    is PixivSauceState.Found -> Found(shown.matches, choose)
                }
            }
        }
    }
}

@Composable
private fun PickButton(first: Boolean, choose: () -> Unit) {
    Button(onClick = choose) {
        Icon(PixivIcons.AddPhotoAlternate, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(pluginString(if (first) Strings.saucenao_pick else Strings.saucenao_another))
    }
}

@Composable
private fun Searching() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(pluginString(Strings.saucenao_searching))
    }
}

/** Why it failed, in Pixiv's words for a failed exchange, else SauceNAO's; Retry and another picture. */
@Composable
private fun Failed(error: Throwable, onRetry: () -> Unit, choose: () -> Unit) {
    val reason = PixivSauceModel.failureOf(error)?.let { pluginString(failureText(it)) } ?: pluginString(Strings.saucenao_failed)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(reason, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            TextButton(onClick = onRetry) { Text(pluginString(Strings.retry)) }
            PickButton(first = false, choose)
        }
    }
}

@Composable
private fun Found(matches: List<PixivSauceMatch>, choose: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (matches.isEmpty()) {
            Text(pluginString(Strings.saucenao_empty), textAlign = TextAlign.Center)
        } else {
            LazyColumn(Modifier.weight(1f, fill = false)) { items(matches, key = { it.illustId }) { match -> SauceRow(match) } }
        }
        Spacer(Modifier.height(12.dp))
        PickButton(first = false, choose)
    }
}

/**
 * A match: the work's thumbnail once Pixiv describes it (none for a work the
 * reader's filters or mutes keep out), its title, how alike SauceNAO found it
 * and its artist; a tap opens the work, the sheet waiting for the way back.
 */
@Composable
private fun SauceRow(match: PixivSauceMatch) {
    val runtime = LocalPixiv.current
    val language = currentLanguage()
    val preview by produceState<Result<PixivIllust>?>(null, match.illustId) { value = previewOf(runtime, match.illustId) }
    val illust = preview?.getOrNull()
    val failure = preview?.exceptionOrNull()?.let(PixivFailure::of)
    val signedOut = failure == PixivFailure.UNAUTHORIZED || failure == PixivFailure.NOT_SIGNED_IN
    val details = listOfNotNull(
        match.similarity?.let { pluginString(Strings.saucenao_similarity, PixivSauceModel.similarity(it, language)) },
        match.author.ifEmpty { illust?.userName.orEmpty() }.ifEmpty { null },
    ).joinToString(" · ")
    ListItem(
        headlineContent = { Text(match.title.ifEmpty { illust?.title ?: "#${match.illustId}" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (signedOut) Text(pluginString(Strings.saucenao_preview_unauthorized), color = MaterialTheme.colorScheme.error) else Text(details)
        },
        leadingContent = { MatchThumb(illust) },
        modifier = Modifier.clickable { if (illust != null) runtime.openWork(illust) else runtime.open(PixivRoute.Work(match.illustId)) },
    )
}

private suspend fun previewOf(runtime: PixivRuntime, id: Long): Result<PixivIllust> = try {
    Result.success(runtime.client.illustDetail(id))
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

@Composable
private fun MatchThumb(illust: PixivIllust?) {
    val runtime = LocalPixiv.current
    val settings = pixivSettings()
    val shown = illust?.takeIf { !runtime.mutes.now.isMuted(it) && it.allowed(settings[PixivPrefs.showR18], !settings[PixivPrefs.hideAi]) }
    val shape = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
    if (shown != null) {
        PixivPicture(shown.thumbnailUrl, shape)
    } else {
        Box(shape.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) { Icon(PostIcons.Image, null) }
    }
}
