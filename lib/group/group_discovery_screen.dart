import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/group/group_discovery_sources.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/tweet/tweet.dart';
import 'package:xta/tweet/tweet_context_scope.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/ai_client.dart';

Future<void> openGroupDiscovery(BuildContext context, {required String id, required String name, bool useAi = false}) =>
    Navigator.push(
      context,
      MaterialPageRoute<void>(
        builder: (_) => GroupDiscoveryScreen(id: id, name: name, useAi: useAi),
      ),
    );

class GroupDiscoveryScreen extends StatefulWidget {
  final String id;
  final String name;
  final bool useAi;
  const GroupDiscoveryScreen({super.key, required this.id, required this.name, this.useAi = false});

  @override
  State<GroupDiscoveryScreen> createState() => _GroupDiscoveryScreenState();
}

class _GroupDiscoveryScreenState extends State<GroupDiscoveryScreen> {
  late final GroupModel _group = GroupModel(widget.id, prefs: PrefService.of(context, listen: false))..loadGroup();

  @override
  void dispose() {
    _group.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text('${L10n.of(context).discover} · ${widget.name}')),
    body: ScopedBuilder<GroupModel, SubscriptionGroupGet>(
      store: _group,
      onLoading: (_) => const Center(child: CircularProgressIndicator()),
      onError: (_, _) => Center(
        child: FilledButton.tonal(onPressed: () => _group.loadGroup(), child: Text(L10n.of(context).retry)),
      ),
      onState: (_, group) => group.id.isEmpty
          ? const Center(child: CircularProgressIndicator())
          : GroupDiscoveryPane(group: group, useAi: widget.useAi),
    ),
  );
}

class GroupDiscoveryPane extends StatefulWidget {
  final SubscriptionGroupGet group;
  final bool useAi;
  const GroupDiscoveryPane({super.key, required this.group, this.useAi = false});

  @override
  State<GroupDiscoveryPane> createState() => _GroupDiscoveryPaneState();
}

class _GroupDiscoveryPaneState extends State<GroupDiscoveryPane> {
  final _model = GroupDiscoveryStore();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _load(useAi: widget.useAi);
    });
  }

  @override
  void dispose() {
    _model.destroy();
    super.dispose();
  }

  Future<void> _load({bool useAi = false}) => _model.load(
    sources: groupDiscoverySources(context, widget.group.subscriptions),
    followed: currentDiscoveryFollowedIds(context, widget.group.subscriptions),
    groupName: widget.group.name,
    ai: useAi ? AiConfig.fromPrefs(PrefService.of(context, listen: false)) : null,
  );

  void _afterProfile() {
    if (!mounted) return;
    _model.excludeFollowed(currentDiscoveryFollowedIds(context, widget.group.subscriptions));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final configured = AiConfig.fromPrefs(PrefService.of(context)).isConfigured;
    return ScopedBuilder<GroupDiscoveryStore, GroupDiscoveryState>(
      store: _model,
      onLoading: (_) => const Center(child: CircularProgressIndicator()),
      onError: (_, _) => Center(
        child: FilledButton.tonal(onPressed: () => _load(), child: Text(l10n.retry)),
      ),
      onState: (_, state) => RefreshIndicator(
        onRefresh: () => _load(),
        child: TweetContextScope(
          child: ListView.builder(
            key: PageStorageKey('discovery-${widget.group.id}'),
            primary: false,
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.only(bottom: 24),
            itemCount: state.accounts.length + 1,
            itemBuilder: (context, index) => index == 0
                ? Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(l10n.group_discovery_description),
                        if (configured)
                          Padding(
                            padding: const EdgeInsets.only(top: 12),
                            child: FilledButton.tonalIcon(
                              onPressed: () => _load(useAi: true),
                              icon: const Icon(Icons.auto_awesome),
                              label: Text(l10n.group_discovery_ai),
                            ),
                          ),
                        if (state.usedAi) Text(l10n.sort_ungrouped_ai_note),
                        if (state.aiFailed) Text(l10n.group_ai_fallback),
                        if (state.sourceFailed) Text(l10n.group_discovery_partial),
                        if (state.accounts.isEmpty)
                          Padding(padding: const EdgeInsets.only(top: 24), child: Text(l10n.group_discovery_empty)),
                      ],
                    ),
                  )
                : _DiscoveryCard(account: state.accounts[index - 1], onReturn: _afterProfile),
          ),
        ),
      ),
    );
  }
}

class _DiscoveryCard extends StatelessWidget {
  final DiscoveryAccount account;
  final VoidCallback onReturn;
  const _DiscoveryCard({required this.account, required this.onReturn});

  Future<void> _open(BuildContext context) async {
    switch (account.source) {
      case DiscoverySource.x:
        await Navigator.pushNamed(
          context,
          routeProfile,
          arguments: ProfileScreenArguments(account.id, account.handle, null),
        );
      case DiscoverySource.bluesky:
        await Navigator.push(context, MaterialPageRoute<void>(builder: (_) => BlueskyProfileScreen(actor: account.id)));
      case DiscoverySource.mastodon:
        await Navigator.push(context, MaterialPageRoute<void>(builder: (_) => MastodonProfileScreen(acct: account.id)));
      case DiscoverySource.pixiv:
        await Navigator.push(
          context,
          MaterialPageRoute<void>(builder: (_) => PixivUserScreen(userId: int.parse(account.id))),
        );
    }
    onReturn();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final source = switch (account.source) {
      DiscoverySource.x => l10n.source_x,
      DiscoverySource.bluesky => l10n.plugin_bluesky_title,
      DiscoverySource.mastodon => l10n.plugin_mastodon_title,
      DiscoverySource.pixiv => l10n.plugin_pixiv_title,
    };
    return Column(
      children: [
        ListTile(
          leading: UserAvatar(uri: account.avatarUrl, size: 40),
          title: Text(account.name, maxLines: 1, overflow: TextOverflow.ellipsis),
          subtitle: Text('$source · @${account.handle}', maxLines: 1, overflow: TextOverflow.ellipsis),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => _open(context),
        ),
        switch (account.supportingPost) {
          TweetWithCard post => TweetTile(clickable: true, tweet: post),
          BlueskyPost post => BlueskyPostCard(post: post, showSourceBadge: true),
          MastodonPost post => MastodonPostCard(post: post, showSourceBadge: true),
          PixivIllust post => Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: PixivIllustTile(illust: post),
          ),
          _ => Padding(padding: const EdgeInsets.all(16), child: Text(account.text)),
        },
        const Divider(height: 1),
      ],
    );
  }
}
