import 'package:xta/plugins/social_account_groups.dart';
import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/ui/empty_pane.dart';

class MastodonFollowingControls extends StatelessWidget {
  final bool people;
  final ValueChanged<bool> onSelected;
  final VoidCallback onAdd;
  const MastodonFollowingControls({super.key, required this.people, required this.onSelected, required this.onAdd});

  @override
  Widget build(BuildContext context) => PluginHomeChrome(
    tabs: [
      PluginHomeTab(
        label: L10n.of(context).tweets,
        icon: Icons.view_stream_outlined,
        selected: !people,
        onTap: () => onSelected(false),
      ),
      PluginHomeTab(
        label: L10n.of(context).plugin_mastodon_accounts,
        icon: Icons.people_outline,
        selected: people,
        onTap: () => onSelected(true),
      ),
    ],
    actions: [
      IconButton(
        key: const ValueKey('mastodon-add-account'),
        tooltip: L10n.of(context).plugin_mastodon_add,
        onPressed: onAdd,
        icon: const Icon(Icons.person_add_alt),
      ),
    ],
  );
}

class MastodonPeoplePane extends StatelessWidget {
  final VoidCallback onAdd;
  final ScrollController scrollController;
  const MastodonPeoplePane({super.key, required this.onAdd, required this.scrollController});

  @override
  Widget build(BuildContext context) => ScopedBuilder<MastodonAccountsStore, List<MastodonAccount>>(
    store: context.read<MastodonAccountsStore>(),
    onState: (context, accounts) {
      final l10n = L10n.of(context);
      if (accounts.isEmpty) {
        return EmptyPane(
          icon: Icons.people_outline,
          message: l10n.plugin_mastodon_empty,
          action: FilledButton.icon(
            onPressed: onAdd,
            icon: const Icon(Icons.person_add_alt),
            label: Text(l10n.plugin_mastodon_add),
          ),
        );
      }
      return ListView.separated(
        key: const PageStorageKey('mastodon-following-people'),
        controller: pluginInnerScrollController(context, scrollController),
        padding: pluginFeedPadding(context, extra: const EdgeInsets.only(top: 8)),
        itemCount: accounts.length,
        separatorBuilder: (_, _) => const Divider(height: 1, indent: 76),
        itemBuilder: (context, index) {
          final account = accounts[index];
          return ListTile(
            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
            leading: MastodonPersonAvatar(acct: account.acct, name: account.name, url: account.avatarUrl),
            title: Text(account.name, maxLines: 2, overflow: TextOverflow.ellipsis),
            subtitle: Text('@${account.acct}', maxLines: 2, overflow: TextOverflow.ellipsis),
            trailing: IconButton(icon: const Icon(Icons.group_add_outlined),
              tooltip: l10n.add_to_group,
              onPressed: () => addMastodonAccountToGroup(context, account)),
            onTap: () =>
                Navigator.push(context, MaterialPageRoute(builder: (_) => MastodonProfileScreen(acct: account.acct))),
          );
        },
      );
    },
  );
}

class MastodonPersonAvatar extends StatelessWidget {
  final String acct;
  final String name;
  final String? url;
  const MastodonPersonAvatar({super.key, required this.acct, required this.name, this.url});

  @override
  Widget build(BuildContext context) {
    final fallback = FallbackAvatar(
      seed: acct,
      displayName: name,
      size: 44,
      accent: Theme.of(context).colorScheme.primary,
    );
    return ClipOval(
      child: url == null || url!.isEmpty
          ? fallback
          : ExtendedImage.network(
              url!,
              width: 44,
              height: 44,
              fit: BoxFit.cover,
              cacheWidth: (44 * MediaQuery.devicePixelRatioOf(context)).ceil(),
              loadStateChanged: (state) => state.extendedImageLoadState == LoadState.failed ? fallback : null,
            ),
    );
  }
}
