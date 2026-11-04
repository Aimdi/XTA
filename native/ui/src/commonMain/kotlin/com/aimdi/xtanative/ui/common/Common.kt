package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.problem_no_account
import com.aimdi.xtanative.ui.generated.resources.problem_offline
import com.aimdi.xtanative.ui.generated.resources.problem_other
import com.aimdi.xtanative.ui.generated.resources.problem_rate_limited
import com.aimdi.xtanative.ui.generated.resources.problem_refused
import com.aimdi.xtanative.ui.generated.resources.problem_signing
import com.aimdi.xtanative.ui.generated.resources.retry
import org.jetbrains.compose.resources.stringResource

val LocalGraph = staticCompositionLocalOf<AppGraph> { error("No AppGraph provided") }

/** A round picture of someone, asked for as its server wants it ([pictureModel]: Pixiv's with its Referer). */
@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    AsyncImage(
        model = url?.let { rememberPicture(it) },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun LoadingRow(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
fun problemText(problem: Problem): String = when (problem) {
    Problem.NoAccount -> stringResource(Res.string.problem_no_account)
    Problem.RateLimited -> stringResource(Res.string.problem_rate_limited)
    Problem.Offline -> stringResource(Res.string.problem_offline)
    Problem.Signing -> stringResource(Res.string.problem_signing)
    is Problem.Refused -> stringResource(Res.string.problem_refused, problem.code)
    Problem.Other -> stringResource(Res.string.problem_other)
}

/** A read that brought nothing: why, and a way to try again. */
@Composable
fun ProblemView(problem: Problem, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            problemText(problem),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
}

/** One row in a list: a failed page under what is already shown. */
@Composable
fun ProblemRow(problem: Problem, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(problemText(problem), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
}
