import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:quax/client/login_webview.dart';
import 'package:quax/database/entities.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/settings/settings_chrome.dart';
import 'package:quax/settings/settings_view_store.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/ui/errors.dart';

class SettingsAccountFragment extends StatefulWidget {
  const SettingsAccountFragment({super.key});

  @override
  State<SettingsAccountFragment> createState() =>
      _SettingsAccountFragmentState();
}

class _SettingsAccountFragmentState extends State<SettingsAccountFragment> {
  late final SettingsAccountsStore _accounts;

  @override
  void initState() {
    super.initState();
    _accounts = SettingsAccountsStore()..load();
  }

  @override
  void dispose() {
    _accounts.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return SettingsPageScaffold(
      title: l10n.account,
      actions: [
        IconButton(
          tooltip: l10n.add_account,
          onPressed: () async {
            await Navigator.push(
              context,
              MaterialPageRoute<void>(
                builder: (_) => const TwitterLoginWebview(),
              ),
            );
            if (mounted) await _accounts.load();
          },
          icon: const Icon(Icons.person_add_alt_outlined),
        ),
      ],
      body: ScopedBuilder<SettingsAccountsStore, List<Account>>.transition(
        store: _accounts,
        onLoading: (_) => const SettingsListSkeleton(count: 4),
        onError: (_, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: l10n.diagnostics_no_accounts,
          onRetry: _accounts.load,
        ),
        onState: (_, accounts) {
          if (accounts.isEmpty) {
            return SettingsList(
              children: [
                SettingsRow(
                  icon: Icons.person_off_outlined,
                  title: l10n.diagnostics_no_accounts,
                ),
              ],
            );
          }
          return SettingsList(
            children: [
              SettingsSection(
                children: accounts
                    .map(
                      (account) => _AccountRow(
                        account: account,
                        onDelete: () => _accounts.delete(account.id),
                      ),
                    )
                    .toList(growable: false),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _AccountRow extends StatelessWidget {
  final Account account;
  final Future<void> Function() onDelete;

  const _AccountRow({required this.account, required this.onDelete});

  @override
  Widget build(BuildContext context) {
    return Dismissible(
      key: ValueKey(account.id),
      background: ColoredBox(
        color: Theme.of(context).colorScheme.error,
        child: const Padding(
          padding: EdgeInsets.symmetric(horizontal: kTweetHorizontalPadding),
          child: Align(
            alignment: Alignment.centerLeft,
            child: Icon(Icons.delete_outline, color: Colors.white),
          ),
        ),
      ),
      secondaryBackground: ColoredBox(
        color: Theme.of(context).colorScheme.error,
        child: const Padding(
          padding: EdgeInsets.symmetric(horizontal: kTweetHorizontalPadding),
          child: Align(
            alignment: Alignment.centerRight,
            child: Icon(Icons.delete_outline, color: Colors.white),
          ),
        ),
      ),
      onDismissed: (_) => onDelete(),
      child: SettingsRow(
        icon: Icons.account_circle_outlined,
        title: account.screenName ?? L10n.of(context).unknown_username,
      ),
    );
  }
}
