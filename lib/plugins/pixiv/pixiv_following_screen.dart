import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_group.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';
import 'package:xta/ui/errors.dart';

class PixivFollowingStore extends Store<List<PixivUser>> {
  final PixivClient client;
  String? _nextUrl;
  bool _private = false;
  bool _hasMore = true;
  bool _busy = false;
  bool _closed = false;
  int _generation = 0;
  Object? _pageError;

  PixivFollowingStore(this.client) : super(const []);
  bool get hasMore => _hasMore;
  bool get busy => _busy;
  Object? get pageError => _pageError;
  bool _current(int generation) => !_closed && generation == _generation;

  Future<void> refresh() => _load(reset: true);
  Future<void> loadMore() => _load(reset: false);

  Future<void> _load({required bool reset}) async {
    if (_closed || (!reset && (_busy || !_hasMore))) return;
    final generation = reset ? ++_generation : _generation;
    if (reset) { _nextUrl = null; _private = false; _hasMore = true; }
    _busy = true;
    _pageError = null;
    if (reset) { setLoading(true); } else { update(state, force: true); }
    try {
      var page = await _next(generation);
      if (!_current(generation)) return;
      if (reset && page.users.isEmpty && _hasMore) page = await _next(generation);
      if (!_current(generation)) return;
      final byId = {if (!reset) for (final user in state) user.id: user,
        for (final user in page.users) user.id: user};
      update(byId.values.toList());
    } catch (error) {
      if (_current(generation)) {
        if (reset) { setError(error); } else { _pageError = error; }
      }
    } finally {
      if (_current(generation)) {
        _busy = false;
        if (reset) { setLoading(false); } else { update(state, force: true); }
      }
    }
  }

  Future<PixivUserPage> _next(int generation) async {
    final page = await client.followedUsers(nextUrl: _nextUrl, private: _private);
    if (!_current(generation)) return page;
    _nextUrl = page.nextUrl;
    if (_nextUrl == null || _nextUrl!.isEmpty) {
      if (_private) { _hasMore = false; } else { _private = true; }
    }
    return page;
  }

  @override
  Future<void> destroy() { _closed = true; _generation++; return super.destroy(); }
}

class PixivFollowingScreen extends StatefulWidget {
  const PixivFollowingScreen({super.key});
  @override
  State<PixivFollowingScreen> createState() => _PixivFollowingScreenState();
}

class _PixivFollowingScreenState extends State<PixivFollowingScreen> {
  late final PixivFollowingStore _store;
  @override
  void initState() {
    super.initState();
    _store = PixivFollowingStore(context.read<PixivClient>())..refresh();
  }
  @override
  void dispose() { _store.destroy(); super.dispose(); }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(L10n.of(context).following)),
    body: ScopedBuilder<PixivFollowingStore, List<PixivUser>>(
      store: _store,
      onLoading: (_) => const Center(child: CircularProgressIndicator()),
      onError: (context, error) => FullPageErrorWidget(error: error, stackTrace: null,
        prefix: pixivErrorMessage(L10n.of(context), error), onRetry: _store.refresh),
      onState: (context, users) => RefreshIndicator(
        onRefresh: _store.refresh,
        child: ListView.builder(
          physics: const AlwaysScrollableScrollPhysics(),
          itemCount: users.length + 1,
          itemBuilder: (context, index) {
            if (index == users.length) return _tail(context, users.isEmpty);
            final user = users[index];
            return ListTile(
              leading: CircleAvatar(child: user.avatarUrl == null
                ? const Icon(Icons.person_outline)
                : ClipOval(child: PixivNetworkImage(url: user.avatarUrl!, fit: BoxFit.cover))),
              title: Text(user.name), subtitle: Text('@${user.account}'),
              onTap: () => Navigator.push(context,
                MaterialPageRoute(builder: (_) => PixivUserScreen(userId: user.id))),
              trailing: IconButton(tooltip: L10n.of(context).add_to_group,
                icon: const Icon(Icons.group_add_outlined), onPressed: () => addPixivToGroup(context, user)),
            );
          },
        ),
      ),
    ),
  );

  Widget _tail(BuildContext context, bool empty) => Padding(
    padding: const EdgeInsets.all(20),
    child: _store.hasMore ? OutlinedButton.icon(
      onPressed: _store.busy ? null : _store.loadMore,
      icon: const Icon(Icons.expand_more), label: Text(_store.pageError == null ? L10n.of(context).clickToShowMore : L10n.of(context).retry))
      : empty ? Text(L10n.of(context).no_subscriptions_try_searching_or_importing_some,
        textAlign: TextAlign.center) : const SizedBox.shrink(),
  );
}
