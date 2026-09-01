import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/tiktok/tiktok_client.dart';
import 'package:xta/plugins/tiktok/tiktok_errors.dart';
import 'package:xta/plugins/tiktok/tiktok_models.dart';
import 'package:xta/plugins/tiktok/tiktok_parse.dart';
import 'package:xta/plugins/tiktok/tiktok_post_card.dart';
import 'package:xta/plugins/tiktok/tiktok_profile_screen.dart';
import 'package:xta/plugins/tiktok/tiktok_store.dart';
import 'package:xta/plugins/plugin_counts.dart';

Future<void> showTikTokSearchSheet(
  BuildContext context, {
  String? initialQuery,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (_) => _TikTokSearchSheet(initialQuery: initialQuery),
  );
}

class _TikTokSearchSheet extends StatefulWidget {
  final String? initialQuery;

  const _TikTokSearchSheet({this.initialQuery});

  @override
  State<_TikTokSearchSheet> createState() => _TikTokSearchSheetState();
}

class _TikTokSearchSheetState extends State<_TikTokSearchSheet>
    with SingleTickerProviderStateMixin {
  late final TextEditingController _controller;
  late final TabController _tabs;
  Timer? _suggestDebounce;
  List<TikTokSearchUser> _people = const [];
  List<TikTokSearchUser> _suggested = const [];
  List<TikTokPost> _posts = const [];
  List<String> _suggestions = const [];
  List<String> _trending = const [];
  Object? _error;
  var _loading = false;
  var _landing = true;
  var _searched = false;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialQuery ?? '');
    _tabs = TabController(length: 2, vsync: this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      context.read<TikTokSearchHistoryStore>().load();
      if ((widget.initialQuery ?? '').trim().isNotEmpty) {
        _search();
      } else {
        _loadLanding();
      }
    });
  }

  @override
  void dispose() {
    _suggestDebounce?.cancel();
    _controller.dispose();
    _tabs.dispose();
    super.dispose();
  }

  Future<void> _loadLanding() async {
    setState(() {
      _landing = true;
      _error = null;
    });
    try {
      final client = context.read<TikTokClient>();
      final users = await client.discoverUsers();
      final trending = await client.trendingQueries();
      if (!mounted) return;
      setState(() {
        _suggested = users;
        _trending = trending;
        _landing = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _landing = false;
      });
    }
  }

  void _onQueryChanged(String value) {
    _suggestDebounce?.cancel();
    if (value.trim().isEmpty) {
      setState(() {
        _suggestions = const [];
        _searched = false;
        _error = null;
      });
      return;
    }
    _suggestDebounce = Timer(const Duration(milliseconds: 350), () {
      if (mounted) _loadSuggestions(value);
    });
  }

  Future<void> _loadSuggestions(String raw) async {
    try {
      final words = await context.read<TikTokClient>().suggestQueries(raw);
      if (!mounted || _controller.text.trim() != raw.trim()) return;
      setState(() => _suggestions = words);
    } catch (_) {}
  }

  Future<void> _search([String? raw]) async {
    final query = (raw ?? _controller.text).trim();
    if (query.isEmpty) return;
    if (raw != null && raw != _controller.text) {
      _controller.text = raw;
    }

    setState(() {
      _loading = true;
      _error = null;
      _searched = true;
    });
    try {
      await context.read<TikTokSearchHistoryStore>().remember(query);
      if (!mounted) return;
      final page = await context.read<TikTokClient>().search(query);
      if (!mounted) return;
      setState(() {
        _people = page.users;
        _posts = page.posts;
        _suggestions = page.suggestions;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
        _people = const [];
        _posts = const [];
      });
    }
  }

  Future<void> _openUser(String handle) async {
    if (!mounted) return;
    Navigator.pop(context);
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => TikTokProfileScreen(handle: handle)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final bottom = MediaQuery.viewInsetsOf(context).bottom;

    return Padding(
      padding: EdgeInsets.only(bottom: bottom),
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.75,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
              child: TextField(
                controller: _controller,
                autofocus: widget.initialQuery == null,
                textInputAction: TextInputAction.search,
                decoration: InputDecoration(
                  filled: true,
                  hintText: l10n.plugin_tiktok_search_hint,
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: IconButton(
                    tooltip: l10n.plugin_tiktok_search,
                    icon: const Icon(Icons.arrow_forward),
                    onPressed: () => _search(),
                  ),
                ),
                onChanged: _onQueryChanged,
                onSubmitted: _search,
              ),
            ),
            TabBar(
              controller: _tabs,
              onTap: (_) => setState(() {}),
              tabs: [
                Tab(text: l10n.plugin_tiktok_search_people),
                Tab(text: l10n.plugin_tiktok_search_videos),
              ],
            ),
            Expanded(child: _body(l10n)),
          ],
        ),
      ),
    );
  }

  Widget _body(L10n l10n) {
    if (_loading || _landing) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null && _searched) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            tiktokErrorMessage(l10n, _error),
            textAlign: TextAlign.center,
          ),
        ),
      );
    }
    if (!_searched) return _landingList(l10n);
    return _tabs.index == 0 ? _peopleList(l10n) : _videoList(l10n);
  }

  Widget _landingList(L10n l10n) {
    final theme = Theme.of(context);
    final typed = _controller.text.trim();
    final direct = normaliseTikTokHandle(typed);
    return ListView(
      children: [
        if (direct != null)
          ListTile(
            leading: const Icon(Icons.person_outline),
            title: Text('@$direct'),
            subtitle: Text(l10n.plugin_tiktok_open_handle),
            onTap: () => _openUser(direct),
          ),
        if (_suggestions.isNotEmpty)
          _chipSection(
            l10n.plugin_tiktok_search,
            _suggestions,
            onTap: (word) => _search(word),
          ),
        ScopedBuilder<TikTokSearchHistoryStore, List<String>>(
          store: context.read<TikTokSearchHistoryStore>(),
          onState: (context, history) {
            if (history.isEmpty) return const SizedBox.shrink();
            return _historySection(l10n, theme, history);
          },
        ),
        if (_trending.isNotEmpty)
          _chipSection(
            l10n.plugin_tiktok_trending_searches,
            _trending,
            onTap: (word) => _search(word),
          ),
        if (_suggested.isNotEmpty) ...[
          _sectionTitle(theme, l10n.plugin_tiktok_suggested),
          for (final user in _suggested.take(20)) _userTile(user),
        ],
        if (_suggested.isEmpty && _trending.isEmpty)
          Padding(
            padding: const EdgeInsets.all(24),
            child: Text(
              l10n.plugin_tiktok_search_prompt,
              textAlign: TextAlign.center,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
      ],
    );
  }

  Widget _historySection(L10n l10n, ThemeData theme, List<String> history) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _sectionTitle(theme, l10n.plugin_tiktok_recent_searches),
        for (final item in history)
          ListTile(
            leading: const Icon(Icons.history),
            title: Text(normaliseTikTokHandle(item) == null ? item : '@$item'),
            trailing: Icon(
              Icons.chevron_right,
              color: theme.colorScheme.onSurfaceVariant,
            ),
            onTap: () => _search(item),
          ),
      ],
    );
  }

  Widget _peopleList(L10n l10n) {
    if (_people.isEmpty) {
      return _emptyResults(l10n);
    }
    return ListView(
      children: [
        if (_suggestions.isNotEmpty)
          _chipSection(
            l10n.plugin_tiktok_search,
            _suggestions,
            onTap: (word) => _search(word),
          ),
        for (final user in _people) _userTile(user),
      ],
    );
  }

  Widget _videoList(L10n l10n) {
    if (_posts.isEmpty) {
      return _emptyResults(l10n);
    }
    return ListView.builder(
      itemCount: _posts.length,
      itemBuilder: (context, index) => TikTokPostCard(post: _posts[index]),
    );
  }

  Widget _emptyResults(L10n l10n) {
    return ListView(
      children: [
        if (_suggestions.isNotEmpty)
          _chipSection(
            l10n.plugin_tiktok_search,
            _suggestions,
            onTap: (word) => _search(word),
          ),
        Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            l10n.plugin_tiktok_search_empty,
            textAlign: TextAlign.center,
          ),
        ),
      ],
    );
  }

  Widget _userTile(TikTokSearchUser user) {
    final theme = Theme.of(context);
    final followers = user.followerCount > 0
        ? compactCount(user.followerCount)
        : null;
    return ListTile(
      leading: TikTokAvatar(
        url: user.avatarUrl,
        seed: user.uniqueId,
        name: user.displayName,
        size: 44,
      ),
      title: Row(
        children: [
          Flexible(
            child: Text(
              user.displayName,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          if (user.verified) ...[
            const SizedBox(width: 4),
            Icon(Icons.verified, size: 16, color: theme.colorScheme.primary),
          ],
        ],
      ),
      subtitle: Text(
        followers == null
            ? '@${user.uniqueId}'
            : '@${user.uniqueId} · $followers',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      onTap: () => _openUser(user.uniqueId),
    );
  }

  Widget _sectionTitle(ThemeData theme, String label) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
      child: Text(
        label,
        style: theme.textTheme.titleSmall?.copyWith(
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }

  Widget _chipSection(
    String title,
    List<String> words, {
    required void Function(String) onTap,
  }) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: Theme.of(
              context,
            ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final word in words.take(12))
                ActionChip(label: Text(word), onPressed: () => onTap(word)),
            ],
          ),
        ],
      ),
    );
  }
}
