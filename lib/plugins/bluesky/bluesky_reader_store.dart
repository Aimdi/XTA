import 'dart:async';
import 'dart:convert';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/plugin_reading_view.dart';
import 'package:xta/utils/json.dart';

enum BlueskyReaderContent { all, images, videos, links }

enum BlueskyReaderOrder { feed, newest, oldest }

class BlueskyReaderOptions {
  final String query;
  final BlueskyReaderContent content;
  final BlueskyReaderOrder order;
  final bool hideReplies;
  final bool hideReposts;
  const BlueskyReaderOptions({
    this.query = '',
    this.content = BlueskyReaderContent.all,
    this.order = BlueskyReaderOrder.feed,
    this.hideReplies = false,
    this.hideReposts = false,
  });
  bool get filtered => query.trim().isNotEmpty || content != BlueskyReaderContent.all || hideReplies || hideReposts;
  BlueskyReaderOptions copy({
    String? query,
    BlueskyReaderContent? content,
    BlueskyReaderOrder? order,
    bool? hideReplies,
    bool? hideReposts,
  }) => BlueskyReaderOptions(
    query: query ?? this.query,
    content: content ?? this.content,
    order: order ?? this.order,
    hideReplies: hideReplies ?? this.hideReplies,
    hideReposts: hideReposts ?? this.hideReposts,
  );
  Map<String, Object> toJson() => {
    'content': content.name,
    'order': order.name,
    'hideReplies': hideReplies,
    'hideReposts': hideReposts,
  };
  static BlueskyReaderOptions parse(Json json) => BlueskyReaderOptions(
    content:
        BlueskyReaderContent.values.where((value) => value.name == json['content'].string).firstOrNull ??
        BlueskyReaderContent.all,
    order:
        BlueskyReaderOrder.values.where((value) => value.name == json['order'].string).firstOrNull ??
        BlueskyReaderOrder.feed,
    hideReplies: json['hideReplies'].boolean ?? false,
    hideReposts: json['hideReposts'].boolean ?? false,
  );
}

class BlueskyReaderState {
  final int tab;
  final Map<String, BlueskyReaderOptions> options;
  const BlueskyReaderState({this.tab = 0, this.options = const {}});
}

const blueskyReaderPreference = 'plugin.bluesky.reader.v1';
const blueskyReadingLimit = 144;

class BlueskyReaderStore extends Store<BlueskyReaderState> {
  final BasePrefService prefs;
  String _source;
  Timer? _timer;
  Future<void> _writes = Future.value();
  PluginReadingPosition<BlueskyPost>? _point;
  PluginReadingPosition<BlueskyPost>? _origin;
  bool _positioned = false;
  bool _closed = false;
  bool get enabled =>
      !prefs.getKeys().contains(optionFeedReadingPosition) || prefs.get(optionFeedReadingPosition) != false;

  BlueskyReaderStore(this.prefs, String source) : _source = source, super(const BlueskyReaderState()) {
    try {
      if (!prefs.getKeys().contains(blueskyReaderPreference)) return;
      final raw = prefs.get<String>(blueskyReaderPreference) ?? '';
      if (raw.isEmpty || raw.length > 2500000) return;
      final json = Json(jsonDecode(raw));
      if (json['version'].integer != 1) return;
      update(
        BlueskyReaderState(
          tab: (json['tab'].integer ?? 0).clamp(0, 3),
          options: {
            for (final slot in ['following', 'likes']) slot: BlueskyReaderOptions.parse(json['options'][slot]),
          },
        ),
      );
      if (!enabled || json['source'].string != source) return;
      final posts = [
        for (final item in json['posts'].list.take(blueskyReadingLimit)) BlueskyPost.fromSnapshot(item.raw),
      ].where((post) => post.uri.startsWith('at://') && post.url.startsWith('https://')).toList();
      if (posts.isEmpty) return;
      final leading = json['leading'].number ?? 0;
      _point = PluginReadingPosition(
        posts: posts,
        anchor: json['anchor'].string ?? posts.first.uri,
        leading: leading.isFinite ? leading.clamp(-10000.0, 10000.0) : 0,
      );
      _origin = _point;
    } catch (_) {
      /* A corrupt snapshot does not prevent a fresh public read. */
    }
  }

  BlueskyReaderOptions options(String slot) => state.options[slot] ?? const BlueskyReaderOptions();
  void configure(String slot, BlueskyReaderOptions options) {
    if (_closed) return;
    update(BlueskyReaderState(tab: state.tab, options: {...state.options, slot: options}));
    _schedule();
  }

  void selectTab(int tab) {
    if (_closed || tab == state.tab) return;
    update(BlueskyReaderState(tab: tab, options: state.options));
    _schedule();
  }

  void changeSource(String source) {
    if (_closed || source == _source) return;
    _source = source;
    _point = _origin = null;
    _positioned = false;
    _schedule();
  }

  ({PluginReadingPosition<BlueskyPost>? point, bool restore}) layoutPoint() {
    if (!enabled) return (point: null, restore: false);
    final restore = !_positioned;
    _positioned = true;
    return (point: _origin, restore: restore);
  }

  List<BlueskyPost> restorePosts(List<BlueskyAccount> accounts) {
    if (!enabled) return [];
    final actors = {
      for (final account in accounts) account.actor.trim().toLowerCase(),
      for (final account in accounts) account.handle.trim().toLowerCase(),
    }..remove('');
    return [
      for (final post in _point?.posts ?? <BlueskyPost>[])
        if ([
          post.handle,
          post.did,
          post.repostedByHandle,
          post.repostedByDid,
        ].any((value) => value != null && actors.contains(value.toLowerCase())))
          post,
    ];
  }

  void remember(PluginReadingPosition<BlueskyPost> point) {
    if (_closed || !enabled) return;
    _point = point;
    _schedule();
  }

  Future<void> reset() async {
    if (_closed) return;
    _timer?.cancel();
    _point = _origin = null;
    _positioned = false;
    update(const BlueskyReaderState());
    await flush();
  }

  void _schedule() {
    _timer?.cancel();
    _timer = Timer(const Duration(milliseconds: 350), flush);
  }

  Future<void> flush() {
    _timer?.cancel();
    final point = enabled ? _point : null;
    var posts = point?.posts.take(blueskyReadingLimit).toList() ?? <BlueskyPost>[];
    Map<String, Object?> payload() => {
      'version': 1,
      'source': _source,
      'tab': state.tab,
      'options': state.options.map((key, value) => MapEntry(key, value.toJson())),
      'anchor': point?.anchor,
      'leading': point?.leading ?? 0,
      'posts': posts.map((post) => post.toJson()).toList(),
    };
    var raw = jsonEncode(payload());
    if (raw.length > 2000000) {
      posts = [];
      raw = jsonEncode(payload());
    }
    _writes = _writes
        .then((_) async {
          await prefs.set(blueskyReaderPreference, raw);
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

List<BlueskyPost> filterBlueskyReader(List<BlueskyPost> posts, BlueskyReaderOptions options) {
  final terms = options.query.trim().toLowerCase().split(RegExp(r'\s+')).where((term) => term.isNotEmpty).toList();
  final seen = <String>{};
  final result = posts.where((post) {
    if (options.hideReposts && post.isRepost || options.hideReplies && post.isReply) return false;
    final matchesContent = switch (options.content) {
      BlueskyReaderContent.all => true,
      BlueskyReaderContent.images => post.mediaItems.any((item) => !item.isVideo),
      BlueskyReaderContent.videos => post.mediaItems.any((item) => item.isVideo),
      BlueskyReaderContent.links => post.hasLinkCard || post.facets.any((facet) => facet.kind.name == 'link'),
    };
    if (!matchesContent) return false;
    final text = '${post.text} ${post.authorName} ${post.handle} ${post.linkCard?.title ?? ''}'.toLowerCase();
    return terms.every(text.contains) && seen.add(post.uri);
  }).toList();
  if (options.order == BlueskyReaderOrder.feed) return result;
  final positions = {for (var i = 0; i < result.length; i++) result[i].uri: i};
  result.sort((a, b) {
    final left = a.timelineDate, right = b.timelineDate;
    if (left == null && right != null) return 1;
    if (left != null && right == null) return -1;
    final compare = left == null || right == null ? 0 : left.compareTo(right);
    return compare == 0
        ? positions[a.uri]!.compareTo(positions[b.uri]!)
        : options.order == BlueskyReaderOrder.oldest
        ? compare
        : -compare;
  });
  return result;
}
