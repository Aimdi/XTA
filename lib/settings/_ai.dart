import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/settings_chrome.dart';

/// Where an AI feature should send its requests, if the reader wants one.
///
/// The Grok chip fills xAI's OpenAI-compatible root so only a key is pasted.
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
  var _obscureKey = true;

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    _baseUrlController = TextEditingController(
      text: prefs.get<String>(optionAiBaseUrl) ?? '',
    );
    _keyController = TextEditingController(
      text: prefs.get<String>(optionAiApiKey) ?? '',
    );
    _modelController = TextEditingController(
      text: prefs.get<String>(optionAiModel) ?? '',
    );
  }

  @override
  void dispose() {
    _baseUrlController.dispose();
    _keyController.dispose();
    _modelController.dispose();
    super.dispose();
  }

  void _applyGrok() {
    setState(() {
      _baseUrlController.text = aiGrokBaseUrl;
      _modelController.text = aiGrokModel;
    });
  }

  void _applyOpenAi() {
    setState(() {
      _baseUrlController.text = aiOpenAiBaseUrl;
      if (_modelController.text.trim().isEmpty ||
          _modelController.text.toLowerCase().startsWith('grok')) {
        _modelController.text = aiOpenAiModel;
      }
    });
  }

  Future<void> _save() async {
    final prefs = PrefService.of(context, listen: false);
    await prefs.set(optionAiBaseUrl, _baseUrlController.text.trim());
    await prefs.set(optionAiApiKey, _keyController.text.trim());
    await prefs.set(optionAiModel, _modelController.text.trim());

    if (mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(L10n.of(context).ai_saved)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final grok = _baseUrlController.text.toLowerCase().contains('api.x.ai');

    return SettingsPageScaffold(
      title: l10n.ai_provider,
      body: SettingsList(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            l10n.ai_provider_description,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              ActionChip(
                avatar: const Icon(Icons.auto_awesome, size: 18),
                label: Text(l10n.ai_preset_grok),
                onPressed: _applyGrok,
              ),
              ActionChip(
                label: Text(l10n.ai_preset_openai),
                onPressed: _applyOpenAi,
              ),
            ],
          ),
          const SizedBox(height: 24),
          TextField(
            controller: _baseUrlController,
            keyboardType: TextInputType.url,
            autocorrect: false,
            onChanged: (_) => setState(() {}),
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
            obscureText: _obscureKey,
            autocorrect: false,
            enableSuggestions: false,
            decoration: InputDecoration(
              labelText: l10n.ai_api_key,
              helperText: grok ? l10n.ai_grok_key_hint : null,
              helperMaxLines: 3,
              suffixIcon: IconButton(
                icon: Icon(
                  _obscureKey ? Icons.visibility_off : Icons.visibility,
                ),
                onPressed: () => setState(() => _obscureKey = !_obscureKey),
              ),
            ),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _modelController,
            autocorrect: false,
            decoration: InputDecoration(
              labelText: l10n.ai_model,
              hintText: aiGrokModel,
            ),
          ),
          const SizedBox(height: 24),
          FilledButton(onPressed: _save, child: Text(l10n.save)),
        ],
      ),
    );
  }
}
