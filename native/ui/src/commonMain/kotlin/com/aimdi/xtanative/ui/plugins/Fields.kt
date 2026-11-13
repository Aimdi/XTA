package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.post.PostIcons
import kotlinx.coroutines.flow.collectLatest

/** Shows [notices] over the foot of the screen, one at a time, the newest taking the place of the one before. */
@Composable
fun BoxScope.NoticeHost(notices: PluginNotices, modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(notices) { notices.notices.collectLatest { host.showSnackbar(it) } }
    SnackbarHost(host, modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp))
}

/**
 * A field for a cookie header, a token or a key, as XTA's settings ask for
 * them: hidden until the reader shows it ([reveal] and [conceal] name the
 * button), one line while hidden and up to [lines] when shown, never
 * corrected or suggested by the keyboard.
 */
@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    reveal: String,
    conceal: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    supporting: String? = null,
    lines: Int = 1,
) {
    var shown by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = hint?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = !shown || lines == 1,
        maxLines = if (shown) lines else 1,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { shown = !shown }) {
                Icon(if (shown) PostIcons.VisibilityOff else Visibility, contentDescription = if (shown) conceal else reveal)
            }
        },
        modifier = modifier,
    )
}

/**
 * A field for the address of a server, as XTA's settings ask for the
 * reader's own: one line, typed as a link, never corrected or suggested by
 * the keyboard, [supporting] under it.
 */
@Composable
fun PluginAddressField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, supporting: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
        modifier = modifier,
    )
}

private val Visibility: ImageVector by lazy {
    val path = "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5z" +
        "M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z"
    ImageVector.Builder(name = "Visibility", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .build()
}
