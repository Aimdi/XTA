import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_accounts.dart';
import 'package:xta/plugins/pixiv/pixiv_avatar.dart';
import 'package:xta/plugins/pixiv/pixiv_confirm.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/ui/errors.dart';

/// Drops what was loaded for the account in use, then makes [account] the
/// active one. Forgetting first means nothing reloaded for the new account is
/// forgotten after it.
Future<void> switchPixivAccount(BuildContext context, PixivAccountsStore store, PixivAccount account) async {
  final message = L10n.of(context).plugin_pixiv_signed_in(account.displayName);
  pixivAccountDataForgetter(context)();
  await store.switchTo(account);
  if (context.mounted) showSnackBar(context, icon: '✅', message: message);
}

/// Asks first, then signs the active account out of this device. Another
/// stored account takes over when there is one. True once signed out.
Future<bool> confirmPixivSignOut(BuildContext context, PixivAccountsStore store) async {
  final l10n = L10n.of(context);
  final name = store.active?.displayName;
  final question = name == null ? l10n.plugin_pixiv_sign_out_question : l10n.plugin_pixiv_sign_out_named(name);
  if (!await confirmPixivAction(context, question, l10n.plugin_pixiv_sign_out) || !context.mounted) return false;
  pixivAccountDataForgetter(context)();
  final next = await store.signOutActive();
  if (next != null && context.mounted) {
    showSnackBar(context, icon: '✅', message: l10n.plugin_pixiv_signed_in(next.displayName));
  }
  return true;
}

/// Asks first, then forgets [account]. True once it is gone.
Future<bool> _confirmRemove(BuildContext context, PixivAccountsStore store, PixivAccount account) async {
  final l10n = L10n.of(context);
  final question = l10n.plugin_pixiv_account_remove_question(account.displayName);
  if (!await confirmPixivAction(context, question, l10n.plugin_pixiv_account_remove)) return false;
  await store.remove(account);
  return true;
}

/// The accounts stored on this device, the active one checked. Tapping
/// another switches to it; an inactive one can be removed.
class PixivAccountList extends StatelessWidget {
  final PixivAccountsStore store;

  /// Called after a switch or a removal, for screens that show the account elsewhere.
  final VoidCallback? onChanged;

  const PixivAccountList({super.key, required this.store, this.onChanged});

  @override
  Widget build(BuildContext context) => ScopedBuilder<PixivAccountsStore, List<PixivAccount>>(
    store: store,
    onState: (context, accounts) =>
        Column(mainAxisSize: MainAxisSize.min, children: [for (final account in accounts) _tile(context, account)]),
  );

  Widget _tile(BuildContext context, PixivAccount account) {
    final l10n = L10n.of(context);
    final active = account.userId == store.activeId;
    return ListTile(
      key: ValueKey('pixiv-account-${account.userId}'),
      contentPadding: EdgeInsets.zero,
      selected: active,
      leading: PixivAvatar(userId: account.userId, name: account.displayName, url: account.avatarUrl),
      title: Text(account.displayName, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: account.account.isEmpty
          ? null
          : Text('@${account.account}', maxLines: 1, overflow: TextOverflow.ellipsis),
      trailing: active
          ? Icon(Icons.check_circle, semanticLabel: l10n.plugin_pixiv_account_active)
          : IconButton(
              tooltip: l10n.plugin_pixiv_account_remove,
              icon: const Icon(Icons.delete_outline),
              onPressed: () => _then(_confirmRemove(context, store, account)),
            ),
      onTap: active ? null : () => _then(switchPixivAccount(context, store, account).then((_) => true)),
    );
  }

  /// Tells [onChanged] only about a change that happened, not a cancelled one.
  Future<void> _then(Future<bool> change) async {
    if (await change) onChanged?.call();
  }
}
