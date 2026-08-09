import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

/// Home instance URL and the accounts followed through it.
class MastodonSettingsScreen extends StatefulWidget {
  const MastodonSettingsScreen({super.key});

  @override
  State<MastodonSettingsScreen> createState() => _MastodonSettingsScreenState();
}

class _MastodonSettingsScreenState extends State<MastodonSettingsScreen> {
  late final TextEditingController _instance;
  late final TextEditingController _newInstance;
  late List<String> _extras;
  bool _testing = false;

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    _instance = TextEditingController(text: prefs.get<String>(optionPluginMastodonInstance) ?? '');
    _newInstance = TextEditingController();
    _extras = _storedExtras(prefs);
  }

  static List<String> _storedExtras(BasePrefService prefs) {
    try {
      final decoded = jsonDecode(prefs.get<String>(optionPluginMastodonInstances) ?? '[]');
      return decoded is List ? decoded.whereType<String>().toList() : const [];
    } catch (_) {
      return const [];
    }
  }

  @override
  void dispose() {
    _instance.dispose();
    _newInstance.dispose();
    super.dispose();
  }

  Future<void> _saveExtras() async {
    await PrefService.of(context, listen: false).set(optionPluginMastodonInstances, jsonEncode(_extras));
  }

  Future<void> _addExtra() async {
    final normalised = normaliseMastodonInstance(_newInstance.text);
    if (normalised == null || _extras.contains(normalised)) {
      return;
    }

    setState(() {
      _extras = [..._extras, normalised];
      _newInstance.clear();
    });
    await _saveExtras();
  }

  Future<void> _removeExtra(String instance) async {
    setState(() => _extras = _extras.where((e) => e != instance).toList());
    await _saveExtras();
  }

  Future<void> _save() async {
    final normalised = normaliseMastodonInstance(_instance.text) ?? _instance.text.trim();
    await PrefService.of(context, listen: false).set(optionPluginMastodonInstance, normalised);
    if (normalised != _instance.text) {
      _instance.text = normalised;
    }
  }

  Future<void> _test() async {
    final l10n = L10n.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final client = context.read<MastodonClient>();

    await _save();
    setState(() => _testing = true);
    String message;
    try {
      await client.verify(_instance.text.trim());
      message = l10n.plugin_mastodon_test_ok;
    } catch (e) {
      message = mastodonErrorMessage(l10n, e);
    }

    if (mounted) {
      setState(() => _testing = false);
      messenger.showSnackBar(SnackBar(content: Text(message)));
    }
  }

  Future<void> _remove(String acct) async {
    await context.read<MastodonAccountsStore>().remove(acct);
    if (mounted) {
      await context.read<MastodonFeedStore>().refresh();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_mastodon_title)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SwitchListTile(
            secondary: const Icon(Icons.dynamic_feed_outlined),
            title: Text(l10n.plugin_mastodon_in_home_feed),
            subtitle: Text(l10n.plugin_mastodon_in_home_feed_description),
            value: PrefService.of(context).get<bool>(optionPluginMastodonInHomeFeed) == true,
            onChanged: (value) async {
              await PrefService.of(context, listen: false).set(optionPluginMastodonInHomeFeed, value);
              if (mounted) setState(() {});
            },
          ),
          Text(l10n.plugin_mastodon_settings_intro, style: theme.textTheme.bodyMedium),
          const SizedBox(height: 16),
          Text(l10n.plugin_mastodon_instance, style: theme.textTheme.titleSmall),
          const SizedBox(height: 8),
          TextField(
            controller: _instance,
            decoration: InputDecoration(
              hintText: l10n.plugin_mastodon_instance_hint,
              border: const OutlineInputBorder(),
            ),
            keyboardType: TextInputType.url,
            autocorrect: false,
            onChanged: (_) => _save(),
          ),
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerLeft,
            child: FilledButton.tonal(
              onPressed: _testing ? null : _test,
              child: _testing
                  ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                  : Text(l10n.plugin_mastodon_test),
            ),
          ),
          const SizedBox(height: 28),
          Text(l10n.plugin_mastodon_more_instances, style: theme.textTheme.titleSmall),
          const SizedBox(height: 8),
          for (final instance in _extras)
            ListTile(
              contentPadding: EdgeInsets.zero,
              dense: true,
              title: Text(instance),
              trailing: IconButton(icon: const Icon(Icons.delete_outline), onPressed: () => _removeExtra(instance)),
            ),
          TextField(
            controller: _newInstance,
            decoration: InputDecoration(
              hintText: l10n.plugin_mastodon_instance_hint,
              border: const OutlineInputBorder(),
              suffixIcon: IconButton(icon: const Icon(Icons.add), onPressed: _addExtra),
            ),
            keyboardType: TextInputType.url,
            autocorrect: false,
            onSubmitted: (_) => _addExtra(),
          ),
          const SizedBox(height: 28),
          Text(l10n.plugin_mastodon_builtin_instances, style: theme.textTheme.titleSmall),
          const SizedBox(height: 8),
          Text(
            l10n.plugin_mastodon_builtin_instances_description,
            style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
          const SizedBox(height: 8),
          for (final instance in kMastodonDefaultInstances)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 2),
              child: Text(
                mastodonInstanceDomain(instance) ?? instance,
                style: theme.textTheme.bodyMedium!.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
            ),
          const SizedBox(height: 28),
          Text(l10n.plugin_mastodon_accounts, style: theme.textTheme.titleSmall),
          const SizedBox(height: 8),
          ScopedBuilder<MastodonAccountsStore, List<MastodonAccount>>(
            store: context.read<MastodonAccountsStore>(),
            onState: (context, accounts) {
              if (accounts.isEmpty) {
                return Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Text(
                    l10n.plugin_mastodon_no_accounts,
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
                                seed: account.acct,
                                displayName: account.name,
                                size: 40,
                                accent: theme.colorScheme.primary,
                              )
                            : Image.network(account.avatarUrl!, width: 40, height: 40, fit: BoxFit.cover),
                      ),
                      title: Text(account.name),
                      subtitle: Text('@${account.acct}'),
                      trailing: IconButton(
                        icon: const Icon(Icons.delete_outline),
                        tooltip: l10n.plugin_mastodon_unfollow,
                        onPressed: () => _remove(account.acct),
                      ),
                      onTap: () => Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => MastodonProfileScreen(acct: account.acct)),
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
