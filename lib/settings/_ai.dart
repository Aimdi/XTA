import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/settings_chrome.dart';

/// Where an AI feature should send its requests, if the reader wants one.
///
/// Presets fill the provider's API root and a model; custom servers stay editable.
/// Empty fields keep every AI feature off — XTA never ships a key of its own.
class SettingsAiFragment extends StatefulWidget {
  const SettingsAiFragment({super.key});

  @override
  State<SettingsAiFragment> createState() => _SettingsAiFragmentState();
}

class _SettingsAiFragmentState extends State<SettingsAiFragment> {
  late final TextEditingController _baseUrlController;
  late final TextEditingController _keyController;
  late final TextEditingController _modelController;
  late final _model = _AiSettingsStore(_baseUrlController.text);

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    _baseUrlController = TextEditingController(text: prefs.get<String>(optionAiBaseUrl) ?? '');
    _keyController = TextEditingController(text: prefs.get<String>(optionAiApiKey) ?? '');
    _modelController = TextEditingController(text: prefs.get<String>(optionAiModel) ?? '');
  }

  @override
  void dispose() {
    _model.destroy();
    _baseUrlController.dispose();
    _keyController.dispose();
    _modelController.dispose();
    super.dispose();
  }

  void _applyPreset(String baseUrl, String model) {
    final sameProvider = Uri.tryParse(_baseUrlController.text.trim())?.host == Uri.parse(baseUrl).host;
    if (!sameProvider) _keyController.clear();
    _baseUrlController.text = baseUrl;
    if (!sameProvider || _modelController.text.trim().isEmpty) _modelController.text = model;
    _model.addressChanged(baseUrl);
  }

  Future<void> _save() async {
    final prefs = PrefService.of(context, listen: false);
    await prefs.set(optionAiBaseUrl, _baseUrlController.text.trim());
    await prefs.set(optionAiApiKey, _keyController.text.trim());
    await prefs.set(optionAiModel, _modelController.text.trim());

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(L10n.of(context).ai_saved)));
    }
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<_AiSettingsStore, _AiSettingsState>(
    store: _model,
    onState: (context, state) => _buildSettings(context, state),
  );

  Widget _buildSettings(BuildContext context, _AiSettingsState state) {
    final l10n = L10n.of(context);
    final host = Uri.tryParse(state.address.trim())?.host.toLowerCase();
    final grok = host == 'api.x.ai';
    final openRouter = host == 'openrouter.ai';

    return SettingsPageScaffold(
      title: l10n.ai_provider,
      body: SettingsList(
        padding: const EdgeInsets.all(16),
        children: [
          Text(l10n.ai_provider_description, style: Theme.of(context).textTheme.bodyMedium),
          const SizedBox(height: 16),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              ActionChip(
                avatar: const Icon(Icons.auto_awesome, size: 18),
                label: Text(l10n.ai_preset_grok),
                onPressed: () => _applyPreset(aiGrokBaseUrl, aiGrokModel),
              ),
              ActionChip(
                label: Text(l10n.ai_preset_openrouter),
                onPressed: () => _applyPreset(aiOpenRouterBaseUrl, aiOpenRouterModel),
              ),
              ActionChip(
                label: Text(l10n.ai_preset_openai),
                onPressed: () => _applyPreset(aiOpenAiBaseUrl, aiOpenAiModel),
              ),
            ],
          ),
          const SizedBox(height: 24),
          TextField(
            controller: _baseUrlController,
            keyboardType: TextInputType.url,
            autocorrect: false,
            onChanged: _model.addressChanged,
            decoration: InputDecoration(
              labelText: l10n.ai_base_url,
              hintText: aiGrokBaseUrl,
              helperText: l10n.ai_base_url_description,
              helperMaxLines: 3,
            ),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _keyController,
            obscureText: state.obscureKey,
            autocorrect: false,
            enableSuggestions: false,
            decoration: InputDecoration(
              labelText: l10n.ai_api_key,
              helperText: openRouter ? l10n.ai_openrouter_key_hint : (grok ? l10n.ai_grok_key_hint : null),
              helperMaxLines: 3,
              suffixIcon: IconButton(
                tooltip: state.obscureKey ? l10n.show : l10n.hide,
                icon: Icon(state.obscureKey ? Icons.visibility_off : Icons.visibility),
                onPressed: _model.toggleKeyVisibility,
              ),
            ),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _modelController,
            autocorrect: false,
            decoration: InputDecoration(
              labelText: l10n.ai_model,
              hintText: openRouter ? aiOpenRouterModel : (grok ? aiGrokModel : aiOpenAiModel),
              helperText: openRouter ? l10n.ai_openrouter_model_hint : null,
              helperMaxLines: 3,
            ),
          ),
          const SizedBox(height: 24),
          FilledButton(onPressed: _save, child: Text(l10n.save)),
        ],
      ),
    );
  }
}

class _AiSettingsState {
  final String address;
  final bool obscureKey;
  const _AiSettingsState(this.address, {this.obscureKey = true});
}

class _AiSettingsStore extends Store<_AiSettingsState> {
  _AiSettingsStore(String address) : super(_AiSettingsState(address));
  void addressChanged(String value) => update(_AiSettingsState(value, obscureKey: state.obscureKey));
  void toggleKeyVisibility() => update(_AiSettingsState(state.address, obscureKey: !state.obscureKey));
}
