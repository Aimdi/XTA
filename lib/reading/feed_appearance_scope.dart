import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_dock.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/reading/feed_appearance_controls.dart';
import 'package:xta/reading/feed_appearance_store.dart';

bool feedAppearanceCapable(String source, {bool mixed = false}) =>
    {'x', 'reddit', 'mastodon', 'bluesky', 'threads', 'rss', 'substack'}.contains(source) || (source == 'mix' && mixed);

FeedIdentity homeFeedIdentity(String source) => switch (source) {
  'following' => const FeedIdentity('x', 'following'),
  'x' => const FeedIdentity('x', 'for-you'),
  _ => FeedIdentity(source, 'home'),
};

String feedAppearanceLabel(BuildContext context, FeedIdentity feed, FeedAppearanceStore store) {
  final l10n = L10n.of(context);
  if (feed == const FeedIdentity('x', 'following')) return l10n.following;
  if (feed == const FeedIdentity('x', 'for-you')) return l10n.foryou;
  return store.labelFor(feed) ?? pluginById(feed.source)?.title(context) ?? l10n.feed;
}

class _AppearanceData extends InheritedWidget {
  final FeedIdentity feed;
  final FeedAppearance appearance;
  const _AppearanceData({required this.feed, required this.appearance, required super.child});
  @override
  bool updateShouldNotify(_AppearanceData oldWidget) => feed != oldWidget.feed || appearance != oldWidget.appearance;
}

/// Observes only appearance. The native reader remains mounted below the same child.
class FeedAppearanceScope extends StatefulWidget {
  final FeedIdentity feed;
  final Widget child;
  final FeedAppearanceStore? store;
  final bool publishAction;
  final bool mixed;
  final String? label;
  const FeedAppearanceScope({
    super.key,
    required this.feed,
    required this.child,
    this.store,
    this.publishAction = true,
    this.mixed = false,
    this.label,
  });
  static FeedIdentity? feedOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_AppearanceData>()?.feed;
  static FeedAppearance of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_AppearanceData>()?.appearance ?? const FeedAppearance();
  @override
  State<FeedAppearanceScope> createState() => _FeedAppearanceScopeState();
}

class _FeedAppearanceScopeState extends State<FeedAppearanceScope> {
  final _owner = Object();
  FeedAppearanceStore? _store;
  PluginHomeDockScope? _dock;
  bool _queued = false;

  void _remove(PluginHomeDockScope? dock) {
    if (dock != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) => dock.store.remove(dock.source, 'appearance', _owner));
    }
  }

  void _publish() {
    if (_queued) return;
    _queued = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _queued = false;
      if (!mounted) return;
      final dock = _dock;
      final store = _store;
      if (store == null || !feedAppearanceCapable(widget.feed.source, mixed: widget.mixed)) {
        if (dock != null) dock.store.remove(dock.source, 'appearance', _owner);
        return;
      }
      final label = widget.label ?? feedAppearanceLabel(context, widget.feed, store);
      store.rememberLabel(widget.feed, label);
      if (!widget.publishAction) {
        if (dock != null) dock.store.remove(dock.source, 'appearance', _owner);
        return;
      }
      dock?.store.publish(
        dock.source,
        'appearance',
        _owner,
        PluginDockContent(
          actions: [FeedAppearanceAction(feed: widget.feed, store: store, label: label)],
        ),
      );
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final prefs = context.dependOnInheritedWidgetOfExactType<PrefService>()?.service;
    _store = widget.store ?? (prefs == null ? null : FeedAppearanceStore.forPrefs(prefs));
    final next = PluginHomeDockScope.maybeOf(context);
    if (_dock?.source != next?.source || _dock?.store != next?.store) {
      _remove(_dock);
    }
    _dock = next;
    _publish();
  }

  @override
  void didUpdateWidget(FeedAppearanceScope oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.store != widget.store) {
      final prefs = context.dependOnInheritedWidgetOfExactType<PrefService>()?.service;
      _store = widget.store ?? (prefs == null ? null : FeedAppearanceStore.forPrefs(prefs));
    }
    _publish();
  }

  @override
  Widget build(BuildContext context) {
    final store = _store;
    if (store == null) {
      return _AppearanceData(feed: widget.feed, appearance: const FeedAppearance(), child: widget.child);
    }
    return ScopedBuilder<FeedAppearanceStore, Map<FeedIdentity, FeedAppearance>>(
      store: store,
      onState: (_, _) =>
          _AppearanceData(feed: widget.feed, appearance: store.appearance(widget.feed), child: widget.child),
    );
  }

  @override
  void dispose() {
    _remove(_dock);
    super.dispose();
  }
}

bool feedCountsVisible(BuildContext context, {bool? sourceDefault}) {
  final prefs = context.dependOnInheritedWidgetOfExactType<PrefService>()?.service;
  final normal = sourceDefault ?? !(prefs?.get(optionZenMode) == true || prefs?.get(optionCalmMode) == true);
  return FeedAppearanceScope.of(context).counts ?? normal;
}

bool feedMediaVisible(BuildContext context) => FeedAppearanceScope.of(context).media ?? true;
int? feedTextLines(BuildContext context, {int? normal}) => switch (FeedAppearanceScope.of(context).preset) {
  FeedPreset.compact => 3,
  FeedPreset.gallery => normal == null ? 6 : math.min(normal, 3),
  FeedPreset.reading => null,
  null => normal,
};
TextStyle feedBodyStyle(BuildContext context, TextStyle style) =>
    FeedAppearanceScope.of(context).preset == FeedPreset.reading ? style.copyWith(height: 1.55) : style;
EdgeInsets feedCardPadding(BuildContext context) =>
    EdgeInsets.fromLTRB(16, FeedAppearanceScope.of(context).preset == FeedPreset.compact ? 8 : 12, 16, 4);
double feedMediaAspect(BuildContext context, double normal) => switch (FeedAppearanceScope.of(context).preset) {
  FeedPreset.compact => math.max(normal, 2.4),
  FeedPreset.gallery => math.min(normal, 1),
  _ => normal,
};

enum FeedAppearancePartKind { media, linkPreviews }

/// Do not fetch initially hidden attachments; park already mounted native state.
class FeedAppearancePart extends StatefulWidget {
  final FeedAppearancePartKind? kind;
  final Widget child;
  final Widget? hiddenChild;
  const FeedAppearancePart({super.key, required this.kind, required this.child, this.hiddenChild});
  @override
  State<FeedAppearancePart> createState() => _FeedAppearancePartState();
}

class _FeedAppearancePartState extends State<FeedAppearancePart> {
  bool _shown = false;
  @override
  Widget build(BuildContext context) {
    if (widget.kind == null) return widget.child;
    final appearance = FeedAppearanceScope.of(context);
    final visible = (widget.kind == FeedAppearancePartKind.media ? appearance.media : appearance.linkPreviews) ?? true;
    _shown |= visible;
    if (!_shown) return widget.hiddenChild ?? const SizedBox.shrink();
    final parked = TickerMode(
      enabled: visible,
      child: Offstage(offstage: !visible, child: widget.child),
    );
    if (widget.hiddenChild == null) return parked;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [parked, if (!visible) widget.hiddenChild!],
    );
  }
}

/// Keeps existing native subtrees and their keys below a stable appearance scope.
extension FeedAppearanceWidgets on Widget {
  Widget withFeedAppearance({
    required FeedIdentity feed,
    String? label,
    bool publishAction = true,
    bool mixed = false,
  }) => FeedAppearanceScope(feed: feed, label: label, publishAction: publishAction, mixed: mixed, child: this);

  Widget withFeedAppearancePart({required FeedAppearancePartKind? kind, Widget? hiddenChild}) =>
      FeedAppearancePart(kind: kind, hiddenChild: hiddenChild, child: this);
}
