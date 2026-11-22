package com.aimdi.xtanative.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.data.ReaderTranslation
import com.aimdi.xtanative.services.translation.TranslationConfig
import com.aimdi.xtanative.services.translation.TranslationException
import com.aimdi.xtanative.services.translation.TranslationFailure
import com.aimdi.xtanative.services.translation.TranslationService
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Translation (reader_translation_settings.dart): which service
 * translates posts of every network (none until the reader picks one), into
 * which language, and for DeepL and LibreTranslate the server and its key,
 * with a Test that asks the server what it translates; for LibreTranslate
 * a few public servers to try and the note that what is translated goes
 * there. Nothing is sent until the reader taps Test or Translate.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TranslationSettings(title: String, vm: SettingsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val current = ReaderTranslation.configOf(settings)
    var form by remember { mutableStateOf(TranslationForm.of(ReaderTranslation.configOf(vm.settings.value))) }
    val savedText = stringResource(Res.string.translation_saved)
    val invalidText = stringResource(if (form.service == TranslationService.AI) Res.string.translation_ai_not_configured else Res.string.translation_invalid)
    val clearedText = stringResource(Res.string.translation_cache_cleared)
    SettingsPage(title, onBack, snackbar) { padding ->
        SettingsList(padding) {
            SettingsHeader(null, stringResource(Res.string.translation_privacy))
            ChoiceRow(
                title = stringResource(Res.string.translation_provider),
                options = TranslationService.entries,
                selected = form.service,
                label = { serviceName(it) },
            ) { form = form.withService(it) }
            ChoiceRow(
                title = stringResource(Res.string.translation_target),
                options = TranslationForm.targets,
                selected = form.target,
                label = { code -> if (code.isEmpty()) stringResource(Res.string.translation_target_app) else SettingsChoices.languageName(code) ?: code },
            ) { form = form.copy(target = it) }
            when {
                form.asksServer -> ServerFields(form, onChange = { form = it }) {
                    val config = form.draft(current)
                    form = form.copy(test = TranslationTest.Testing)
                    scope.launch {
                        form = form.copy(
                            test = try {
                                TranslationTest.Reached(vm.graph.translation.languages(config).size)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: TranslationException) {
                                TranslationTest.Failed(e.failure)
                            },
                        )
                    }
                }
                form.service == TranslationService.AI -> {
                    SettingsHeader(null, stringResource(Res.string.translation_ai_description))
                    NavigationRow(
                        stringResource(Res.string.settings_ai),
                        if (current.ai.isConfigured) current.ai.model else stringResource(Res.string.translation_ai_not_configured),
                        Icons.Outlined.Create,
                    ) { onOpen(SettingsRoutes.of(SettingsSection.AI)) }
                }
            }
            Button(
                onClick = {
                    val next = form.saved(vm.settings.value, current)
                    if (next != null) vm.update { form.saved(it, current) ?: it }
                    scope.launch { snackbar.showSnackbar(if (next != null) savedText else invalidText) }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text(stringResource(Res.string.settings_save)) }
            NavigationRow(stringResource(Res.string.translation_clear_cache), icon = Icons.Outlined.Delete, chevron = false) {
                scope.launch {
                    vm.graph.translation.clear()
                    snackbar.showSnackbar(clearedText)
                }
            }
        }
    }
}

/** The server's address with its examples (LibreTranslate's) and its key, and Test with what it found. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerFields(form: TranslationForm, onChange: (TranslationForm) -> Unit, onTest: () -> Unit) {
    val libre = form.service == TranslationService.LIBRE
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = FormSpacing) {
        OutlinedTextField(
            value = form.endpoint,
            onValueChange = { onChange(form.copy(endpoint = it, test = null)) },
            label = { Text(stringResource(Res.string.translation_endpoint)) },
            placeholder = if (libre) null else ({ Text(TranslationConfig.DEEPL_FREE) }),
            supportingText = { Text(stringResource(if (libre) Res.string.translation_endpoint_libre_help else Res.string.translation_endpoint_deepl_help)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        if (libre) {
            Text(stringResource(Res.string.translation_examples), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TranslationForm.LIBRE_EXAMPLES.forEach { address ->
                    AssistChip(onClick = { onChange(form.withExample(address)) }, label = { Text(address.removePrefix("https://")) })
                }
            }
            Text(stringResource(Res.string.translation_examples_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = form.apiKey,
            onValueChange = { onChange(form.copy(apiKey = it, test = null)) },
            label = { Text(stringResource(Res.string.settings_ai_api_key)) },
            supportingText = if (libre) ({ Text(stringResource(Res.string.translation_key_optional)) }) else null,
            singleLine = true,
            visualTransformation = if (form.keyHidden) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = {
                TextButton(onClick = { onChange(form.copy(keyHidden = !form.keyHidden)) }) {
                    Text(stringResource(if (form.keyHidden) Res.string.settings_show else Res.string.settings_hide))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onTest, enabled = form.canTest) { Text(stringResource(Res.string.translation_test)) }
            when (val test = form.test) {
                TranslationTest.Testing -> {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(Res.string.translation_testing), style = MaterialTheme.typography.bodySmall)
                }
                is TranslationTest.Reached -> Text(stringResource(Res.string.translation_test_ok, test.languages), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                is TranslationTest.Failed -> Text(stringResource(translationFailureText(test.failure)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                null -> Unit
            }
        }
    }
}

@Composable
private fun serviceName(service: TranslationService): String = when (service) {
    TranslationService.OFF -> stringResource(Res.string.translation_provider_off)
    TranslationService.DEEPL -> "DeepL"
    TranslationService.LIBRE -> "LibreTranslate"
    TranslationService.AI -> stringResource(Res.string.translation_provider_ai)
}

/** Why a translation or a Test failed, in words. */
internal fun translationFailureText(failure: TranslationFailure): StringResource = when (failure) {
    TranslationFailure.NOT_CONFIGURED -> Res.string.translation_not_configured
    TranslationFailure.TOO_LONG -> Res.string.translation_bad_answer
    TranslationFailure.UNREACHABLE -> Res.string.translation_unreachable
    TranslationFailure.REFUSED -> Res.string.translation_refused
    TranslationFailure.RATE_LIMITED -> Res.string.translation_rate_limited
    TranslationFailure.UNSUPPORTED_LANGUAGE -> Res.string.translation_unsupported
    TranslationFailure.BAD_ANSWER -> Res.string.translation_bad_answer
}
