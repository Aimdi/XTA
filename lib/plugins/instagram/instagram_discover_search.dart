import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/instagram/instagram_client.dart';
import 'package:xta/plugins/instagram/instagram_errors.dart';
import 'package:xta/plugins/instagram/instagram_models.dart';
import 'package:xta/plugins/instagram/instagram_post_card.dart';
import 'package:xta/plugins/instagram/instagram_profile_screen.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';

/// Instagram account hits for the Discover hub.
class InstagramDiscoverSearch extends StatefulWidget {
  final String query;

  const InstagramDiscoverSearch({super.key, required this.query});

  @override
  State<InstagramDiscoverSearch> createState() =>
      _InstagramDiscoverSearchState();
}

class _InstagramDiscoverSearchState extends State<InstagramDiscoverSearch> {
  late final _InstagramDiscoverStore _store;

  @override
  void initState() {
    super.initState();
    _store = _InstagramDiscoverStore(context.read<InstagramClient>());
    _store.search(widget.query);
  }

  @override
  void didUpdateWidget(InstagramDiscoverSearch old) {
    super.didUpdateWidget(old);
    if (old.query != widget.query) {
      _store.search(widget.query);
    }
  }

  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  Future<void> _open(String handle) async {
    if (!mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => InstagramProfileScreen(handle: handle)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<_InstagramDiscoverStore, List<InstagramSearchUser>>(
      store: _store,
      onLoading: (_) => const Center(child: CircularProgressIndicator()),
      onError: (_, error) => FullPageErrorWidget(
        error: error,
        stackTrace: null,
        prefix: instagramErrorMessage(l10n, error),
        onRetry: () => _store.search(widget.query),
      ),
      onState: (context, users) {
        if (users.isEmpty) {
          return EmptyPane(
            icon: Icons.search_off,
            message: l10n.plugin_instagram_search_empty,
          );
        }
        return ListView(
          children: [
            for (final user in users)
              ListTile(
                leading: InstagramAvatar(
                  url: user.avatarUrl,
                  seed: user.username,
                  name: user.displayName,
                ),
                title: Text('@${user.username}'),
                subtitle: user.fullName.isEmpty ? null : Text(user.fullName),
                onTap: () => _open(user.username),
              ),
          ],
        );
      },
    );
  }
}

class _InstagramDiscoverStore extends Store<List<InstagramSearchUser>> {
  final InstagramClient client;

  _InstagramDiscoverStore(this.client) : super(const []);

  Future<void> search(String value) async {
    final query = value.trim();
    if (query.isEmpty) {
      update(const []);
      return;
    }
    await execute(() => client.searchUsers(query));
  }
}
