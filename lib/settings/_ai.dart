import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:quax/constants.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/settings/settings_chrome.dart';
import 'package:quax/settings/settings_view_store.dart';
import 'package:quax/tweet/tweet_chrome.dart';

/// Where an AI feature should send its requests, if the reader wants one.
///
/// Nothing calls this yet — it is the connection details, kept on the device,
/// so that features built on top of it never have to ship a key of QuaX's own
/// or route a reader's posts through a service they did not choose.
class SettingsAiFragment extends StatefulWidget {
  const SettingsAiFragment({super.key});

  @override
  State<SettingsAiFragment> createState() => _SettingsAiFragmentState();
}

class _SettingsAiFragmentState extends State<SettingsAiFragment> {
  late final TextEditingController _baseUrlController;
  late final TextEditingController _keyController;
  late final TextEditingController _modelController;
  late final SettingsValueStore<bool> _obscureStore;

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
    _obscureStore = SettingsValueStore(true);
  }

  @override
  void dispose() {
    _baseUrlController.dispose();
    _keyController.dispose();
    _modelController.dispose();
    _obscureStore.destroy();
    super.dispose();
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

    return SettingsPageScaffold(
      title: l10n.ai_provider,
      body: SettingsList(
        children: [
          SettingsSection(
            description: l10n.ai_provider_description,
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: kTweetHorizontalPadding,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    TextField(
                      controller: _baseUrlController,
                      keyboardType: TextInputType.url,
                      autocorrect: false,
                      decoration: InputDecoration(
                        labelText: l10n.ai_base_url,
                        hintText: 'https://api.openai.com/v1',
                        helperText: l10n.ai_base_url_description,
                        helperMaxLines: 3,
                      ),
                    ),
                    const SizedBox(height: kTweetSpace4),
                    ScopedBuilder<SettingsValueStore<bool>, bool>(
                      store: _obscureStore,
                      onState: (_, obscure) => TextField(
                        controller: _keyController,
                        obscureText: obscure,
                        autocorrect: false,
                        enableSuggestions: false,
                        decoration: InputDecoration(
                          labelText: l10n.ai_api_key,
                          suffixIcon: IconButton(
                            tooltip: obscure ? l10n.show : l10n.hide,
                            icon: Icon(
                              obscure ? Icons.visibility_off : Icons.visibility,
                            ),
                            onPressed: () => _obscureStore.setValue(!obscure),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: kTweetSpace4),
                    TextField(
                      controller: _modelController,
                      autocorrect: false,
                      decoration: InputDecoration(
                        labelText: l10n.ai_model,
                        hintText: 'gpt-4o-mini',
                      ),
                    ),
                    const SizedBox(height: kTweetSpace6),
                    Align(
                      alignment: Alignment.centerRight,
                      child: FilledButton(
                        onPressed: _save,
                        child: Text(l10n.save),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
