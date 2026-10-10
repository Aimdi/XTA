import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_auth.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_login_webview.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_account.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_content.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_mute.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_viewing.dart';
import 'package:xta/ui/errors.dart';

export 'package:xta/plugins/pixiv/pixiv_settings_content.dart' show PixivHideAiSwitch, PixivPrefSwitch;

String pixivErrorMessage(L10n l10n, Object error) {
  if (error is! PixivException) {
    return l10n.plugin_pixiv_error_network;
  }
  return switch (error.kind) {
    PixivErrorKind.notConfigured => l10n.plugin_pixiv_not_configured,
    PixivErrorKind.network => l10n.plugin_pixiv_error_network,
    PixivErrorKind.unauthorized => l10n.plugin_pixiv_error_unauthorized,
    PixivErrorKind.rateLimited => l10n.plugin_pixiv_error_rate_limited,
    PixivErrorKind.notFound => l10n.plugin_pixiv_error_not_found,
    PixivErrorKind.badResponse => l10n.plugin_pixiv_error_response,
  };
}

/// Opens the Pixiv login webview and stores tokens on success.
///
/// Returns the signed-in user, or null when cancelled or failed.
Future<PixivAuthUser?> runPixivSignIn(BuildContext context) async {
  final pkce = PixivAuth.generatePkce();
  final code = await Navigator.push<String>(
    context,
    MaterialPageRoute(builder: (_) => PixivLoginWebview(codeChallenge: pkce.challenge)),
  );
  if (code == null || !context.mounted) {
    return null;
  }

  try {
    final tokens = await PixivAuth().exchangeCode(code: code, codeVerifier: pkce.verifier);
    if (!context.mounted) {
      return null;
    }
    final user = await context.read<PixivClient>().applyLoginTokens(tokens);
    if (context.mounted) {
      showSnackBar(context, icon: '✅', message: L10n.of(context).plugin_pixiv_signed_in(user.displayName));
    }
    return user;
  } on PixivException catch (_) {
    if (context.mounted) {
      showSnackBar(context, icon: '🔒', message: L10n.of(context).plugin_pixiv_sign_in_failed);
    }
    return null;
  }
}

/// The plugin's settings page: an intro over one section per concern. A
/// feature with settings of its own adds its section to this list.
const pixivSettingsSections = <Widget>[
  PixivAccountSettings(),
  PixivContentSettings(),
  PixivViewingSettings(),
  PixivMuteSettings(),
];

class PixivSettingsScreen extends StatelessWidget {
  const PixivSettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_pixiv_title)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(l10n.plugin_pixiv_settings_intro, style: Theme.of(context).textTheme.bodyMedium),
          const SizedBox(height: 20),
          for (final (index, section) in pixivSettingsSections.indexed) ...[
            if (index > 0) const SizedBox(height: 28),
            section,
          ],
        ],
      ),
    );
  }
}
