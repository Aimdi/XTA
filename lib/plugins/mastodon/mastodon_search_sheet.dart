import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/mastodon/mastodon_search_store.dart';
import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/plugins/mastodon/mastodon_search_results.dart';
import 'package:xta/search/recent_searches_store.dart';
import 'package:xta/search/recent_searches_bar.dart';

/// Kept as an entry-point alias for plugin search; discovery now has a full route.
Future<void> showMastodonSearchSheet(BuildContext context, {String? initialQuery}) =>
    Navigator.push<void>(context, MaterialPageRoute(
      builder: (_) => MastodonSearchScreen(initialQuery: initialQuery)));

List<String> _discoveryInstances(BuildContext context) {
  final configured = mastodonConfiguredInstances(
    PrefService.of(context, listen: false),
  );
  final ordered = [...configured, ...kMastodonDefaultInstances];
  final seen = <String>{};
  return [
    for (final candidate in ordered)
      if (normaliseMastodonInstance(candidate) case final instance?
          when seen.add(instance))
        instance,
  ];
}

class MastodonSearchScreen extends StatefulWidget {
  final String? initialQuery;

  const MastodonSearchScreen({super.key, this.initialQuery});

  @override
  State<MastodonSearchScreen> createState() => _MastodonSearchScreenState();
}

class _MastodonSearchScreenState extends State<MastodonSearchScreen> {
  late final TextEditingController _controller;
  late final MastodonSearchStore _store;
  final _positions = [ScrollController(), ScrollController(), ScrollController()];
  late final RecentSearchesStore _history;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialQuery ?? '');
    _store = MastodonSearchStore(context.read<MastodonClient>(), _discoveryInstances(context));
    _history = RecentSearchesStore(PrefService.of(context, listen: false));
    WidgetsBinding.instance.addPostFrameCallback((_) { if (mounted) _search(); });
  }

  @override
  void dispose() {
    _controller.dispose();
    for (final controller in _positions) { controller.dispose(); }
    _store.destroy();
    _history.destroy();
    super.dispose();
  }

  Future<void> _search() async {
    FocusScope.of(context).unfocus();
    for (final controller in _positions) {
      if (controller.hasClients) controller.jumpTo(0);
    }
    await _history.remember('mastodon', _controller.text);
    await _store.search(_controller.text);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_mastodon_search)),
      body: Column(children: [
        Padding(padding: const EdgeInsets.fromLTRB(16, 8, 16, 12), child: TextField(
          key: const ValueKey('mastodon-search-field'), controller: _controller,
          textInputAction: TextInputAction.search,
          decoration: InputDecoration(hintText: l10n.plugin_mastodon_search_hint,
            prefixIcon: const Icon(Icons.search), filled: true,
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(28), borderSide: BorderSide.none),
            suffixIcon: IconButton(icon: const Icon(Icons.arrow_forward),
              tooltip: l10n.plugin_mastodon_search, onPressed: _search)),
          onSubmitted: (_) => _search(),
        )),
        Expanded(child: ScopedBuilder<MastodonSearchStore, MastodonSearchState>(
          store: _store, onState: (context, state) => KeyedSubtree(
            key: PageStorageKey('mastodon-search-${state.query}'), child: Column(children: [
              if (state.query.isEmpty) RecentSearchesBar(store: _history, scope: 'mastodon',
                onSelected: (query) { _controller.text = query; _search(); }),
              Expanded(child: MastodonSearchResults(state: state, onRetry: _search,
                onSelected: _store.select, positions: _positions)),
            ])))),
      ]),
    );
  }

}

/// Public posts for one hashtag on the reader's discovery instances.
class MastodonTagScreen extends StatefulWidget {
  final String tag;

  const MastodonTagScreen({super.key, required this.tag});

  @override
  State<MastodonTagScreen> createState() => _MastodonTagScreenState();
}

class _MastodonTagScreenState extends State<MastodonTagScreen> {
  List<MastodonPost> _posts = const [];
  Object? _error;
  String? _instance;
  var _loading = true;
  var _loadingMore = false;
  var _hasMore = true;
  var _backedOff = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _load();
    });
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
      _backedOff = false;
    });
    try {
      final client = context.read<MastodonClient>();
      final page = await client.firstInstanceThat(
        _discoveryInstances(context),
        (instance) async {
          final posts = await client.getTagTimeline(instance, widget.tag);
          return (instance: instance, posts: posts);
        },
      );
      if (!mounted) return;
      setState(() {
        _posts = page.posts;
        _instance = page.instance;
        _hasMore = page.posts.length >= 30;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
      });
    }
  }

  Future<void> _loadMore() async {
    final instance = _instance;
    if (!_hasMore ||
        _loadingMore ||
        _backedOff ||
        instance == null ||
        _posts.isEmpty) {
      return;
    }
    setState(() => _loadingMore = true);
    try {
      final more = await context.read<MastodonClient>().getTagTimeline(
        instance,
        widget.tag,
        maxId: _posts.last.id,
      );
      if (!mounted) return;
      setState(() {
        _posts = appendUniqueMastodonPosts(_posts, more);
        _hasMore = more.length >= 30;
        _loadingMore = false;
      });
    } catch (_) {
      if (mounted) {
        setState(() {
          _loadingMore = false;
          _backedOff = true;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final title = '#${widget.tag.replaceFirst(RegExp(r'^#'), '')}';

    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text(
                  mastodonErrorMessage(l10n, _error!),
                  textAlign: TextAlign.center,
                ),
              ),
            )
          : _posts.isEmpty
          ? Center(child: Text(l10n.plugin_mastodon_no_posts))
          : NotificationListener<ScrollNotification>(
              onNotification: (notification) {
                if (notification is UserScrollNotification) {
                  _backedOff = false;
                }
                if (notification.metrics.pixels >
                    notification.metrics.maxScrollExtent - 1200) {
                  _loadMore();
                }
                return false;
              },
              child: RefreshIndicator(
                onRefresh: _load,
                child: FeedListView(
                  itemCount: _posts.length + (_loadingMore ? 1 : 0),
                  itemBuilder: (context, index) {
                    if (index >= _posts.length) {
                      return const Padding(
                        padding: EdgeInsets.all(16),
                        child: Center(child: CircularProgressIndicator()),
                      );
                    }
                    return MastodonPostCard(
                      key: ValueKey(_posts[index].id),
                      post: _posts[index],
                      showSourceBadge: false,
                    );
                  },
                ),
              ),
            ),
    );
  }
}
