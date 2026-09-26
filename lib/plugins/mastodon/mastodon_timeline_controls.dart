import 'package:xta/plugins/plugin_home_dock.dart';
import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/utils/json.dart';

enum MastodonContentFilter { all, media, links }

enum MastodonTimelineOrder { feed, newest, oldest }

class MastodonTimelineOptions {
  final String query;
  final MastodonContentFilter content;
  final MastodonTimelineOrder order;
  final bool hideBoosts;
  final bool hideReplies;
  const MastodonTimelineOptions({
    this.query = '',
    this.content = MastodonContentFilter.all,
    this.order = MastodonTimelineOrder.feed,
    this.hideBoosts = false,
    this.hideReplies = false,
  });

  int get activeFilters =>
      (query.trim().isEmpty ? 0 : 1) +
      (content == MastodonContentFilter.all ? 0 : 1) +
      (hideBoosts ? 1 : 0) +
      (hideReplies ? 1 : 0);

  MastodonTimelineOptions copy({
    String? query,
    MastodonContentFilter? content,
    MastodonTimelineOrder? order,
    bool? hideBoosts,
    bool? hideReplies,
  }) => MastodonTimelineOptions(
    query: query ?? this.query,
    content: content ?? this.content,
    order: order ?? this.order,
    hideBoosts: hideBoosts ?? this.hideBoosts,
    hideReplies: hideReplies ?? this.hideReplies,
  );

  Map<String, Object> toJson() => {
    'content': content.name,
    'order': order.name,
    'hideBoosts': hideBoosts,
    'hideReplies': hideReplies,
  };

  static MastodonTimelineOptions parse(Object? raw) {
    final json = Json(raw);
    return MastodonTimelineOptions(
      content:
          MastodonContentFilter.values.where((value) => value.name == json['content'].string).firstOrNull ??
          MastodonContentFilter.all,
      order:
          MastodonTimelineOrder.values.where((value) => value.name == json['order'].string).firstOrNull ??
          MastodonTimelineOrder.feed,
      hideBoosts: json['hideBoosts'].boolean ?? false,
      hideReplies: json['hideReplies'].boolean ?? false,
    );
  }
}

const mastodonTimelinePreference = 'plugin.mastodon.timeline.v1';

class MastodonTimelineControlsStore extends Store<Map<String, MastodonTimelineOptions>> {
  final BasePrefService prefs;
  Timer? _timer;
  Future<void> _writes = Future.value();
  bool _closed = false;
  MastodonTimelineControlsStore(this.prefs) : super(const {}) {
    try {
      if (!prefs.getKeys().contains(mastodonTimelinePreference)) return;
      final raw = prefs.get<String>(mastodonTimelinePreference) ?? '';
      if (raw.length > 10000) return;
      final json = Json(jsonDecode(raw));
      update({for (var tab = 0; tab < 4; tab++) '$tab': MastodonTimelineOptions.parse(json['$tab'].raw)});
    } catch (_) {
      /* Invalid preferences leave the complete feed visible. */
    }
  }

  String _key(String slot) => slot.split(':').last;
  MastodonTimelineOptions options(String slot) => state[_key(slot)] ?? const MastodonTimelineOptions();

  void select(String slot, MastodonTimelineOptions options) {
    if (_closed) return;
    update({...state, _key(slot): options});
    _timer?.cancel();
    _timer = Timer(const Duration(milliseconds: 250), flush);
  }

  void reset(String slot) => select(slot, const MastodonTimelineOptions());

  Future<void> flush() {
    _timer?.cancel();
    final raw = jsonEncode(state.map((key, value) => MapEntry(key, value.toJson())));
    _writes = _writes
        .then((_) async {
          await prefs.set(mastodonTimelinePreference, raw);
        })
        .catchError((Object _) {});
    return _writes;
  }

  @override
  Future<void> destroy() async {
    _closed = true;
    await flush();
    await super.destroy();
  }
}

List<MastodonPost> filterMastodonTimeline(List<MastodonPost> posts, MastodonTimelineOptions options) {
  final seen = <String>{};
  final terms = options.query.toLowerCase().trim().split(RegExp(r'\s+')).where((term) => term.isNotEmpty);
  final matches = posts.where((post) {
    if (options.hideBoosts && post.boosted) return false;
    if (options.hideReplies && (post.replyToId != null || post.replyToAcct != null)) return false;
    if (options.content == MastodonContentFilter.media && !post.hasMedia) return false;
    if (options.content == MastodonContentFilter.links &&
        post.linkCard == null &&
        !RegExp(r'https?://', caseSensitive: false).hasMatch(post.text)) {
      return false;
    }
    final haystack = '${post.text} ${post.spoilerText} ${post.acct} ${post.authorName} ${post.linkCard?.title ?? ''}'
        .toLowerCase();
    return terms.every(haystack.contains) && seen.add(canonicalMastodonPostKey(post));
  }).toList();
  if (options.order == MastodonTimelineOrder.feed) return matches;
  final positions = {for (var i = 0; i < matches.length; i++) canonicalMastodonPostKey(matches[i]): i};
  matches.sort((a, b) {
    final left = a.timelineDate;
    final right = b.timelineDate;
    if (left == null && right != null) return 1;
    if (left != null && right == null) return -1;
    var order = left == null || right == null ? 0 : left.compareTo(right);
    if (options.order == MastodonTimelineOrder.newest) order = -order;
    return order == 0
        ? positions[canonicalMastodonPostKey(a)]!.compareTo(positions[canonicalMastodonPostKey(b)]!)
        : order;
  });
  return matches;
}

String mastodonOrderLabel(L10n l10n, MastodonTimelineOrder order) => switch (order) {
  MastodonTimelineOrder.feed => l10n.plugin_mastodon_order_server,
  MastodonTimelineOrder.newest => l10n.plugin_mastodon_order_newest,
  MastodonTimelineOrder.oldest => l10n.plugin_mastodon_order_oldest,
};

class MastodonTimelineToolbar extends StatelessWidget {
  final MastodonTimelineControlsStore store;
  final String slot;
  final MastodonTimelineOptions options;
  const MastodonTimelineToolbar({super.key, required this.store, required this.slot, required this.options});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final fallback = Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8),
      child: Row(
        children: [
          Expanded(
            child: Align(
              alignment: AlignmentDirectional.centerStart,
              child: TextButton.icon(
                onPressed: () => showModalBottomSheet<void>(
                  context: context,
                  isScrollControlled: true,
                  useSafeArea: true,
                  builder: (_) => _TimelineSheet(store: store, slot: slot),
                ),
                icon: Badge(
                  isLabelVisible: options.activeFilters > 0,
                  label: Text('${options.activeFilters}'),
                  child: const Icon(Icons.tune),
                ),
                label: Text(l10n.filters),
              ),
            ),
          ),
          Flexible(
            child: PopupMenuButton<MastodonTimelineOrder>(
              tooltip: l10n.plugin_mastodon_sort,
              initialValue: options.order,
              onSelected: (order) => store.select(slot, options.copy(order: order)),
              itemBuilder: (_) => [
                for (final order in MastodonTimelineOrder.values)
                  CheckedPopupMenuItem(
                    value: order,
                    checked: options.order == order,
                    child: Text(mastodonOrderLabel(l10n, order)),
                  ),
              ],
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Flexible(child: Text(mastodonOrderLabel(l10n, options.order))),
                    const SizedBox(width: 4),
                    const Icon(Icons.sort, size: 20),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
    return PluginDockContribution(
      slot: 'reading',
      content: PluginDockContent(
        trailing: [
          PluginDockFilterButton(
            activeCount: options.activeFilters + (options.order == MastodonTimelineOrder.feed ? 0 : 1),
            onPressed: () => showModalBottomSheet<void>(
              context: context,
              isScrollControlled: true,
              useSafeArea: true,
              builder: (_) => _TimelineSheet(store: store, slot: slot),
            ),
          ),
        ],
      ),
      fallback: fallback,
    );
  }
}

class _TimelineSheet extends StatefulWidget {
  final MastodonTimelineControlsStore store;
  final String slot;
  const _TimelineSheet({required this.store, required this.slot});
  @override
  State<_TimelineSheet> createState() => _TimelineSheetState();
}

class _TimelineSheetState extends State<_TimelineSheet> {
  late final TextEditingController _query = TextEditingController(text: widget.store.options(widget.slot).query);
  @override
  void dispose() {
    _query.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<MastodonTimelineControlsStore, Map<String, MastodonTimelineOptions>>(
      store: widget.store,
      onState: (context, _) {
        final options = widget.store.options(widget.slot);
        void select(MastodonTimelineOptions value) => widget.store.select(widget.slot, value);
        return SingleChildScrollView(
          padding: EdgeInsets.fromLTRB(16, 16, 16, MediaQuery.viewInsetsOf(context).bottom + 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(child: Text(l10n.filters, style: Theme.of(context).textTheme.titleLarge)),
                  IconButton(
                    tooltip: l10n.close,
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
              Text(l10n.plugin_mastodon_loaded_controls, style: Theme.of(context).textTheme.bodySmall),
              const SizedBox(height: 12),
              Text(l10n.plugin_mastodon_sort, style: Theme.of(context).textTheme.titleSmall),
              Wrap(
                spacing: 8,
                children: [
                  for (final order in MastodonTimelineOrder.values)
                    ChoiceChip(
                      label: Text(mastodonOrderLabel(l10n, order)),
                      selected: options.order == order,
                      onSelected: (_) => select(options.copy(order: order)),
                      materialTapTargetSize: MaterialTapTargetSize.padded,
                    ),
                ],
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _query,
                decoration: InputDecoration(
                  labelText: l10n.plugin_mastodon_loaded_search,
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: options.query.isEmpty
                      ? null
                      : IconButton(
                          tooltip: l10n.plugin_mastodon_clear_search,
                          onPressed: () {
                            _query.clear();
                            select(options.copy(query: ''));
                          },
                          icon: const Icon(Icons.clear),
                        ),
                ),
                onChanged: (value) => select(options.copy(query: value)),
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                children: [
                  for (final content in MastodonContentFilter.values)
                    ChoiceChip(
                      label: Text(switch (content) {
                        MastodonContentFilter.all => l10n.all,
                        MastodonContentFilter.media => l10n.media,
                        MastodonContentFilter.links => l10n.search_links,
                      }),
                      selected: options.content == content,
                      onSelected: (_) => select(options.copy(content: content)),
                    ),
                ],
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(l10n.plugin_mastodon_hide_boosts),
                value: options.hideBoosts,
                onChanged: (value) => select(options.copy(hideBoosts: value)),
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(l10n.hide_replies),
                value: options.hideReplies,
                onChanged: (value) => select(options.copy(hideReplies: value)),
              ),
              TextButton.icon(
                onPressed: () {
                  _query.clear();
                  widget.store.reset(widget.slot);
                },
                icon: const Icon(Icons.restart_alt),
                label: Text(l10n.plugin_reader_reset_filters),
              ),
            ],
          ),
        );
      },
    );
  }
}
