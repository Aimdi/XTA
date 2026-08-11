import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_client.dart';

String booruErrorMessage(L10n l10n, Object? error) {
  if (error is! BooruException) {
    return l10n.plugin_booru_error_network;
  }
  return switch (error.kind) {
    BooruErrorKind.notConfigured => l10n.plugin_booru_error_not_configured,
    BooruErrorKind.network => l10n.plugin_booru_error_network,
    BooruErrorKind.unauthorized => l10n.plugin_booru_error_unauthorized,
    BooruErrorKind.rateLimited => l10n.plugin_booru_error_rate_limited,
    BooruErrorKind.notFound => l10n.plugin_booru_error_not_found,
    BooruErrorKind.badResponse => l10n.plugin_booru_error_response,
  };
}
