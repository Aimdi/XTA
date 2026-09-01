import 'package:flutter/material.dart';
import 'package:xta/client/accounts.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

/// The account whose mark stands in for "you" in the chrome: the first stored
/// fetch account.
///
/// XTA has no single signed-in identity — accounts are a pool a request rotates
/// through — so this is a stable stand-in for the chrome, not a claim about
/// which account any given request used.
Future<Account?> primaryAccount() async {
  final accounts = await getAccounts();
  return accounts.isEmpty ? null : accounts.first;
}

/// A round mark for a fetch account: its initial on an accent-derived disc.
///
/// Fetch accounts carry a screen name but no picture, so this never pretends to
/// be a profile photo — and falls back to a neutral glyph when no account has
/// been added at all. For a custom chrome picture, use [ChromeAvatarMark].
class AccountAvatar extends StatelessWidget {
  final Account? account;
  final double size;

  const AccountAvatar({super.key, required this.account, this.size = 32});

  @override
  Widget build(BuildContext context) {
    final name = account?.screenName;
    if (name == null || name.isEmpty) {
      return Icon(Icons.account_circle, size: size, color: Theme.of(context).colorScheme.onSurfaceVariant);
    }
    return FallbackAvatar(
      seed: account!.id,
      displayName: name,
      size: size,
      accent: Theme.of(context).colorScheme.primary,
    );
  }
}
