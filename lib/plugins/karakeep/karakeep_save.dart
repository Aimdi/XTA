import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/karakeep/karakeep_client.dart';

/// One-tap "save this link to Karakeep", with the feedback the user needs:
/// saved, already there, or exactly what went wrong.
///
/// Returns true when the link reached Karakeep (including when it was already
/// bookmarked), so callers can skip their own fallback.
Future<bool> saveToKarakeep(
  BuildContext context, {
  required String url,
  String? title,
}) async {
  final prefs = PrefService.of(context, listen: false);
  final l10n = L10n.of(context);
  final messenger = ScaffoldMessenger.of(context);

  final serverUrl = prefs.get<String>(optionPluginKarakeepServerUrl) ?? '';
  final apiKey = prefs.get<String>(optionPluginKarakeepApiKey) ?? '';

  void report(String message) {
    messenger.showSnackBar(SnackBar(content: Text(message)));
  }

  try {
    final result = await context.read<KarakeepClient>().saveLink(
          baseUrl: serverUrl,
          apiKey: apiKey,
          url: url,
          title: title,
        );
    report(switch (result.outcome) {
      KarakeepSaveOutcome.saved => l10n.plugin_karakeep_saved,
      KarakeepSaveOutcome.alreadySaved => l10n.plugin_karakeep_already_saved,
    });
    return true;
  } on KarakeepException catch (e) {
    report(switch (e.kind) {
      KarakeepErrorKind.notConfigured => l10n.plugin_karakeep_not_configured,
      KarakeepErrorKind.unauthorized => l10n.plugin_karakeep_error_unauthorized,
      KarakeepErrorKind.badServer => l10n.plugin_karakeep_error_server_url,
      KarakeepErrorKind.network => l10n.plugin_karakeep_error_network,
      KarakeepErrorKind.server => l10n.plugin_karakeep_error_generic,
    });
    return false;
  }
}

/// Whether the plugin is on and has somewhere to save to.
bool karakeepReady(BasePrefService prefs) =>
    prefs.get(optionPluginKarakeepEnabled) == true &&
    (prefs.get<String>(optionPluginKarakeepServerUrl) ?? '').trim().isNotEmpty &&
    (prefs.get<String>(optionPluginKarakeepApiKey) ?? '').trim().isNotEmpty;

/// True when the plugin is enabled at all, configured or not — the save entry
/// is still offered so the user is told what is missing instead of wondering
/// where the button went.
bool karakeepEnabled(BasePrefService prefs) => prefs.get(optionPluginKarakeepEnabled) == true;
