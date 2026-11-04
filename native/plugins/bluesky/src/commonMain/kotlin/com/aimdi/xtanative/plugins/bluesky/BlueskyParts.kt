package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.bluesky.BlueskyErrorKind
import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.plugins.AddToGroupButton
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What a failed Bluesky read says where it is told in a line of its own (bluesky_profile_screen.dart's blueskyErrorMessage). */
internal fun errorText(error: Throwable): PluginText = when (BlueskyErrorKind.of(error)) {
    BlueskyErrorKind.NETWORK -> Strings.error_network
    BlueskyErrorKind.NOT_FOUND -> Strings.error_not_found
    BlueskyErrorKind.RATE_LIMITED -> Strings.error_rate_limited
    BlueskyErrorKind.BAD_RESPONSE -> Strings.error_response
}

/** A row of a sheet's menu: its glyph and what it does. */
@Composable
internal fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Text(label, modifier = Modifier.padding(start = 24.dp))
    }
}

/** A part of a screen, named in the theme's primary colour. */
@Composable
internal fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

/** Small print under a part of a screen. */
@Composable
internal fun NoteText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/**
 * An account in a list: its picture, name and handle (and, with
 * [description], the start of its description), Follow or Unfollow here
 * beside it, after Add to group where the list offers it ([groups]); it
 * opens the profile.
 */
@Composable
internal fun PersonRow(app: BlueskyApp, person: BlueskyProfile, followed: Boolean, description: Boolean = false, groups: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clickable { app.openProfile(person.actor) }.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(person.avatarUrl, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(person.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@" + person.handle.ifEmpty { person.did }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (description && person.description.isNotBlank()) {
                Text(person.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (groups) AddToGroupButton(app.host, person.actor) { app.ensureFollowed(person) }
        TextButton(onClick = { app.toggleFollow(person) }) { Text(pluginString(if (followed) Strings.unfollow else Strings.follow)) }
    }
    Hairline()
}

/**
 * A dialog asking for one line ([title], [hint]): [submit] takes what was
 * typed and gives the reason it was refused, or null once it was done; it
 * may read the network, and the dialog waits for it, saying why a read failed.
 */
@Composable
internal fun TextPrompt(title: String, hint: String, onDismiss: () -> Unit, submit: suspend (String) -> String?) {
    var text by rememberSaveable { mutableStateOf("") }
    var refusal by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val language = currentLanguage()
    val send = {
        if (!busy) {
            busy = true
            scope.launch {
                refusal = try {
                    submit(text.trim())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errorText(e).get(language)
                }
                busy = false
                if (refusal == null) onDismiss()
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    refusal = null
                },
                singleLine = true,
                placeholder = { Text(hint) },
                isError = refusal != null,
                supportingText = refusal?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { send() }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { send() }, enabled = !busy) { Text(pluginString(Strings.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) } },
    )
}
