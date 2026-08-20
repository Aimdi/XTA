import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/hackernews/hn_client.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/hackernews/hn_store.dart';
import 'package:xta/plugins/hackernews/hn_story_card.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';

class HnUserScreen extends StatefulWidget {
  final String userId;

  const HnUserScreen({super.key, required this.userId});

  @override
  State<HnUserScreen> createState() => _HnUserScreenState();
}

class _HnUserScreenState extends State<HnUserScreen> {
  late final _HnUserStore _store;

  @override
  void initState() {
    super.initState();
    _store = _HnUserStore(context.read<HackerNewsClient>(), widget.userId);
    WidgetsBinding.instance.addPostFrameCallback((_) => _store.refresh());
  }

  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final follows = context.read<HnFollowsStore>();
    return Scaffold(
      appBar: AppBar(title: Text(widget.userId)),
      body: ScopedBuilder<_HnUserStore, _HnUserPage>.transition(
        store: _store,
        onLoading: (_) => const Center(child: CircularProgressIndicator()),
        onError: (_, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: error.toString(),
          onRetry: _store.refresh,
        ),
        onState: (_, page) => RefreshIndicator(
          onRefresh: _store.refresh,
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              ListTile(
                title: Text(page.user.id),
                subtitle: Text(l10n.plugin_hn_user_karma(page.user.karma)),
                trailing: ScopedBuilder<HnFollowsStore, List<String>>(
                  store: follows,
                  onState: (_, _) => TextButton(
                    onPressed: () => follows.toggle(page.user.id),
                    child: Text(
                      follows.isFollowing(page.user.id)
                          ? l10n.plugin_hn_unfollow
                          : l10n.plugin_hn_follow,
                    ),
                  ),
                ),
              ),
              if ((page.user.about ?? '').isNotEmpty)
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                  child: Text(page.user.about!),
                ),
              const Divider(height: 1),
              if (page.stories.isEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 48),
                  child: EmptyPane(
                    icon: Icons.person_outline,
                    message: l10n.plugin_hn_user_empty,
                  ),
                )
              else
                for (final story in page.stories) HnStoryCard(story: story),
            ],
          ),
        ),
      ),
    );
  }
}

class _HnUserPage {
  final HnUser user;
  final List<HnStory> stories;

  const _HnUserPage(this.user, this.stories);
}

class _HnUserStore extends Store<_HnUserPage> {
  final HackerNewsClient client;
  final String userId;

  _HnUserStore(this.client, this.userId)
    : super(const _HnUserPage(HnUser(id: ''), []));

  Future<void> refresh() async {
    await execute(() async {
      final user = await client.user(userId);
      final page = await client.submissions(userId);
      return _HnUserPage(user, page.stories);
    });
  }
}
