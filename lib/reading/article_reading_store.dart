import 'dart:async';
import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';

const articleReadingPreference = 'reading.articles.v1';
const articleAppearancePreference = 'reading.appearance.v1';
const articleReadingLimit = 300;

class ArticleReadPoint {
  final double fraction;
  final int paragraph;
  final double leading;
  final bool completed;
  final int updatedAt;

  const ArticleReadPoint({
    this.fraction = 0,
    this.paragraph = 0,
    this.leading = 0,
    this.completed = false,
    this.updatedAt = 0,
  });

  Map<String, Object> toJson() => {
    'fraction': fraction,
    'paragraph': paragraph,
    'leading': leading,
    'completed': completed,
    'updatedAt': updatedAt,
  };

  static ArticleReadPoint parse(Object? raw) {
    final map = raw is Map ? raw : const {};
    return ArticleReadPoint(
      fraction: _number(map['fraction'], 0).clamp(0, 1),
      paragraph: _number(map['paragraph'], 0).toInt().clamp(0, 100000),
      leading: _number(map['leading'], 0).clamp(-100000, 100000),
      completed: map['completed'] == true,
      updatedAt: _number(map['updatedAt'], 0).toInt(),
    );
  }
}

class ArticleReadingState {
  final double fontSize;
  final double lineHeight;
  final ArticleReadPoint point;
  final bool resumed;

  const ArticleReadingState({
    this.fontSize = 18,
    this.lineHeight = 1.7,
    this.point = const ArticleReadPoint(),
    this.resumed = false,
  });
}

double _number(Object? value, double fallback) => value is num && value.isFinite ? value.toDouble() : fallback;

Map<String, dynamic> _preferenceMap(BasePrefService prefs, String key) {
  try {
    if (!prefs.getKeys().contains(key)) return {};
    final raw = prefs.get(key);
    if (raw is! String || raw.length > 300000) return {};
    final decoded = jsonDecode(raw);
    return decoded is Map<String, dynamic> ? decoded : {};
  } catch (_) {
    return {};
  }
}

/// One article session. Journal writes are serialized across nested readers.
class ArticleReadingStore extends Store<ArticleReadingState> {
  final BasePrefService prefs;
  final String articleId;
  final Future<void> Function() onCompleted;
  final Stopwatch activeTime;
  static Future<void>? _writes;
  Timer? _debounce;
  Timer? _endTimer;
  bool _closed = false;
  bool _completionPending = false;
  bool allowAutomaticCompletion;
  bool get remembersPosition =>
      !prefs.getKeys().contains(optionFeedReadingPosition) || prefs.get(optionFeedReadingPosition) != false;

  ArticleReadingStore({
    required this.prefs,
    required this.articleId,
    required this.onCompleted,
    this.allowAutomaticCompletion = true,
    bool alreadyCompleted = false,
    Stopwatch? clock,
  }) : activeTime = clock ?? Stopwatch(),
       super(const ArticleReadingState()) {
    final appearance = _preferenceMap(prefs, articleAppearancePreference);
    final saved = remembersPosition ? _preferenceMap(prefs, articleReadingPreference)[articleId] : null;
    final point = ArticleReadPoint.parse(saved);
    update(
      ArticleReadingState(
        fontSize: _number(appearance['fontSize'], 18).clamp(16, 28),
        lineHeight: _number(appearance['lineHeight'], 1.7).clamp(1.4, 2.2),
        point: ArticleReadPoint(
          fraction: point.fraction,
          paragraph: point.paragraph,
          leading: point.leading,
          completed: point.completed || alreadyCompleted,
          updatedAt: DateTime.now().millisecondsSinceEpoch,
        ),
        resumed: point.fraction > 0.01 && !point.completed,
      ),
    );
    _schedule();
  }

  void setActive(bool active) {
    if (_closed) return;
    if (active) {
      activeTime.start();
    } else {
      activeTime.stop();
      _endTimer?.cancel();
      unawaited(flush());
    }
  }

  void appearance({double? fontSize, double? lineHeight}) {
    if (_closed) return;
    update(
      ArticleReadingState(
        fontSize: (fontSize ?? state.fontSize).clamp(16, 28),
        lineHeight: (lineHeight ?? state.lineHeight).clamp(1.4, 2.2),
        point: state.point,
        resumed: state.resumed,
      ),
    );
    final payload = jsonEncode({'fontSize': state.fontSize, 'lineHeight': state.lineHeight});
    unawaited(
      _write(() async {
        await prefs.set(articleAppearancePreference, payload);
      }),
    );
  }

  void receiveProgress(String message) {
    if (_closed || message.length > 1500) return;
    try {
      final raw = jsonDecode(message);
      if (raw is! Map) return;
      final point = ArticleReadPoint.parse(raw);
      update(
        ArticleReadingState(
          fontSize: state.fontSize,
          lineHeight: state.lineHeight,
          point: ArticleReadPoint(
            fraction: point.fraction,
            paragraph: point.paragraph,
            leading: point.leading,
            completed: state.point.completed,
            updatedAt: DateTime.now().millisecondsSinceEpoch,
          ),
          resumed: state.resumed && raw['interacted'] != true,
        ),
      );
      _schedule();
      _checkCompletion(raw);
    } catch (_) {
      // Malformed web content cannot affect persisted reading state.
    }
  }

  void _checkCompletion(Map<dynamic, dynamic> raw) {
    _endTimer?.cancel();
    if (!allowAutomaticCompletion || raw['userScrolled'] != true || raw['atEnd'] != true || !activeTime.isRunning)
      return;
    final remaining = const Duration(seconds: 12) - activeTime.elapsed;
    if (remaining <= Duration.zero) {
      unawaited(complete());
    } else {
      _endTimer = Timer(remaining, () {
        if (!_closed && activeTime.isRunning && allowAutomaticCompletion) {
          unawaited(complete());
        }
      });
    }
  }

  Future<void> complete() async {
    if (_closed || state.point.completed || _completionPending) return;
    _completionPending = true;
    try {
      await onCompleted();
      if (_closed) return;
      final point = state.point;
      update(
        ArticleReadingState(
          fontSize: state.fontSize,
          lineHeight: state.lineHeight,
          point: ArticleReadPoint(
            fraction: point.fraction,
            paragraph: point.paragraph,
            leading: point.leading,
            completed: true,
            updatedAt: DateTime.now().millisecondsSinceEpoch,
          ),
        ),
      );
      await flush();
    } catch (_) {
      // Leave the finish action available when the local read store cannot save.
    } finally {
      _completionPending = false;
    }
  }

  void startOver() {
    if (_closed) return;
    update(
      ArticleReadingState(
        fontSize: state.fontSize,
        lineHeight: state.lineHeight,
        point: ArticleReadPoint(completed: state.point.completed),
      ),
    );
    _schedule();
  }

  void _schedule() {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 400), flush);
  }

  Future<void> flush() {
    _debounce?.cancel();
    if (!remembersPosition) return _writes ?? Future.value();
    final point = state.point.toJson();
    return _write(() async {
      final journal = _preferenceMap(prefs, articleReadingPreference);
      journal[articleId] = point;
      final entries = journal.entries.toList()
        ..sort(
          (a, b) => ArticleReadPoint.parse(b.value).updatedAt.compareTo(ArticleReadPoint.parse(a.value).updatedAt),
        );
      await prefs.set(articleReadingPreference, jsonEncode(Map.fromEntries(entries.take(articleReadingLimit))));
    });
  }

  static Future<void> _write(Future<void> Function() task) {
    final previous = _writes;
    final next = (previous == null ? Future<void>.sync(task) : previous.then((_) => task())).catchError((Object _) {});
    _writes = next;
    return next.whenComplete(() {
      // Drop the completed future and its async zone after the queue drains.
      if (identical(_writes, next)) _writes = null;
    });
  }

  @override
  Future<void> destroy() async {
    _closed = true;
    _endTimer?.cancel();
    activeTime.stop();
    await flush();
    await super.destroy();
  }
}
