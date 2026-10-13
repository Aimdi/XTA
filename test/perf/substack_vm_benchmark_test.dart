import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:html/parser.dart' as html_parser;
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/offline/offline_article.dart';
import 'package:xta/offline/offline_store.dart';
import 'package:xta/plugins/substack/substack_article_navigation.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_comments_store.dart';
import 'package:xta/plugins/substack/substack_html.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_store.dart';

import '../fixtures/substack_synthetic.dart';

/// Host-VM cost of the Substack code paths the device baseline times (S2, S3,
/// S4, S7 in docs/substack/audit.md). `flutter test` runs JIT with assertions
/// on, so these rank work and count bytes; they are not phone timings.
///
///   fvm flutter test test/perf/substack_vm_benchmark_test.dart --dart-define=RUN_BENCH=true
const _skip = bool.fromEnvironment('RUN_BENCH') ? false : 'opt-in: --dart-define=RUN_BENCH=true';

Map<String, double> _stats(List<double> samples) {
  final sorted = samples.toList()..sort();
  double at(double q) => sorted[(q * (sorted.length - 1)).round()];
  return {'median_ms': at(0.5), 'p95_ms': at(0.95)};
}

Future<Map<String, double>> _time(FutureOr<void> Function() body, {int warmup = 3, int trials = 15}) async {
  for (var i = 0; i < warmup; i++) {
    await body();
  }
  final samples = <double>[];
  for (var i = 0; i < trials; i++) {
    final watch = Stopwatch()..start();
    await body();
    samples.add(watch.elapsedMicroseconds / 1000);
  }
  return _stats(samples);
}

void _report(String bench, Map<String, Object?> values) {
  // ignore: avoid_print
  print(jsonEncode({'bench': bench, ...values}));
}

SubstackPost _decodePost(String payload) => SubstackPost.fromJson(
  Map<String, dynamic>.from(jsonDecode(payload) as Map),
  publicationBaseUrl: 'https://xtaperf.substack.com',
  publicationName: 'XTA Perf',
);

String _page(SubstackPost post, String body) => wrapSubstackHtml(
  title: post.title,
  body: body,
  background: '#000000',
  foreground: '#ffffff',
  muted: '#999999',
  link: '#1d9bf0',
  isDark: true,
);

/// What `SubstackReaderScreen._showContent` does to one body before the web
/// view gets it: the navigation index, the page wrapper and the speech text.
void _openPipeline(SubstackPost post) {
  final html = post.bodyHtml!;
  final document = SubstackArticleDocument.parse(html);
  _page(post, document.body);
  buildSubstackSpeakText(title: post.title, bodyHtml: html);
}

class _InstantClient extends SubstackClient {
  @override
  Future<List<SubstackPost>> fetchPosts(SubstackPublication publication, {int limit = 12, int offset = 0}) async => [
    for (var i = 0; i < limit; i++)
      SubstackPost(
        id: '${publication.id}-$i',
        title: 'Post $i',
        slug: 'post-$i',
        postDate: DateTime.utc(2026, 9, 1 + i, publication.id.length).toIso8601String(),
        publicationBaseUrl: publication.baseUrl,
        publicationName: publication.name,
      ),
  ];
}

class _Publications extends SubstackPublicationsStore {
  _Publications(List<SubstackPublication> pubs) : super(PrefServiceCache(cache: {})) {
    update(pubs);
  }
}

void main() {
  test('S2: one long-post open', () async {
    final body = syntheticSubstackBody();
    final payload = jsonEncode(syntheticSubstackPostJson(body: body));
    final post = _decodePost(payload);
    _report('s2_open', {
      'words': html_parser.parseFragment(body).text!.split(RegExp(r'\s+')).length,
      'images': RegExp('<img ').allMatches(body).length,
      'body_bytes': utf8.encode(body).length,
      'single_parse': await _time(() => html_parser.parseFragment(body)),
      'decode_post_json': await _time(() => _decodePost(payload)),
      'navigation_index': await _time(() => SubstackArticleDocument.parse(body)),
      'page_wrapper': await _time(() => _page(post, body)),
      'speech_text': await _time(() => buildSubstackSpeakText(title: post.title, bodyHtml: body)),
      'open_pipeline_total': await _time(() => _openPipeline(post)),
    });
  }, skip: _skip);

  test('S3: comment discussions', () async {
    for (final count in [300, 1000]) {
      final payload = jsonEncode(syntheticSubstackComments(count));
      final comments = flattenSubstackComments(jsonDecode(payload));
      _report('s3_comments_$count', {
        'payload_bytes_synthetic': utf8.encode(payload).length,
        'decode_json': await _time(() => jsonDecode(payload)),
        'flatten': await _time(() => flattenSubstackComments(jsonDecode(payload))),
        'rows_original': await _time(() => substackCommentRows(comments)),
        'rows_newest': await _time(() => substackCommentRows(comments, order: SubstackCommentOrder.newest)),
        'rows_per_search_keystroke': await _time(() => substackCommentRows(comments, query: 'river')),
      });
    }
  }, skip: _skip);

  test('S4: a home refresh over 30 publications with an instant network', () async {
    final pubs = [
      for (var i = 0; i < 30; i++)
        SubstackPublication(subdomain: 'pub$i', baseUrl: 'https://pub$i.substack.com', name: 'Pub $i'),
    ];
    var publishes = 0;
    final timing = await _time(
      () async {
        final store = SubstackFeedStore(_InstantClient(), _Publications(pubs));
        final dispose = store.observer(onState: (_) => publishes++);
        await store.refresh(force: true);
        await dispose();
        await store.destroy();
      },
      warmup: 2,
      trials: 10,
    );
    _report('s4_refresh_cpu', {'publications': 30, 'state_updates_per_refresh': publishes ~/ 12, ...timing});
  }, skip: _skip);

  test('S7: rendering a retained long post with its images', () async {
    final root = await Directory.systemTemp.createTemp('xta_offline_bench');
    addTearDown(() => root.delete(recursive: true));
    const imageBytes = 250 * 1024;
    final store = OfflineStore(
      directory: () async => root,
      client: MockClient((_) async => http.Response.bytes([255, 216, 255, ...List.filled(imageBytes - 3, 0)], 200)),
    );
    addTearDown(store.destroy);
    final post = _decodePost(jsonEncode(syntheticSubstackPostJson(body: syntheticSubstackBody())));
    final article = OfflineArticle.substack(post);
    await store.keepArticle(article);
    final page = _page(post, post.bodyHtml!);
    final rendered = await store.renderArticle(article.id, page);
    _report('s7_offline_render', {
      'retained_images': store.state.entry(article.id)?.files.length,
      'assumed_image_bytes': imageBytes,
      'page_bytes_online': utf8.encode(page).length,
      'page_bytes_offline': utf8.encode(rendered).length,
      'render': await _time(() => store.renderArticle(article.id, page), warmup: 1, trials: 5),
    });
  }, skip: _skip);
}
