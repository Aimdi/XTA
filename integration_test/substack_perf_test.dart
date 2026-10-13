import 'dart:async';
import 'dart:convert';

import 'package:ffcache/ffcache.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:integration_test/integration_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/podcast_store.dart';
import 'package:xta/plugins/substack/substack_article_cache.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_comments_screen.dart';
import 'package:xta/plugins/substack/substack_discussion_text.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_reader_screen.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/speech/speech_store.dart';
import 'package:xta/ui/x_look_theme.dart';

import '../test/fixtures/substack_synthetic.dart';

/// S2 (long post) and S3 (300+ comments) of docs/substack/audit.md, on a phone,
/// in profile mode — a debug build's frame times say nothing:
///
///   fvm flutter drive --profile \
///     --driver=test_driver/substack_perf_test.dart \
///     --target=integration_test/substack_perf_test.dart \
///     --dart-define=XTA_COMMIT=$(git rev-parse --short HEAD) --dart-define=XTA_NETWORK=wifi \
///     --dart-define=SUBSTACK_PERF_POST=https://example.substack.com/p/a-long-read \
///     --dart-define=SUBSTACK_PERF_COMMENTS=https://example.substack.com/p/a-busy-discussion
///
/// SUBSTACK_PERF_POST names a post of 8,000+ words with 20+ images, and
/// SUBSTACK_PERF_COMMENTS one with 300+ comments.
///
/// Without the two URLs both scenarios read a synthetic post and discussion
/// from memory: that checks the harness and the CPU side, not the network or
/// image decoding. The driver writes build/substack_perf_summary.json.
const _postUrl = String.fromEnvironment('SUBSTACK_PERF_POST');
const _commentsUrl = String.fromEnvironment('SUBSTACK_PERF_COMMENTS');
const _trials = 3;

const _firstParagraphJs =
    "(function(){var p=document.querySelector('.content p');if(!p)return 0;"
    'var r=p.getBoundingClientRect();return r.height>0&&r.top<window.innerHeight?1:0;})()';
const _scrollJs =
    'JSON.stringify({y:window.scrollY,max:Math.max(0,document.documentElement.scrollHeight-window.innerHeight)})';
const _rafStartJs =
    '(function(){window.__xtaGaps=[];var last=0;function tick(t){if(last)window.__xtaGaps.push(Math.round((t-last)*10)/10);'
    'last=t;window.__xtaRaf=requestAnimationFrame(tick);}window.__xtaRaf=requestAnimationFrame(tick);return 0;})()';
const _rafStopJs = '(function(){cancelAnimationFrame(window.__xtaRaf);return JSON.stringify(window.__xtaGaps||[]);})()';

class _Source {
  final SubstackClient client;
  final SubstackPost longPost;
  final SubstackPost discussion;
  final String mode;

  const _Source(this.client, this.longPost, this.discussion, this.mode);
}

SubstackPost _stub(String url) {
  final ref = resolveSubstackPostRef(url)!;
  final origin = ref.base.origin;
  return SubstackPost(
    id: ref.slug,
    title: ref.slug,
    slug: ref.slug,
    publicationBaseUrl: origin,
    publicationName: subdomainOf(ref.base),
    canonicalUrl: '$origin/p/${ref.slug}',
  );
}

_Source _syntheticSource() {
  final post = syntheticSubstackPostJson(body: syntheticSubstackBody());
  final comments = jsonEncode(syntheticSubstackComments(340));
  final client = SubstackClient(
    httpClient: MockClient((request) async {
      final body = request.url.path.endsWith('/comments') ? comments : jsonEncode(post);
      return http.Response(body, 200, request: request, headers: {'content-type': 'application/json; charset=utf-8'});
    }),
  );
  final stub = _stub(post['canonical_url']! as String);
  final discussion = SubstackPost(
    id: '${post['id']}',
    title: '${post['title']}',
    slug: stub.slug,
    publicationBaseUrl: stub.publicationBaseUrl,
    publicationName: stub.publicationName,
    canonicalUrl: stub.canonicalUrl,
  );
  return _Source(client, stub, discussion, 'synthetic');
}

Future<_Source> _source() async {
  if (_postUrl.isEmpty || _commentsUrl.isEmpty) return _syntheticSource();
  final client = SubstackClient();
  final discussion = _stub(_commentsUrl);
  return _Source(client, _stub(_postUrl), await client.fetchPost(discussion.publication, discussion.slug), 'live');
}

class _Harness {
  final SubstackClient client;
  final navigator = GlobalKey<NavigatorState>();
  final prefs = PrefServiceCache(defaults: {optionPluginSubstackEnabled: true, optionFeedReadingPosition: false});
  late final read = SubstackReadStore(prefs);
  late final likes = SubstackLikesStore(prefs);
  late final saved = SubstackSavedStore(prefs);
  late final publications = SubstackPublicationsStore(prefs);
  late final podcast = PodcastStore(prefs: prefs, observeLifecycle: false);
  late final speech = SpeechStore();

  _Harness(this.client);

  Widget app() => PrefService(
    service: prefs,
    child: MultiProvider(
      providers: [
        Provider<SubstackClient>.value(value: client),
        Provider<SubstackPublicationsStore>.value(value: publications),
        Provider<SubstackReadStore>.value(value: read),
        Provider<SubstackLikesStore>.value(value: likes),
        Provider<SubstackSavedStore>.value(value: saved),
        Provider<PodcastStore>.value(value: podcast),
        Provider<SpeechStore>.value(value: speech),
      ],
      child: MaterialApp(
        navigatorKey: navigator,
        theme: xLookLightTheme(null),
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: const SizedBox.expand(),
      ),
    ),
  );

  Future<int> open(WidgetTester tester, Widget screen, Future<bool> Function() ready) async {
    final watch = Stopwatch()..start();
    unawaited(navigator.currentState!.push(MaterialPageRoute<void>(builder: (_) => screen)));
    while (!await ready()) {
      if (watch.elapsed > const Duration(seconds: 60)) throw TimeoutException('Nothing readable after a minute');
      await tester.pump(const Duration(milliseconds: 16));
    }
    return watch.elapsedMilliseconds;
  }

  Future<void> close(WidgetTester tester) async {
    navigator.currentState!.pop();
    await tester.pumpAndSettle();
    await Future<void>.delayed(const Duration(milliseconds: 500));
  }
}

Object? _decodeJs(Object raw) {
  if (raw is! String) return raw;
  try {
    final once = jsonDecode(raw);
    return once is String ? jsonDecode(once) : once;
  } on FormatException {
    return raw;
  }
}

Future<Object?> _js(WidgetTester tester, String script) async {
  final page = tester.widget<WebViewWidget>(find.byType(WebViewWidget)).platform.params.controller;
  return _decodeJs(await page.runJavaScriptReturningResult(script));
}

Future<bool> _firstParagraphVisible(WidgetTester tester) async {
  if (find.byType(WebViewWidget).evaluate().isEmpty) return false;
  try {
    return '${await _js(tester, _firstParagraphJs)}' == '1';
  } on Exception {
    return false;
  }
}

Future<({num y, num max})> _scroll(WidgetTester tester) async {
  final state = await _js(tester, _scrollJs);
  return state is Map ? (y: state['y'] as num, max: state['max'] as num) : (y: 0, max: 0);
}

/// A synthesized touch fling reaches the page on some platform-view modes and
/// not on others; the report says which way the page was moved.
Future<String> _flingArticle(WidgetTester tester, {required bool touch}) async {
  for (var i = 0; i < 400; i++) {
    final at = await _scroll(tester);
    if (at.y >= at.max - 2) break;
    if (touch) {
      await tester.fling(find.byType(WebViewWidget), const Offset(0, -900), 3000);
    } else {
      await _js(tester, "window.scrollBy({top:window.innerHeight*1.5,behavior:'smooth'});0");
    }
    await Future<void>.delayed(const Duration(milliseconds: 700));
  }
  return touch ? 'touch-fling' : 'js-smooth-scroll';
}

Future<bool> _touchMovesPage(WidgetTester tester) async {
  final before = await _scroll(tester);
  await tester.fling(find.byType(WebViewWidget), const Offset(0, -900), 3000);
  await Future<void>.delayed(const Duration(milliseconds: 700));
  final moved = (await _scroll(tester)).y > before.y;
  await _js(tester, 'window.scrollTo(0,0);0');
  return moved;
}

Future<int> _flingList(WidgetTester tester) async {
  final list = find.byType(ListView);
  var flings = 0;
  while (flings < 600) {
    final scrollable = find.descendant(of: list, matching: find.byType(Scrollable)).first;
    if (tester.state<ScrollableState>(scrollable).position.extentAfter <= 0) break;
    await tester.fling(list, const Offset(0, -900), 3000);
    await tester.pumpAndSettle();
    flings++;
  }
  return flings;
}

Map<String, Object?> _environment(WidgetTester tester, String mode) => {
  'commit': const String.fromEnvironment('XTA_COMMIT'),
  'network': const String.fromEnvironment('XTA_NETWORK'),
  'data': mode,
  'refresh_rate_hz': tester.view.display.refreshRate,
  'device_pixel_ratio': tester.view.devicePixelRatio,
  'logical_size': '${tester.view.physicalSize / tester.view.devicePixelRatio}',
};

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  binding.framePolicy = LiveTestWidgetsFlutterBindingFramePolicy.fullyLive;

  testWidgets('S2: a long post opens to its first paragraph and flings to the end', (tester) async {
    if (WebViewPlatform.instance == null) return markTestSkipped('S2 needs a platform web view');
    final source = await _source();
    final harness = _Harness(source.client);
    await tester.pumpWidget(harness.app());
    final post = source.longPost;
    Future<bool> evict() =>
        FFCache(name: 'substack_articles').remove(SubstackArticleCache.keyFor(post.publication, post.slug));
    Future<int> open() => harness.open(tester, SubstackReaderScreen(post: post), () => _firstParagraphVisible(tester));

    await evict();
    await open();
    final touch = await _touchMovesPage(tester);
    await harness.close(tester);

    final uncached = <int>[];
    for (var i = 0; i < _trials; i++) {
      await evict();
      uncached.add(await open());
      await harness.close(tester);
    }

    final cached = <int>[];
    final gaps = <Object?>[];
    for (var i = 0; i < _trials; i++) {
      cached.add(await open());
      await binding.watchPerformance(() async {
        await _js(tester, _rafStartJs);
        await _flingArticle(tester, touch: touch);
        gaps.add(await _js(tester, _rafStopJs));
      }, reportKey: 's2_fling_trial_${i + 1}');
      await harness.close(tester);
    }

    binding.reportData ??= {};
    binding.reportData!['environment'] = _environment(tester, source.mode);
    binding.reportData!['s2'] = {
      'post': source.mode == 'live' ? _postUrl : post.canonicalUrl,
      'first_paragraph_uncached_ms': uncached,
      'first_paragraph_cached_ms': cached,
      'scroll_method': touch ? 'touch-fling' : 'js-smooth-scroll',
      'webview_raf_gaps_ms': gaps,
    };
  });

  testWidgets('S3: a 300+ comment discussion shows its first comments and scrolls to the end', (tester) async {
    final source = await _source();
    final harness = _Harness(source.client);
    await tester.pumpWidget(harness.app());
    final post = source.discussion;
    final comments = await source.client.fetchComments(post.publication, post.id);
    Future<int> open() => harness.open(
      tester,
      SubstackCommentsScreen(post: post),
      () async => find.byType(SubstackDiscussionText).evaluate().isNotEmpty,
    );

    await open();
    await harness.close(tester);

    final first = <int>[];
    final flings = <int>[];
    for (var i = 0; i < _trials; i++) {
      first.add(await open());
      await binding.watchPerformance(
        () async => flings.add(await _flingList(tester)),
        reportKey: 's3_scroll_trial_${i + 1}',
      );
      await harness.close(tester);
    }

    binding.reportData ??= {};
    binding.reportData!['environment'] ??= _environment(tester, source.mode);
    binding.reportData!['s3'] = {
      'post': source.mode == 'live' ? _commentsUrl : post.canonicalUrl,
      'comments': comments.length,
      'first_comment_ms': first,
      'flings_to_end': flings,
    };
  });
}
