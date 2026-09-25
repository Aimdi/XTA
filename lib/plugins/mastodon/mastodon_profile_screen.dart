import 'package:xta/plugins/social_account_groups.dart';
import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_card.dart';
import 'package:xta/utils/urls.dart';

export 'package:xta/plugins/mastodon/mastodon_profile_card.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_store.dart';
import 'package:xta/plugins/mastodon/mastodon_search_sheet.dart';
import 'package:xta/plugins/mastodon/mastodon_media_grid.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/ui/errors.dart';

String mastodonErrorMessage(L10n l10n, Object error) {
  if (error is! MastodonException) {
    return l10n.plugin_mastodon_error_network;
  }
  return switch (error.kind) {
    MastodonErrorKind.notConfigured => l10n.plugin_mastodon_not_configured,
    MastodonErrorKind.network => l10n.plugin_mastodon_error_network,
    MastodonErrorKind.notFound => l10n.plugin_mastodon_error_not_found,
    MastodonErrorKind.rateLimited => l10n.plugin_mastodon_error_rate_limited,
    MastodonErrorKind.unauthorized => l10n.plugin_mastodon_error_unauthorized,
    MastodonErrorKind.badResponse => l10n.plugin_mastodon_error_response,
  };
}

/// One Fediverse profile and independently paged public reading views.
class MastodonProfileScreen extends StatefulWidget {
  final String acct;

  const MastodonProfileScreen({super.key, required this.acct});

  @override
  State<MastodonProfileScreen> createState() => _MastodonProfileScreenState();
}

class _MastodonProfileScreenState extends State<MastodonProfileScreen> {
  late final MastodonProfileStore _store;
  final _scrolls = {for (final tab in MastodonProfileTab.values) tab: ScrollController()};

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    _store = MastodonProfileStore(
      context.read<MastodonClient>(),
      mastodonInstanceCandidates(widget.acct, configured: mastodonConfiguredInstances(prefs)),
      widget.acct,
    );
    _store.refresh();
  }

  @override
  void dispose() {
    _store.destroy();
    for (final scroll in _scrolls.values) {
      scroll.dispose();
    }
    super.dispose();
  }

  Future<void> _toggleFollow(MastodonProfile profile) async {
    final accounts = context.read<MastodonAccountsStore>();
    final feed = context.read<MastodonFeedStore>();
    try {
      if (accounts.follows(profile.acct)) {
        await accounts.remove(profile.acct);
      } else {
        await accounts.add(profile.toAccount());
      }
      await feed.refresh();
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(mastodonErrorMessage(L10n.of(context), error))));
      }
    }
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<MastodonProfileStore, MastodonProfileState>(
    store: _store,
    onState: (context, state) => Scaffold(
      appBar: AppBar(
        title: Text('@${state.profile?.acct ?? widget.acct}', maxLines: 1, overflow: TextOverflow.ellipsis),
        actions: [
          if (mastodonProfileWebUrl(state.profile) case final String url) ...[
            IconButton(
              icon: const Icon(Icons.share_outlined),
              tooltip: L10n.of(context).share_link,
              onPressed: () => SharePlus.instance.share(ShareParams(text: url)),
            ),
            IconButton(
              icon: const Icon(Icons.open_in_browser),
              tooltip: L10n.of(context).open_in_browser,
              onPressed: () => openUri(context, url),
            ),
          ],
        ],
      ),
      body: _body(context, state),
    ),
  );

  Widget _body(BuildContext context, MastodonProfileState state) {
    final l10n = L10n.of(context);
    final profile = state.profile;
    if (profile == null) {
      if (state.error != null) {
        return Padding(
          padding: const EdgeInsets.all(24),
          child: FullPageErrorWidget(
            error: state.error,
            stackTrace: null,
            prefix: mastodonErrorMessage(l10n, state.error!),
            onRetry: _store.refresh,
          ),
        );
      }
      return const Center(child: CircularProgressIndicator());
    }
    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification is ScrollUpdateNotification &&
            notification.depth == 0 &&
            state.error == null &&
            notification.metrics.extentAfter < 500) {
          _store.loadMore();
        }
        return false;
      },
      child: RefreshIndicator(
        onRefresh: _store.refresh,
        child: CustomScrollView(
          key: PageStorageKey('mastodon-profile-${state.selected.name}'),
          controller: _scrolls[state.selected],
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: ScopedBuilder<MastodonAccountsStore, List<MastodonAccount>>(
                  store: context.read<MastodonAccountsStore>(),
                  onState: (context, accounts) => MastodonProfileCard(
                    profile: profile,
                    following: accounts.any((account) => account.acct.toLowerCase() == profile.acct.toLowerCase()),
                    onFollowToggle: () => _toggleFollow(profile),
                    onAddToGroup: () => addMastodonAccountToGroup(context, profile.toAccount()),
                    onTagTap: (tag) =>
                        Navigator.push(context, MaterialPageRoute(builder: (_) => MastodonTagScreen(tag: tag))),
                  ),
                ),
              ),
            ),
            SliverToBoxAdapter(
              child: _ProfileTabs(selected: state.selected, onSelected: _store.select),
            ),
            if (state.mediaSelected)
              MastodonMediaGrid(posts: state.media)
            else
              SliverList.builder(
                itemCount: state.visible.length,
                itemBuilder: (context, index) => MastodonPostCard(
                  key: ValueKey(state.visible[index].id),
                  post: state.visible[index],
                  showSourceBadge: false,
                  pinned:
                      state.selected == MastodonProfileTab.posts && state.pinnedIds.contains(state.visible[index].id),
                ),
              ),
            if (state.loading || state.loadingMore)
              const SliverToBoxAdapter(
                child: Padding(
                  padding: EdgeInsets.all(24),
                  child: Center(child: CircularProgressIndicator()),
                ),
              ),
            if (!state.loading && !state.loadingMore && state.visible.isEmpty && state.error == null)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.all(32),
                  child: Text(l10n.plugin_mastodon_no_posts, textAlign: TextAlign.center),
                ),
              ),
            if (state.error != null)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: FullPageErrorWidget(
                    error: state.error,
                    stackTrace: null,
                    prefix: mastodonErrorMessage(l10n, state.error!),
                    onRetry: _store.retry,
                  ),
                ),
              ),
            if (!state.loading && !state.loadingMore && state.error == null && state.current.more)
              SliverToBoxAdapter(
                child: Center(
                  child: TextButton.icon(
                    onPressed: _store.loadMore,
                    icon: const Icon(Icons.expand_more),
                    label: Text(l10n.plugin_mastodon_load_more),
                  ),
                ),
              ),
            const SliverToBoxAdapter(child: SizedBox(height: 32)),
          ],
        ),
      ),
    );
  }
}

class _ProfileTabs extends StatelessWidget {
  final MastodonProfileTab selected;
  final ValueChanged<MastodonProfileTab> onSelected;

  const _ProfileTabs({required this.selected, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final labels = {
      MastodonProfileTab.posts: l10n.tweets,
      MastodonProfileTab.replies: l10n.tweets_and_replies,
      MastodonProfileTab.media: l10n.media,
    };
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        children: [
          for (final tab in MastodonProfileTab.values)
            Padding(
              padding: const EdgeInsetsDirectional.only(end: 8),
              child: Semantics(
                selected: selected == tab,
                child: TextButton(
                  key: ValueKey('mastodon-profile-tab-${tab.name}'),
                  style: TextButton.styleFrom(
                    foregroundColor: selected == tab
                        ? theme.colorScheme.onPrimaryContainer
                        : theme.colorScheme.onSurfaceVariant,
                    backgroundColor: selected == tab ? theme.colorScheme.primaryContainer : null,
                    minimumSize: const Size(64, 48),
                  ),
                  onPressed: () => onSelected(tab),
                  child: Text(labels[tab]!),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
