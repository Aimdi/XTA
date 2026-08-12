import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_search_sheet.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';

/// The Mastodon tab: every locally followed acct, merged newest first.
class MastodonScreen extends StatefulWidget {
  final ScrollController scrollController;

  const MastodonScreen({super.key, required this.scrollController});

  @override
  State<MastodonScreen> createState() => _MastodonScreenState();
}

class _MastodonScreenState extends State<MastodonScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        context.read<MastodonFeedStore>().refresh();
      }
    });
  }

  Future<void> _lookUpProfile() async {
    await showMastodonSearchSheet(context);
    if (mounted) {
      await context.read<MastodonFeedStore>().refresh();
    }
  }

  Future<void> _addAccount() async {
    final acct = await showMastodonAddAccountDialog(context);
    if (acct == null || !mounted) {
      return;
    }

    final messenger = ScaffoldMessenger.of(context);
    final prefs = PrefService.of(context, listen: false);
    final client = context.read<MastodonClient>();
    final accounts = context.read<MastodonAccountsStore>();
    final l10n = L10n.of(context);

    try {
      // No instance required any more: the acct's own instance is asked first,
      // then the reader's, then the built-in defaults.
      final candidates = mastodonInstanceCandidates(
        acct,
        configured: mastodonConfiguredInstances(prefs),
      );
      final profile = await client.lookupAnywhere(candidates, acct);
      await accounts.add(profile.toAccount());
    } catch (e) {
      if (mounted) {
        messenger.showSnackBar(
          SnackBar(content: Text(mastodonErrorMessage(l10n, e))),
        );
      }
      return;
    }

    if (mounted) {
      await context.read<MastodonFeedStore>().refresh();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final feed = context.read<MastodonFeedStore>();

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.plugin_mastodon_title),
        actions: [
          IconButton(
            icon: const Icon(Icons.search),
            tooltip: l10n.plugin_mastodon_search,
            onPressed: _lookUpProfile,
          ),
          IconButton(
            icon: const Icon(Icons.person_add_alt),
            tooltip: l10n.plugin_mastodon_add,
            onPressed: _addAccount,
          ),
        ],
      ),
      body: ScopedBuilder<MastodonFeedStore, List<MastodonPost>>(
        store: feed,
        onLoading: (_) {
          if (feed.state.isNotEmpty) {
            return _feed(context, l10n, feed.state);
          }
          return const Center(child: CircularProgressIndicator());
        },
        onError: (context, error) {
          if (feed.state.isNotEmpty) {
            return _feed(context, l10n, feed.state);
          }
          return Padding(
            padding: const EdgeInsets.all(24),
            child: FullPageErrorWidget(
              error: error,
              stackTrace: null,
              prefix: mastodonErrorMessage(l10n, error ?? Exception()),
              onRetry: () => context.read<MastodonFeedStore>().refresh(),
            ),
          );
        },
        onState: (context, posts) => _feed(context, l10n, posts),
      ),
    );
  }

  Widget _feed(BuildContext context, L10n l10n, List<MastodonPost> posts) {
    if (posts.isEmpty) {
      // Scrollable and refreshable even when empty: an empty batch from
      // half-down instances is exactly when the reader reaches for the pull,
      // and this used to be a static screen with no gesture on it at all.
      return ScopedBuilder<MastodonAccountsStore, List<MastodonAccount>>(
        store: context.read<MastodonAccountsStore>(),
        onState: (context, accounts) => EmptyPane(
          icon: Icons.public,
          message: accounts.isEmpty
              ? l10n.plugin_mastodon_empty
              : l10n.plugin_mastodon_no_posts,
          scrollController: widget.scrollController,
          onRefresh: () =>
              context.read<MastodonFeedStore>().refresh(force: true),
          action: accounts.isEmpty
              ? FilledButton.icon(
                  onPressed: () => showMastodonSearchSheet(context),
                  icon: const Icon(Icons.explore_outlined),
                  label: Text(l10n.plugin_mastodon_discover),
                )
              : FilledButton.icon(
                  onPressed: () =>
                      context.read<MastodonFeedStore>().refresh(force: true),
                  icon: const Icon(Icons.refresh),
                  label: Text(l10n.retry),
                ),
        ),
      );
    }

    return RefreshIndicator(
      // Past the ten-minute per-account cache: the pull is the reader asking.
      onRefresh: () => context.read<MastodonFeedStore>().refresh(force: true),
      child: FeedListView(
        controller: widget.scrollController,
        itemCount: posts.length,
        itemBuilder: (context, index) => MastodonPostCard(
          key: ValueKey(posts[index].id),
          post: posts[index],
          showSourceBadge: false,
        ),
      ),
    );
  }
}

Future<String?> showMastodonAddAccountDialog(
  BuildContext context, {
  bool lookup = false,
}) {
  final controller = TextEditingController();

  return showDialog<String>(
    context: context,
    builder: (dialogContext) {
      final l10n = L10n.of(dialogContext);
      String? error;

      return StatefulBuilder(
        builder: (context, setState) => AlertDialog(
          title: Text(
            lookup ? l10n.plugin_mastodon_lookup : l10n.plugin_mastodon_add,
          ),
          content: TextField(
            controller: controller,
            autofocus: true,
            decoration: InputDecoration(
              hintText: l10n.plugin_mastodon_handle_hint,
              errorText: error,
            ),
            onSubmitted: (_) {
              final acct = normaliseMastodonAcct(controller.text);
              if (acct == null) {
                setState(() => error = l10n.plugin_mastodon_invalid_handle);
              } else {
                Navigator.pop(context, acct);
              }
            },
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text(l10n.cancel),
            ),
            TextButton(
              onPressed: () {
                final acct = normaliseMastodonAcct(controller.text);
                if (acct == null) {
                  setState(() => error = l10n.plugin_mastodon_invalid_handle);
                } else {
                  Navigator.pop(context, acct);
                }
              },
              child: Text(l10n.ok),
            ),
          ],
        ),
      );
    },
  );
}
