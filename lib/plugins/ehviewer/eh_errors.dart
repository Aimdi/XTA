import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';

String ehErrorMessage(L10n l10n, Object? error) {
  if (error is! EhException) {
    return l10n.plugin_eh_error_network;
  }
  return switch (error.kind) {
    EhErrorKind.notConfigured => l10n.plugin_eh_error_not_configured,
    EhErrorKind.network => l10n.plugin_eh_error_network,
    EhErrorKind.unauthorized => l10n.plugin_eh_error_unauthorized,
    EhErrorKind.rateLimited => l10n.plugin_eh_error_rate_limited,
    EhErrorKind.notFound => l10n.plugin_eh_error_not_found,
    EhErrorKind.badResponse => l10n.plugin_eh_error_response,
    EhErrorKind.ban => l10n.plugin_eh_error_ban,
  };
}
