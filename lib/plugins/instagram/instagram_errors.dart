import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/instagram/instagram_client.dart';

String instagramErrorMessage(L10n l10n, Object? error) {
  if (error is! InstagramException) {
    return l10n.plugin_instagram_error_network;
  }
  return switch (error.kind) {
    InstagramErrorKind.network => l10n.plugin_instagram_error_network,
    InstagramErrorKind.notFound => l10n.plugin_instagram_error_not_found,
    InstagramErrorKind.privateAccount => l10n.plugin_instagram_error_private,
    InstagramErrorKind.rateLimited => l10n.plugin_instagram_error_rate_limited,
    InstagramErrorKind.loginRequired => l10n.plugin_instagram_error_login,
    InstagramErrorKind.badResponse => l10n.plugin_instagram_error_response,
  };
}
