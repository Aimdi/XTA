import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';

/// Copies a token's full contract address — the thing a reader pastes into a
/// wallet or explorer — and says so, since a long-press gives no other sign.
Future<void> copyCryptoAddress(BuildContext context, CryptoAsset asset) async {
  final messenger = ScaffoldMessenger.maybeOf(context);
  final message = L10n.of(context).plugin_stocks_crypto_address_copied;
  await HapticFeedback.selectionClick();
  await Clipboard.setData(ClipboardData(text: asset.address));
  messenger?.showSnackBar(SnackBar(content: Text(message)));
}
