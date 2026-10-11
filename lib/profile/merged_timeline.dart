import 'package:xta/client/client.dart';

/// One page of a newest-first timeline and the cursor after it.
typedef TimelinePage = ({List<TweetChain> chains, String? next});

typedef TimelineFetch = Future<TimelinePage> Function(String? cursor);

/// What can be shown from two newest-first buffers, and what must wait.
typedef MergeStep = ({List<TweetChain> ready, List<TweetChain> restA, List<TweetChain> restB});

DateTime? _timeOf(TweetChain chain) => chain.tweets.isEmpty ? null : chain.tweets.first.createdAt;

DateTime? _oldest(List<TweetChain> chains) =>
    chains.map(_timeOf).whereType<DateTime>().fold<DateTime?>(null, (o, t) => o == null || t.isBefore(o) ? t : o);

/// Splits buffered chains of two timelines into those that can be shown now,
/// newest first, and those to keep. A timeline that is still [openA]/[openB]
/// can only have older chains to come than its oldest buffered one, so a chain
/// is ready once it is no older than that for every open timeline. Undated
/// chains are always ready.
MergeStep mergeReady(List<TweetChain> a, bool openA, List<TweetChain> b, bool openB) {
  final bounds = [if (openA) _oldest(a), if (openB) _oldest(b)].whereType<DateTime>();
  final bound = bounds.fold<DateTime?>(null, (m, t) => m == null || t.isAfter(m) ? t : m);
  bool ready(TweetChain c) {
    final time = _timeOf(c);
    return time == null || bound == null || !time.isBefore(bound);
  }

  final shown = [...a.where(ready), ...b.where(ready)]
    ..sort((x, y) {
      final tx = _timeOf(x);
      final ty = _timeOf(y);
      return tx == null || ty == null ? 0 : ty.compareTo(tx);
    });
  return (ready: shown, restA: a.where((c) => !ready(c)).toList(), restB: b.where((c) => !ready(c)).toList());
}

class _Side {
  final TimelineFetch fetch;
  String? cursor;
  bool started = false;
  bool finished = false;
  List<TweetChain> buffer = const [];

  _Side(this.fetch);

  bool get open => !finished;

  /// Fetches until something is buffered or the timeline ends, so an open
  /// timeline always has an oldest chain to bound the merge by. Hands back the
  /// pinned chains met on the way: they go first rather than by time.
  Future<List<TweetChain>> fill() async {
    final pinned = <TweetChain>[];
    while (!finished && buffer.isEmpty) {
      final page = await fetch(started ? cursor : null);
      started = true;
      finished = page.chains.isEmpty || page.next == null || page.next == cursor;
      cursor = page.next;
      buffer = page.chains.where((c) => !c.isPinned).toList();
      pinned.addAll(page.chains.where((c) => c.isPinned));
    }
    return pinned;
  }
}

/// Two newest-first paged timelines read as one, such as a profile's posts and
/// its reposts, which X now serves separately. A later page never brings
/// back something newer than what was already shown, and a post found in both
/// appears once.
class MergedTimeline {
  final _Side _a;
  final _Side _b;
  final _seen = <String>{};
  int _pages = 0;

  MergedTimeline(TimelineFetch a, TimelineFetch b) : _a = _Side(a), _b = _Side(b);

  bool get hasMore => _a.open || _b.open || _a.buffer.isNotEmpty || _b.buffer.isNotEmpty;

  /// A distinct cursor per page, for pagers that compare them.
  String? get nextCursor => hasMore ? 'merged-$_pages' : null;

  Future<List<TweetChain>> next() async {
    _pages++;
    final pinned = <TweetChain>[];
    var shown = <TweetChain>[];
    while (shown.isEmpty && hasMore) {
      final filled = await Future.wait([_a.fill(), _b.fill()]);
      pinned.addAll(filled.expand((p) => p));
      final step = mergeReady(_a.buffer, _a.open, _b.buffer, _b.open);
      _a.buffer = step.restA;
      _b.buffer = step.restB;
      shown = step.ready.where((c) => _seen.add(c.id)).toList();
      if (pinned.isNotEmpty) break;
    }
    return [...pinned.where((c) => _seen.add(c.id)), ...shown];
  }
}
