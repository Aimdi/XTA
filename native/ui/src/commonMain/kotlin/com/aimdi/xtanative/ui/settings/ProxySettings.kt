package com.aimdi.xtanative.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.aimdi.xtanative.store.settings.DesktopPrefs
import com.aimdi.xtanative.store.settings.ProxyMode
import com.aimdi.xtanative.store.settings.ProxyType
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.settings_proxy
import com.aimdi.xtanative.ui.generated.resources.settings_proxy_bypass
import com.aimdi.xtanative.ui.generated.resources.settings_proxy_bypass_hint
import com.aimdi.xtanative.ui.generated.resources.settings_proxy_description
import com.aimdi.xtanative.ui.generated.resources.settings_proxy_host
import com.aimdi.xtanative.ui.generated.resources.settings_proxy_manual
import com.aimdi.xtanative.ui.generated.resources.settings_proxy_none
import com.aimdi.xtanative.ui.generated.resources.settings_proxy_port
import com.aimdi.xtanative.ui.generated.resources.settings_proxy_server
import com.aimdi.xtanative.ui.generated.resources.settings_proxy_server_unset
import com.aimdi.xtanative.ui.generated.resources.settings_proxy_socks_videos
import com.aimdi.xtanative.ui.generated.resources.settings_proxy_type_http
import com.aimdi.xtanative.ui.generated.resources.settings_proxy_type_socks
import com.aimdi.xtanative.ui.generated.resources.settings_save
import com.aimdi.xtanative.ui.generated.resources.settings_sync_password
import com.aimdi.xtanative.ui.generated.resources.settings_sync_username
import com.aimdi.xtanative.ui.generated.resources.settings_system
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The proxy set by hand, as its dialog edits it: text until it is saved. */
internal data class ProxyForm(
    val type: ProxyType = ProxyType.HTTP,
    val host: String = "",
    val port: String = "",
    val user: String = "",
    val password: String = "",
    val bypass: String = "",
) {
    /** The port, when it is one (1–65535). */
    val portNumber: Int? get() = port.trim().toIntOrNull()?.takeIf { it in 1..65535 }

    /** A host without spaces or slashes ("proxy.lan", "10.0.0.2", "[fd00::1]"), and a port. */
    val isComplete: Boolean get() = host.trim().let { it.isNotEmpty() && it.none { c -> c.isWhitespace() || c == '/' } } && portNumber != null

    /** [settings] with this proxy, and Manual chosen. */
    fun saved(settings: Settings): Settings = settings
        .with(DesktopPrefs.proxyType, type).with(DesktopPrefs.proxyHost, host.trim()).with(DesktopPrefs.proxyPort, portNumber ?: 0)
        .with(DesktopPrefs.proxyUser, user.trim()).with(DesktopPrefs.proxyPassword, password).with(DesktopPrefs.proxyBypass, bypass.trim())
        .with(DesktopPrefs.proxyMode, ProxyMode.MANUAL)

    companion object {
        fun of(settings: Settings) = ProxyForm(
            settings[DesktopPrefs.proxyType],
            settings[DesktopPrefs.proxyHost],
            settings[DesktopPrefs.proxyPort].takeIf { it > 0 }?.toString().orEmpty(),
            settings[DesktopPrefs.proxyUser],
            settings[DesktopPrefs.proxyPassword],
            settings[DesktopPrefs.proxyBypass],
        )

        /** "host:port" for the settings row; null while none is set. */
        fun summary(settings: Settings): String? = of(settings).takeIf { it.isComplete }?.let { "${it.host.trim()}:${it.portNumber}" }
    }
}

/** Settings › General's proxy rows, on the desktop: System, None or Manual, and the proxy Manual goes through. */
@Composable
internal fun ProxyRows(settings: Settings, vm: SettingsViewModel, focus: String?) {
    var editing by remember { mutableStateOf(false) }
    val mode = settings[DesktopPrefs.proxyMode]
    ChoiceRow(
        stringResource(Res.string.settings_proxy),
        stringResource(Res.string.settings_proxy_description),
        ProxyMode.entries,
        mode,
        label = { choice ->
            when (choice) {
                ProxyMode.SYSTEM -> stringResource(Res.string.settings_system)
                ProxyMode.NONE -> stringResource(Res.string.settings_proxy_none)
                ProxyMode.MANUAL -> stringResource(Res.string.settings_proxy_manual)
            }
        },
        focused = focus == DesktopPrefs.proxyMode.key,
    ) { choice ->
        // Manual without a proxy would load nothing: its dialog opens first, and Save chooses it.
        if (choice == ProxyMode.MANUAL && ProxyForm.summary(settings) == null) editing = true else vm.set(DesktopPrefs.proxyMode, choice)
    }
    if (mode == ProxyMode.MANUAL) {
        NavigationRow(
            stringResource(Res.string.settings_proxy_server),
            ProxyForm.summary(settings) ?: stringResource(Res.string.settings_proxy_server_unset),
            focused = focus == DesktopPrefs.proxyHost.key,
        ) { editing = true }
    }
    if (editing) {
        ProxyDialog(ProxyForm.of(settings), onDismiss = { editing = false }) { form ->
            editing = false
            vm.update(form::saved)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyDialog(initial: ProxyForm, onDismiss: () -> Unit, onSave: (ProxyForm) -> Unit) {
    var form by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_proxy_server)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = FormSpacing) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ProxyType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = form.type == type,
                            onClick = { form = form.copy(type = type) },
                            shape = SegmentedButtonDefaults.itemShape(index, ProxyType.entries.size),
                        ) { Text(stringResource(if (type == ProxyType.HTTP) Res.string.settings_proxy_type_http else Res.string.settings_proxy_type_socks)) }
                    }
                }
                if (form.type == ProxyType.SOCKS5) {
                    Text(stringResource(Res.string.settings_proxy_socks_videos), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ProxyField(form.host, Res.string.settings_proxy_host, KeyboardType.Uri) { form = form.copy(host = it) }
                ProxyField(form.port, Res.string.settings_proxy_port, KeyboardType.Number) { text -> form = form.copy(port = text.filter(Char::isDigit).take(5)) }
                ProxyField(form.user, Res.string.settings_sync_username) { form = form.copy(user = it) }
                OutlinedTextField(
                    value = form.password,
                    onValueChange = { form = form.copy(password = it) },
                    label = { Text(stringResource(Res.string.settings_sync_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.bypass,
                    onValueChange = { form = form.copy(bypass = it) },
                    label = { Text(stringResource(Res.string.settings_proxy_bypass)) },
                    placeholder = { Text(stringResource(Res.string.settings_proxy_bypass_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(form) }, enabled = form.isComplete) { Text(stringResource(Res.string.settings_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun ProxyField(value: String, label: StringResource, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}
