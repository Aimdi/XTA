import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/rss/rss_html.dart';
import 'package:xta/plugins/substack/substack_html.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_reader_store.dart';
import 'package:xta/reading/article_reading_bridge.dart';
import 'package:xta/reading/article_reading_store.dart';

class ReaderClock implements Stopwatch {
  Duration time = Duration.zero;
  bool running = false;
  @override bool get isRunning => running;
  @override Duration get elapsed => time;
  @override int get elapsedMilliseconds => time.inMilliseconds;
  @override int get elapsedMicroseconds => time.inMicroseconds;
  @override int get elapsedTicks => time.inMicroseconds;
  @override int get frequency => 1000000;
  @override void start() => running = true;
  @override void stop() => running = false;
  @override void reset() => time = Duration.zero;
}

String progress({double fraction = 0.42, bool scroll = true, bool end = false}) => jsonEncode({
  'fraction': fraction, 'paragraph': 8, 'leading': -32.5,
  'userScrolled': scroll, 'interacted': scroll, 'atEnd': end,
});

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('Substack deep links and fetched article IDs share one reading position', () {
    const stub = SubstackPost(id: 'a-long-read', slug: 'a-long-read', title: 'A long read',
      publicationBaseUrl: 'https://studio.substack.com', publicationName: 'Studio');
    const full = SubstackPost(id: '128492', slug: 'a-long-read', title: 'A long read',
      publicationBaseUrl: 'https://studio.substack.com', publicationName: 'Studio');
    expect(substackArticleReadingId(stub), substackArticleReadingId(full));
  });

  test('opening is recorded separately from finishing; article and appearance survive restart', () async {
    final prefs = PrefServiceCache();
    var completed = 0;
    final first = ArticleReadingStore(prefs: prefs, articleId: 'rss:feed:item', onCompleted: () async { completed++; });
    expect(first.state.point.completed, isFalse);
    first.appearance(fontSize: 24, lineHeight: 2);
    first.receiveProgress(progress());
    await first.destroy();
    expect(completed, 0);
    final reopened = ArticleReadingStore(prefs: prefs, articleId: 'rss:feed:item', onCompleted: () async {});
    expect(reopened.state.fontSize, 24);
    expect(reopened.state.lineHeight, 2);
    expect(reopened.state.point.fraction, 0.42);
    expect(reopened.state.point.paragraph, 8);
    expect(reopened.state.point.leading, -32.5);
    expect(reopened.state.resumed, isTrue);
    await reopened.destroy();
    final otherNetwork = ArticleReadingStore(prefs: prefs, articleId: 'substack:pub:item', onCompleted: () async {});
    expect(otherNetwork.state.fontSize, 24);
    expect(otherNetwork.state.point.fraction, 0);
    await otherNetwork.destroy();
  });

  test('only an active reader scrolling to the end after dwell time completes automatically', () async {
    var completed = 0;
    final clock = ReaderClock();
    final store = ArticleReadingStore(prefs: PrefServiceCache(), articleId: 'a', clock: clock,
      onCompleted: () async { completed++; });
    store.setActive(true);
    store.receiveProgress(progress(fraction: 1, end: true));
    expect(completed, 0);
    clock.time = const Duration(seconds: 14);
    store.receiveProgress(progress(fraction: 1, scroll: false, end: true));
    expect(completed, 0, reason: 'restoring at the end is not reading');
    store.setActive(false);
    store.receiveProgress(progress(fraction: 1, end: true));
    expect(completed, 0);
    store.setActive(true);
    store.receiveProgress(progress(fraction: 1, end: true));
    await store.flush();
    expect(completed, 1);
    expect(store.state.point.completed, isTrue);
    await store.complete();
    expect(completed, 1);
    await store.destroy();
  });

  test('previews require explicit completion and failed completion can be retried', () async {
    final clock = ReaderClock()..time = const Duration(minutes: 1);
    var calls = 0;
    final store = ArticleReadingStore(prefs: PrefServiceCache(), articleId: 'preview', clock: clock,
      allowAutomaticCompletion: false, onCompleted: () async {
        calls++;
        if (calls == 1) throw StateError('disk unavailable');
      });
    store.setActive(true);
    store.receiveProgress(progress(fraction: 1, end: true));
    expect(calls, 0);
    await store.complete();
    expect(store.state.point.completed, isFalse);
    await store.complete();
    expect(calls, 2);
    expect(store.state.point.completed, isTrue);
    await store.destroy();
  });

  test('nested readers merge their journal writes and the newest 300 entries are retained', () async {
    final prefs = PrefServiceCache(defaults: {articleReadingPreference: jsonEncode({
      for (var i = 0; i < 305; i++) 'old-$i': {'updatedAt': i},
    })});
    final first = ArticleReadingStore(prefs: prefs, articleId: 'first', onCompleted: () async {});
    final second = ArticleReadingStore(prefs: prefs, articleId: 'second', onCompleted: () async {});
    first.receiveProgress(progress(fraction: 0.3));
    second.receiveProgress(progress(fraction: 0.8));
    await Future.wait([first.destroy(), second.destroy()]);
    final journal = jsonDecode(prefs.get<String>(articleReadingPreference)!) as Map;
    expect(journal.length, articleReadingLimit);
    expect(journal['first']['fraction'], 0.3);
    expect(journal['second']['fraction'], 0.8);
    expect(journal.containsKey('old-0'), isFalse);
  });

  test('malformed preferences and bridge messages are bounded and safe', () async {
    final prefs = PrefServiceCache(defaults: {
      articleReadingPreference: '{broken',
      articleAppearancePreference: '{"fontSize":9999,"lineHeight":-10}',
    });
    final store = ArticleReadingStore(prefs: prefs, articleId: 'a', onCompleted: () async {});
    expect(store.state.fontSize, 28);
    expect(store.state.lineHeight, 1.4);
    store.receiveProgress('{broken');
    store.receiveProgress(jsonEncode({'fraction': 9000, 'paragraph': -8, 'leading': 'bad'}));
    expect(store.state.point.fraction, 1);
    expect(store.state.point.paragraph, 0);
    expect(store.state.point.leading, 0);
    expect(store.state.point.completed, isFalse);
    await store.destroy();
  });

  test('reading-position opt-out keeps shared typography but does not restore or persist position', () async {
    final original = jsonEncode({'a': {'fraction': 0.5, 'paragraph': 12}});
    final prefs = PrefServiceCache(defaults: {
      optionFeedReadingPosition: false, articleReadingPreference: original,
      articleAppearancePreference: '{"fontSize":23}',
    });
    final store = ArticleReadingStore(prefs: prefs, articleId: 'a', onCompleted: () async {});
    expect(store.state.point.fraction, 0);
    expect(store.state.fontSize, 23);
    store.receiveProgress(progress());
    await store.destroy();
    expect(prefs.get<String>(articleReadingPreference), original);
  });

  test('disposed sessions ignore delayed progress callbacks', () async {
    final store = ArticleReadingStore(prefs: PrefServiceCache(), articleId: 'a', onCompleted: () async {});
    final destroying = store.destroy();
    store.receiveProgress(progress());
    expect(store.state.point.fraction, 0);
    await destroying;
  });

  test('both HTML sanitizers remove execution paths before the trusted progress bridge is added', () {
    const payload = '<p>Readable</p><a href="java&#10;script:alert(1)">link</a>'
      '<iframe srcdoc="&lt;script&gt;alert(1)&lt;/script&gt;"></iframe>'
      '<meta http-equiv="refresh" content="0;url=https://bad.invalid"><base href="https://bad.invalid">';
    for (final sanitize in [sanitizeRssBodyHtml, sanitizeSubstackBodyHtml]) {
      final document = sanitize(payload);
      expect(document, contains('Readable'));
      expect(document, isNot(contains('alert(1)')));
      expect(document, isNot(contains('srcdoc')));
      expect(document, isNot(contains('http-equiv')));
      expect(document, isNot(contains('<base')));
    }
  });

  test('bridge scales typography with accessibility and restores paragraph before proportional fallback', () {
    final script = articleReadingBridge(const ArticleReadingState(fontSize: 20,
      point: ArticleReadPoint(fraction: 0.4, paragraph: 7, leading: -18)), textScale: 1.5);
    expect(script, contains('30.0'));
    expect(script, contains('"paragraph":7'));
    expect(script, contains('p.fraction * maxScroll()'));
    expect(script, contains('observer.disconnect()'));
  });
}
