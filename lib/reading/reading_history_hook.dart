import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:pref/pref.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/reading/reading_history_entry.dart';
import 'package:xta/reading/reading_history_store.dart';
import 'package:xta/utils/read_visibility.dart';

/// How long a timeline card must stay on screen to count as read, rather than scrolled past.
const readingHistoryCardDwell = Duration(milliseconds: 1500);

/// Opened posts, articles and profiles fill the screen; a shorter look counts.
const readingHistoryScreenDwell = Duration(milliseconds: 600);

bool _mostlyVisible(VisibilityInfo info) =>
    info.visibleFraction >= 0.5 || (info.size.height > 0 && info.visibleBounds.height >= 320);

/// Lets tests and previews substitute where history is kept.
class ReadingHistoryScope extends InheritedWidget {
  final ReadingHistoryStore store;
  const ReadingHistoryScope({super.key, required this.store, required super.child});

  @override
  bool updateShouldNotify(ReadingHistoryScope oldWidget) => store != oldWidget.store;
}

/// The shared history, found without depending on every preference change.
ReadingHistoryStore? readingHistoryOf(BuildContext context) {
  final scoped = context.getInheritedWidgetOfExactType<ReadingHistoryScope>()?.store;
  if (scoped != null) return scoped;
  final prefs = context.findAncestorWidgetOfExactType<PrefService>()?.service;
  return prefs == null ? null : ReadingHistoryStore.forPrefs(prefs);
}

/// Records [entry] once [child] has been mostly visible, on the current route and with the app in front,
/// for [dwell]. Place it inside content warnings and filters so hidden content is never recorded.
class ReadingHistoryHook extends StatefulWidget {
  final ReadingHistoryEntry Function() entry;
  final Widget child;
  final Duration dwell;
  const ReadingHistoryHook({super.key, required this.entry, required this.child, this.dwell = readingHistoryCardDwell});

  @override
  State<ReadingHistoryHook> createState() => _ReadingHistoryHookState();
}

class _ReadingHistoryHookState extends State<ReadingHistoryHook> {
  ReadingHistoryStore? _store;
  Timer? _dwell;
  bool _recorded = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _store = readingHistoryOf(context);
  }

  void _visible() {
    final store = _store;
    if (_recorded || store == null || !store.state.enabled || (_dwell?.isActive ?? false)) return;
    final epoch = store.epoch;
    _dwell = Timer(widget.dwell, () {
      if (!mounted || _recorded) return;
      _recorded = true;
      store.record(widget.entry(), epoch: epoch);
    });
  }

  void _hidden() => _dwell?.cancel();

  @override
  void dispose() {
    _dwell?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_store == null) return widget.child;
    return ReadVisibility(onVisible: _visible, onHidden: _hidden, visibleWhen: _mostlyVisible, child: widget.child);
  }
}

extension ReadingHistoryWidgets on Widget {
  Widget recordedAs(ReadingHistoryEntry Function() entry, {Duration dwell = readingHistoryCardDwell}) =>
      ReadingHistoryHook(entry: entry, dwell: dwell, child: this);
}
