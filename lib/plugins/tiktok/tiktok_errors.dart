import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/tiktok/tiktok_client.dart';

String tiktokErrorMessage(L10n l10n, Object? error) {
  if (error is! TikTokException) {
    return l10n.plugin_tiktok_error_network;
  }
  return switch (error.kind) {
    TikTokErrorKind.network => l10n.plugin_tiktok_error_network,
    TikTokErrorKind.notFound => l10n.plugin_tiktok_error_not_found,
    TikTokErrorKind.privateAccount => l10n.plugin_tiktok_error_private,
    TikTokErrorKind.rateLimited => l10n.plugin_tiktok_error_rate_limited,
    TikTokErrorKind.badResponse => l10n.plugin_tiktok_error_response,
  };
}
