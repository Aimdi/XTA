import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/reading/reader_translation_config.dart';
import 'package:xta/reading/reader_translation_store.dart';
import 'package:xta/settings/_ai.dart';
import 'package:xta/settings/settings_chrome.dart';
import 'package:xta/utils/local_json_store.dart';
import 'package:xta/utils/native_locale_names.dart';

Future<void> openReaderTranslationSettings(BuildContext context) =>
    Navigator.push<void>(context, MaterialPageRoute(builder: (_) => const ReaderTranslationSettings()));

String readerTranslationProviderLabel(L10n l10n, ReaderTranslationProvider provider) => switch (provider) {
  ReaderTranslationProvider.disabled => l10n.translation_provider_off,
  ReaderTranslationProvider.deepl => 'DeepL',
  ReaderTranslationProvider.libre => 'LibreTranslate',
  ReaderTranslationProvider.ai => l10n.translation_provider_ai,
};

class _TranslationForm {
  final ReaderTranslationProvider provider;
  final String target;
  final bool obscureKey;
  final bool saving;
  const _TranslationForm(this.provider, this.target, {this.obscureKey = true, this.saving = false});
  _TranslationForm copyWith({ReaderTranslationProvider? provider, String? target, bool? obscureKey, bool? saving}) =>
      _TranslationForm(
        provider ?? this.provider,
        target ?? this.target,
        obscureKey: obscureKey ?? this.obscureKey,
        saving: saving ?? this.saving,
      );
}

class _TranslationFormStore extends Store<_TranslationForm> {
  _TranslationFormStore(ReaderTranslationConfig config) : super(_TranslationForm(config.provider, config.target));
  void provider(ReaderTranslationProvider value) => update(state.copyWith(provider: value));
  void target(String value) => update(state.copyWith(target: value));
  void toggleKey() => update(state.copyWith(obscureKey: !state.obscureKey));
  void saving(bool value) => update(state.copyWith(saving: value));
}

/// Which service translates, into which language, and with which key. Nothing is sent until the reader asks.
class ReaderTranslationSettings extends StatefulWidget {
  final JsonStore? cacheStorage;
  const ReaderTranslationSettings({super.key, this.cacheStorage});

  @override
  State<ReaderTranslationSettings> createState() => _ReaderTranslationSettingsState();
}

class _ReaderTranslationSettingsState extends State<ReaderTranslationSettings> {
  late final ReaderTranslationConfigStore _config;
  late final _TranslationFormStore _form;
  late final TextEditingController _endpoint;
  late final TextEditingController _apiKey;

  @override
  void initState() {
    super.initState();
    _config = ReaderTranslationConfigStore.forPrefs(PrefService.of(context, listen: false));
    _form = _TranslationFormStore(_config.state);
    _endpoint = TextEditingController(text: _config.state.endpoint);
    _apiKey = TextEditingController(text: _config.state.apiKey);
  }

  @override
  void dispose() {
    _form.destroy();
    _endpoint.dispose();
    _apiKey.dispose();
    super.dispose();
  }

  ReaderTranslationConfig get _draft => ReaderTranslationConfig(
    provider: _form.state.provider,
    endpoint: _form.state.provider == ReaderTranslationProvider.ai ? '' : _endpoint.text.trim(),
    apiKey: _form.state.provider == ReaderTranslationProvider.ai ? _config.state.apiKey : _apiKey.text.trim(),
    target: _form.state.target,
    ai: _config.state.ai,
  );

  Future<void> _save() async {
    final l10n = L10n.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final draft = _draft;
    if (!readerTranslationConfigValid(draft)) {
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(_invalidReason(l10n, draft))));
      return;
    }
    _form.saving(true);
    final saved = await _config.save(draft);
    if (!mounted) return;
    _form.saving(false);
    messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(saved ? l10n.translation_saved : l10n.translation_save_failed)));
  }

  String _invalidReason(L10n l10n, ReaderTranslationConfig draft) =>
      draft.provider == ReaderTranslationProvider.ai ? l10n.translation_ai_not_configured : l10n.translation_invalid;

  Future<void> _clearCache() async {
    final message = L10n.of(context).translation_cache_cleared;
    final messenger = ScaffoldMessenger.of(context);
    final storage = widget.cacheStorage;
    await (storage == null ? ReaderTranslationCache.shared : ReaderTranslationCache.forStorage(storage)).clear();
    messenger.showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return SettingsPageScaffold(
      title: l10n.translation_title,
      body: ScopedBuilder<_TranslationFormStore, _TranslationForm>(
        store: _form,
        onState: (context, form) => SettingsList(
          children: [
            SettingsSection(
              description: l10n.translation_privacy,
              children: [
                _dropdown<ReaderTranslationProvider>(
                  key: const ValueKey('translation-provider'),
                  label: l10n.translation_provider,
                  value: form.provider,
                  items: {
                    for (final provider in ReaderTranslationProvider.values)
                      provider: readerTranslationProviderLabel(l10n, provider),
                  },
                  onChanged: form.saving ? null : _form.provider,
                ),
                _dropdown<String>(
                  key: const ValueKey('translation-target'),
                  label: l10n.translation_target,
                  value: form.target,
                  items: {
                    '': l10n.translation_target_app,
                    for (final locale in L10n.delegate.supportedLocales)
                      locale.toString(): nativeLocaleNameOf(locale.toString()) ?? locale.toString(),
                  },
                  onChanged: form.saving ? null : _form.target,
                ),
              ],
            ),
            ..._providerFields(context, form),
            Padding(
              padding: const EdgeInsets.all(16),
              child: FilledButton(
                key: const ValueKey('translation-save'),
                style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(48)),
                onPressed: form.saving ? null : _save,
                child: Text(l10n.save),
              ),
            ),
            if (form.saving) const LinearProgressIndicator(),
            SettingsSection(
              children: [
                SettingsRow(icon: Icons.delete_sweep_outlined, title: l10n.translation_clear_cache, onTap: _clearCache),
              ],
            ),
          ],
        ),
      ),
    );
  }

  List<Widget> _providerFields(BuildContext context, _TranslationForm form) {
    final l10n = L10n.of(context);
    return switch (form.provider) {
      ReaderTranslationProvider.disabled => const [],
      ReaderTranslationProvider.ai => [
        ScopedBuilder<ReaderTranslationConfigStore, ReaderTranslationConfig>(
          store: _config,
          onState: (context, config) => SettingsSection(
            description: l10n.translation_ai_description,
            children: [
              SettingsNavigationRow(
                icon: Icons.auto_awesome_outlined,
                title: l10n.ai_provider,
                value: config.ai.isConfigured ? config.ai.model : null,
                description: config.ai.isConfigured ? null : l10n.translation_ai_not_configured,
                onTap: () =>
                    Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const SettingsAiFragment())),
              ),
            ],
          ),
        ),
      ],
      ReaderTranslationProvider.deepl || ReaderTranslationProvider.libre => [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
          child: TextField(
            key: const ValueKey('translation-endpoint'),
            controller: _endpoint,
            keyboardType: TextInputType.url,
            autocorrect: false,
            decoration: InputDecoration(
              labelText: l10n.translation_endpoint,
              hintText: form.provider == ReaderTranslationProvider.deepl ? readerDeeplFreeBaseUrl : null,
              helperText: form.provider == ReaderTranslationProvider.deepl
                  ? l10n.translation_endpoint_deepl_help
                  : l10n.translation_endpoint_libre_help,
              helperMaxLines: 4,
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
          child: TextField(
            key: const ValueKey('translation-key'),
            controller: _apiKey,
            obscureText: form.obscureKey,
            autocorrect: false,
            enableSuggestions: false,
            decoration: InputDecoration(
              labelText: l10n.ai_api_key,
              helperText: form.provider == ReaderTranslationProvider.libre ? l10n.translation_key_optional : null,
              suffixIcon: IconButton(
                tooltip: form.obscureKey ? l10n.show : l10n.hide,
                icon: Icon(form.obscureKey ? Icons.visibility_off : Icons.visibility),
                onPressed: _form.toggleKey,
              ),
            ),
          ),
        ),
      ],
    };
  }

  Widget _dropdown<T>({
    required Key key,
    required String label,
    required T value,
    required Map<T, String> items,
    required ValueChanged<T>? onChanged,
  }) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(label, style: Theme.of(context).textTheme.titleSmall),
        ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 48),
          child: DropdownButton<T>(
            key: key,
            value: items.containsKey(value) ? value : items.keys.first,
            isExpanded: true,
            itemHeight: null,
            items: [for (final entry in items.entries) DropdownMenuItem<T>(value: entry.key, child: Text(entry.value))],
            onChanged: onChanged == null ? null : (value) => value == null ? null : onChanged(value),
          ),
        ),
      ],
    ),
  );
}
