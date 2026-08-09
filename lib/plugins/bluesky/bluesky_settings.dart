import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_import_follows_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_import_list_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

/// AppView URL and the accounts followed through it.
class BlueskySettingsScreen extends StatefulWidget {
  const BlueskySettingsScreen({super.key});

  @override
  State<BlueskySettingsScreen> createState() => _BlueskySettingsScreenState();
}

class _BlueskySettingsScreenState extends State<BlueskySettingsScreen> {
  late final TextEditingController _instance;
  bool _testing = false;

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    final stored = (prefs.get<String>(optionPluginBlueskyInstance) ?? '').trim();
    _instance = TextEditingController(
      text: stored.isEmpty ? kBlueskyDefaultAppView : stored,
    );
  }

  @override
  void dispose() {
    _instance.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final normalised = normaliseBlueskyAppView(_instance.text) ?? _instance.text.trim();
    await PrefService.of(context, listen: false).set(optionPluginBlueskyInstance, normalised);
    if (normalised != _instance.text) {
      _instance.text = normalised;
    }
  }

  Future<void> _useDefault() async {
    _instance.text = kBlueskyDefaultAppView;
    await _save();
  }

  Future<void> _test() async {
    final l10n = L10n.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final client = context.read<BlueskyClient>();

    await _save();
    setState(() => _testing = true);
    String message;
    try {
      await client.verify();
      message = l10n.plugin_bluesky_test_ok;
    } catch (e) {
      message = blueskyErrorMessage(l10n, e);
    }

    if (mounted) {
      setState(() => _testing = false);
      messenger.showSnackBar(SnackBar(content: Text(message)));
    }
  }

  Future<void> _remove(String handle) async {
    final accounts = context.read<BlueskyAccountsStore>();
    final subscriptions = context.read<SubscriptionsModel>();
    final feed = context.read<BlueskyFeedStore>();
    await accounts.remove(handle);
    await subscriptions.reloadSubscriptions();
    if (mounted) {
      await feed.refresh();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_bluesky_title)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SwitchListTile(
            secondary: const Icon(Icons.dynamic_feed_outlined),
            title: Text(l10n.plugin_bluesky_in_home_feed),
            subtitle: Text(l10n.plugin_bluesky_in_home_feed_description),
            value: PrefService.of(context).get<bool>(optionPluginBlueskyInHomeFeed) == true,
            onChanged: (value) async {
              await PrefService.of(context, listen: false).set(optionPluginBlueskyInHomeFeed, value);
              if (mounted) setState(() {});
            },
          ),
          Text(l10n.plugin_bluesky_settings_intro, style: theme.textTheme.bodyMedium),
          const SizedBox(height: 16),
          Text(l10n.plugin_bluesky_instance, style: theme.textTheme.titleSmall),
          const SizedBox(height: 8),
          TextField(
            controller: _instance,
            decoration: InputDecoration(
              hintText: l10n.plugin_bluesky_instance_hint,
              border: const OutlineInputBorder(),
            ),
            keyboardType: TextInputType.url,
            autocorrect: false,
            onChanged: (_) => _save(),
          ),
          const SizedBox(height: 8),
          Text(
            l10n.plugin_bluesky_instance_default(kBlueskyDefaultAppView),
            style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              FilledButton.tonal(
                onPressed: _testing ? null : _test,
                child: _testing
                    ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                    : Text(l10n.plugin_bluesky_test),
              ),
              TextButton(
                onPressed: _useDefault,
                child: Text(l10n.plugin_bluesky_use_default),
              ),
            ],
          ),
          const SizedBox(height: 28),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.cloud_download_outlined),
            title: Text(l10n.plugin_bluesky_import_following),
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const BlueskyImportFollowsScreen()),
            ),
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.list_alt_outlined),
            title: Text(l10n.plugin_bluesky_import_list),
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const BlueskyImportListScreen()),
            ),
          ),
          const SizedBox(height: 16),
          Text(l10n.plugin_bluesky_accounts, style: theme.textTheme.titleSmall),
          const SizedBox(height: 8),
          ScopedBuilder<BlueskyAccountsStore, List<BlueskyAccount>>(
            store: context.read<BlueskyAccountsStore>(),
            onState: (context, accounts) {
              if (accounts.isEmpty) {
                return Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Text(
                    l10n.plugin_bluesky_no_accounts,
                    style: theme.textTheme.bodyMedium!.copyWith(color: theme.colorScheme.onSurfaceVariant),
                  ),
                );
              }

              return Column(
                children: [
                  for (final account in accounts)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: ClipOval(
                        child: account.avatarUrl == null
                            ? FallbackAvatar(
                                seed: account.handle,
                                displayName: account.name,
                                size: 40,
                                accent: theme.colorScheme.primary,
                              )
                            : Image.network(account.avatarUrl!, width: 40, height: 40, fit: BoxFit.cover),
                      ),
                      title: Text(account.name),
                      subtitle: Text('@${account.handle}'),
                      trailing: IconButton(
                        icon: const Icon(Icons.delete_outline),
                        tooltip: l10n.plugin_bluesky_unfollow,
                        onPressed: () => _remove(account.handle),
                      ),
                    ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}
