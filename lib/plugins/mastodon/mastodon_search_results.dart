import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_people.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_search_sheet.dart';
import 'package:xta/plugins/mastodon/mastodon_search_store.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';

class MastodonSearchResults extends StatelessWidget {
  final MastodonSearchState state;
  final VoidCallback onRetry;
  final ValueChanged<int> onSelected;
  final List<ScrollController> positions;
  const MastodonSearchResults({super.key, required this.state, required this.onRetry,
    required this.onSelected, required this.positions});
  List<MastodonTrendingTag> get _tags => state.tags;
  MastodonSearchPage get _results => state.results;
  Object? get _error => state.error;
  bool get _loading => state.loading;
  bool get _searched => state.query.isNotEmpty;
  int get _tab => state.tab;

  void _openTag(BuildContext context, MastodonTrendingTag tag) => Navigator.push(context,
    MaterialPageRoute(builder: (_) => MastodonTagScreen(tag: tag.name)));
  void _openProfile(BuildContext context, MastodonProfile profile) => Navigator.push(context,
    MaterialPageRoute(builder: (_) => MastodonProfileScreen(acct: profile.acct)));

  List<Widget> _people(BuildContext context) {
    final accounts = context.read<MastodonAccountsStore?>()?.state ?? const <MastodonAccount>[];
    if (accounts.isEmpty) return const [];
    return [
      Text(L10n.of(context).following, style: Theme.of(context).textTheme.titleSmall),
      for (final account in accounts.take(4)) ListTile(contentPadding: EdgeInsets.zero,
        leading: MastodonPersonAvatar(acct: account.acct, name: account.name, url: account.avatarUrl),
        title: Text(account.name, maxLines: 1, overflow: TextOverflow.ellipsis),
        subtitle: Text('@${account.acct}', maxLines: 1, overflow: TextOverflow.ellipsis),
        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => MastodonProfileScreen(acct: account.acct)))),
      const SizedBox(height: 16),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Text(mastodonErrorMessage(l10n, _error!), textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton.icon(onPressed: onRetry, icon: const Icon(Icons.refresh), label: Text(l10n.retry)),
          ]),
        ),
      );
    }

    if (_searched) {
      return Column(
        children: [
          _SearchTabs(
            selected: _tab,
            onSelected: onSelected,
          ),
          const Divider(height: 1),
          Expanded(child: _resultsPane(context, l10n)),
        ],
      );
    }

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      children: [
        ..._people(context),
        if (_tags.isEmpty) Text(l10n.plugin_mastodon_search_hint),
        if (_tags.isNotEmpty) Text(
          l10n.plugin_mastodon_trending,
          style: Theme.of(
            context,
          ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final tag in _tags)
              ActionChip(
                label: Text('#${tag.name}'),
                onPressed: () => _openTag(context, tag),
              ),
          ],
        ),
      ],
    );
  }

  Widget _resultsPane(BuildContext context, L10n l10n) {
    if (_tab == 1) {
      if (_results.posts.isEmpty) {
        return Center(child: Text(l10n.plugin_mastodon_no_posts));
      }
      return ListView.builder(
        key: const PageStorageKey('mastodon-search-posts'), controller: positions[1],
        itemCount: _results.posts.length,
        itemBuilder: (context, index) => MastodonPostCard(
          key: ValueKey(_results.posts[index].id),
          post: _results.posts[index],
          showSourceBadge: false,
        ),
      );
    }
    if (_tab == 2) {
      if (_results.tags.isEmpty) {
        return Center(child: Text(l10n.plugin_mastodon_no_hashtags));
      }
      return ListView(
        key: const PageStorageKey('mastodon-search-tags'), controller: positions[2],
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
        children: [
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final tag in _results.tags)
                ActionChip(
                  label: Text('#${tag.name}'),
                  onPressed: () => _openTag(context, tag),
                ),
            ],
          ),
        ],
      );
    }
    if (_results.accounts.isEmpty) {
      return Center(child: Text(l10n.plugin_mastodon_no_results));
    }
    return ListView.separated(
      key: const PageStorageKey('mastodon-search-accounts'), controller: positions[0],
      itemCount: _results.accounts.length,
      separatorBuilder: (_, _) => const Divider(height: 1),
      itemBuilder: (context, index) {
        final profile = _results.accounts[index];
        return ListTile(
          leading: _avatar(context, profile),
          title: Text(
            profile.displayName.isEmpty ? profile.acct : profile.displayName,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          subtitle: Text(
            '@${profile.acct}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          onTap: () => _openProfile(context, profile),
        );
      },
    );
  }

  Widget _avatar(BuildContext context, MastodonProfile profile) => MastodonPersonAvatar(
    acct: profile.acct, name: profile.displayName, url: profile.avatarUrl);
}

class _SearchTabs extends StatelessWidget {
  final int selected;
  final ValueChanged<int> onSelected;

  const _SearchTabs({required this.selected, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PluginHomeChrome(tabs: [
      PluginHomeTab(label: l10n.plugin_mastodon_accounts, icon: Icons.people_outline,
        selected: selected == 0, onTap: () => onSelected(0)),
      PluginHomeTab(label: l10n.tweets, icon: Icons.view_stream_outlined,
        selected: selected == 1, onTap: () => onSelected(1)),
      PluginHomeTab(label: l10n.plugin_mastodon_hashtags, icon: Icons.tag,
        selected: selected == 2, onTap: () => onSelected(2)),
    ]);
  }
}
