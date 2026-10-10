import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_accounts.dart';
import 'package:xta/plugins/pixiv/pixiv_avatar.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/ui/errors.dart';

/// Makes [account] the active one and drops what was loaded for the last.
Future<void> switchPixivAccount(BuildContext context, PixivAccountsStore store, PixivAccount account) async {
  final forget = pixivAccountDataForgetter(context);
  final message = L10n.of(context).plugin_pixiv_signed_in(account.displayName);
  await store.switchTo(account);
  forget();
  if (context.mounted) showSnackBar(context, icon: '✅', message: message);
}

/// Asks first, then signs the active account out of this device. Another
/// stored account takes over when there is one. True once signed out.
Future<bool> confirmPixivSignOut(BuildContext context, PixivAccountsStore store) async {
  final l10n = L10n.of(context);
  final name = store.active?.displayName;
  final question = name == null ? l10n.plugin_pixiv_sign_out_question : l10n.plugin_pixiv_sign_out_named(name);
  if (!await _confirm(context, question, l10n.plugin_pixiv_sign_out)) return false;
  if (!context.mounted) return false;
  final forget = pixivAccountDataForgetter(context);
  final next = await store.signOutActive();
  forget();
  if (next != null && context.mounted) {
    showSnackBar(context, icon: '✅', message: l10n.plugin_pixiv_signed_in(next.displayName));
  }
  return true;
}

Future<void> _confirmRemove(BuildContext context, PixivAccountsStore store, PixivAccount account) async {
  final l10n = L10n.of(context);
  final question = l10n.plugin_pixiv_account_remove_question(account.displayName);
  if (await _confirm(context, question, l10n.plugin_pixiv_account_remove)) await store.remove(account);
}

Future<bool> _confirm(BuildContext context, String question, String action) async {
  final answer = await showDialog<bool>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      title: Text(question),
      actions: [
        TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: Text(L10n.of(dialogContext).cancel)),
        FilledButton(onPressed: () => Navigator.pop(dialogContext, true), child: Text(action)),
      ],
    ),
  );
  return answer == true;
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
      onTap: active ? null : () => _then(switchPixivAccount(context, store, account)),
    );
  }

  Future<void> _then(Future<void> change) async {
    await change;
    onChanged?.call();
  }
}
