import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/deepmarks/deepmarks_client.dart';
import 'package:xta/plugins/deepmarks/nostr_event.dart';

/// One-tap "save this link to Deepmarks": sign the bookmark on the device, hand
/// the finished event to the API, and say plainly what happened.
///
/// Returns true when Deepmarks accepted the event.
Future<bool> saveToDeepmarks(
  BuildContext context, {
  required String url,
  String? title,
  List<String> topics = const [],
}) async {
  final prefs = PrefService.of(context, listen: false);
  final l10n = L10n.of(context);
  final messenger = ScaffoldMessenger.of(context);

  void report(String message) => messenger.showSnackBar(SnackBar(content: Text(message)));

  final apiKey = prefs.get<String>(optionPluginDeepmarksApiKey) ?? '';
  final secret = prefs.get<String>(optionPluginDeepmarksSecretKey) ?? '';
  if (apiKey.trim().isEmpty || secret.trim().isEmpty) {
    report(l10n.plugin_deepmarks_not_configured);
    return false;
  }

  final String secretHex;
  try {
    secretHex = normaliseNostrSecretKey(secret);
  } on NostrKeyException {
    report(l10n.plugin_deepmarks_error_secret_key);
    return false;
  }

  try {
    final event = signWebBookmark(secretKeyHex: secretHex, url: url, title: title, topics: topics);
    final result = await context.read<DeepmarksClient>().publishBookmark(
          baseUrl: prefs.get<String>(optionPluginDeepmarksApiBase) ?? '',
          apiKey: apiKey,
          event: event,
        );

    report(result.reachedNoRelay ? l10n.plugin_deepmarks_saved_no_relay : l10n.plugin_deepmarks_saved);
    return true;
  } on DeepmarksException catch (e) {
    report(deepmarksErrorMessage(l10n, e.kind));
    return false;
  }
}

/// User-facing wording for each failure, so a save never fails with a shrug.
String deepmarksErrorMessage(L10n l10n, DeepmarksErrorKind kind) => switch (kind) {
      DeepmarksErrorKind.notConfigured => l10n.plugin_deepmarks_not_configured,
      DeepmarksErrorKind.badSecretKey => l10n.plugin_deepmarks_error_secret_key,
      DeepmarksErrorKind.unauthorized => l10n.plugin_deepmarks_error_unauthorized,
      DeepmarksErrorKind.notLifetimeMember => l10n.plugin_deepmarks_error_lifetime,
      DeepmarksErrorKind.keyMismatch => l10n.plugin_deepmarks_error_key_mismatch,
      DeepmarksErrorKind.rejected => l10n.plugin_deepmarks_error_rejected,
      DeepmarksErrorKind.rateLimited => l10n.plugin_deepmarks_error_rate_limited,
      DeepmarksErrorKind.badServer => l10n.plugin_deepmarks_error_server,
      DeepmarksErrorKind.network => l10n.plugin_deepmarks_error_network,
    };

/// Whether the plugin is switched on, regardless of whether it is set up.
bool deepmarksEnabled(BasePrefService prefs) => prefs.get(optionPluginDeepmarksEnabled) == true;
