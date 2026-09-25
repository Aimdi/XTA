import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/bluesky/bluesky_facets.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_reader_store.dart';
import 'package:xta/plugins/bluesky/bluesky_reader_view.dart';
import 'package:xta/plugins/plugin_reading_view.dart';

import 'support/bluesky_reading_harness.dart';

BlueskyPost _post(String id, {Map<String, Object?> fields = const {}}) =>
    BlueskyPost.fromSnapshot({...bluePost(id).toJson(), ...fields});

List<String> _ids(Iterable<BlueskyPost> posts) => posts.map((post) => post.uri.split('/').last).toList();

const _source = 'https://public.api.bsky.app';
const _maya = BlueskyAccount(handle: 'maya.bsky.social', name: 'Maya', did: 'did:plc:maya');

void main() {
  test('content filters distinguish images, videos, cards and annotated links', () {
    final posts = [
      _post('plain'),
      _post(
        'image',
        fields: {
          'images': ['https://media.example/photo.jpg'],
        },
      ),
      _post(
        'video',
        fields: {
          'images': ['https://media.example/video.mp4'],
          'imageIsVideo': [true],
        },
      ),
      _post(
        'card',
        fields: {
          'linkCard': {'url': 'https://article.example', 'title': 'A field guide'},
        },
      ),
      _post(
        'link',
        fields: {
          'facets': [
            const BlueskyFacet(
              byteStart: 0,
              byteEnd: 4,
              kind: BlueskyFacetKind.link,
              value: 'https://article.example',
            ).toJson(),
          ],
        },
      ),
    ];
    expect(_ids(filterBlueskyReader(posts, const BlueskyReaderOptions(content: BlueskyReaderContent.images))), [
      'image',
    ]);
    expect(_ids(filterBlueskyReader(posts, const BlueskyReaderOptions(content: BlueskyReaderContent.videos))), [
      'video',
    ]);
    expect(_ids(filterBlueskyReader(posts, const BlueskyReaderOptions(content: BlueskyReaderContent.links))), [
      'card',
      'link',
    ]);
    expect(_ids(filterBlueskyReader(posts, const BlueskyReaderOptions())), ['plain', 'image', 'video', 'card', 'link']);
  });

  test('query requires every term across text, author, handle and card title', () {
    final posts = [
      _post(
        'match',
        fields: {
          'text': 'Quiet coast',
          'authorName': 'MAYA',
          'linkCard': {'url': 'https://example.org', 'title': 'Camera journal'},
        },
      ),
      _post('partial', fields: {'text': 'Camera coast', 'authorName': 'Elsewhere', 'handle': 'other.example'}),
    ];
    expect(_ids(filterBlueskyReader(posts, const BlueskyReaderOptions(query: '  MAYA  camera COAST '))), ['match']);
    expect(_ids(filterBlueskyReader(posts, const BlueskyReaderOptions(query: 'other.example'))), ['partial']);
    expect(filterBlueskyReader(posts, const BlueskyReaderOptions(query: 'missing')), isEmpty);
  });

  test('reply and repost switches compose and duplicate records render once', () {
    final original = _post('original');
    final posts = [
      original,
      _post('reply', fields: {'isReply': true}),
      _post('repost', fields: {'repostedByDid': 'did:plc:reader'}),
      original,
    ];
    expect(_ids(filterBlueskyReader(posts, const BlueskyReaderOptions(hideReplies: true))), ['original', 'repost']);
    expect(_ids(filterBlueskyReader(posts, const BlueskyReaderOptions(hideReposts: true))), ['original', 'reply']);
    expect(_ids(filterBlueskyReader(posts, const BlueskyReaderOptions(hideReplies: true, hideReposts: true))), [
      'original',
    ]);
  });

  test('ordering uses repost date, stable ties and unknown dates last without mutating the feed', () {
    final posts = [
      _post('unknown'),
      _post('older', fields: {'publishedAt': '2025-01-01T00:00:00Z'}),
      _post('tie-a', fields: {'publishedAt': '2026-01-01T00:00:00Z'}),
      _post('reposted', fields: {'publishedAt': '2024-01-01T00:00:00Z', 'repostedAt': '2026-02-01T00:00:00Z'}),
      _post('tie-b', fields: {'publishedAt': '2026-01-01T00:00:00Z'}),
    ];
    expect(_ids(filterBlueskyReader(posts, const BlueskyReaderOptions(order: BlueskyReaderOrder.newest))), [
      'reposted',
      'tie-a',
      'tie-b',
      'older',
      'unknown',
    ]);
    expect(_ids(filterBlueskyReader(posts, const BlueskyReaderOptions(order: BlueskyReaderOrder.oldest))), [
      'older',
      'tie-a',
      'tie-b',
      'reposted',
      'unknown',
    ]);
    expect(_ids(posts), ['unknown', 'older', 'tie-a', 'reposted', 'tie-b']);
  });

  test('restart restores bounded reading position and options without persisting search text', () async {
    final prefs = PrefServiceCache();
    final reader = BlueskyReaderStore(prefs, _source);
    reader.selectTab(2);
    reader.configure(
      'following',
      const BlueskyReaderOptions(
        query: 'private temporary query',
        content: BlueskyReaderContent.links,
        order: BlueskyReaderOrder.oldest,
        hideReposts: true,
      ),
    );
    final posts = [for (var index = 0; index < 200; index++) _post('$index')];
    reader.remember(PluginReadingPosition(posts: posts, anchor: posts[30].uri, leading: -32));
    await reader.destroy();
    final raw = prefs.get<String>(blueskyReaderPreference)!;
    expect(raw, isNot(contains('private temporary query')));
    expect((jsonDecode(raw) as Map)['posts'], hasLength(blueskyReadingLimit));
    final restored = BlueskyReaderStore(prefs, _source);
    addTearDown(restored.destroy);
    expect(restored.state.tab, 2);
    expect(restored.options('following').content, BlueskyReaderContent.links);
    expect(restored.options('following').order, BlueskyReaderOrder.oldest);
    expect(restored.options('following').hideReposts, isTrue);
    expect(restored.options('following').query, isEmpty);
    expect(restored.restorePosts([_maya]), hasLength(144));
    final point = restored.layoutPoint();
    expect(point.restore, isTrue);
    expect(point.point?.anchor, posts[30].uri);
    expect(point.point?.leading, -32);
    expect(restored.layoutPoint().restore, isFalse);
  });

  test('positions belong to one AppView while filter preferences survive a source change', () async {
    final prefs = PrefServiceCache();
    final reader = BlueskyReaderStore(prefs, _source);
    reader.configure('likes', const BlueskyReaderOptions(hideReplies: true));
    reader.remember(PluginReadingPosition(posts: [_post('one')], anchor: _post('one').uri));
    await reader.destroy();
    final other = BlueskyReaderStore(prefs, 'https://other.example');
    expect(other.restorePosts([_maya]), isEmpty);
    expect(other.layoutPoint().point, isNull);
    expect(other.options('likes').hideReplies, isTrue);
    other.remember(PluginReadingPosition(posts: [_post('two')], anchor: _post('two').uri));
    other.changeSource('https://third.example');
    expect(other.restorePosts([_maya]), isEmpty);
    expect(other.layoutPoint().point, isNull);
    await other.destroy();
  });

  test('reset clears session state and queued persistence before disposal', () async {
    final prefs = PrefServiceCache();
    final reader = BlueskyReaderStore(prefs, _source);
    reader.selectTab(3);
    reader.configure('following', const BlueskyReaderOptions(query: 'temporary', hideReplies: true));
    reader.remember(PluginReadingPosition(posts: [_post('one')], anchor: _post('one').uri));
    final firstWrite = reader.flush();
    await reader.reset();
    await firstWrite;
    expect(reader.state.tab, 0);
    expect(reader.options('following').filtered, isFalse);
    expect(reader.restorePosts([_maya]), isEmpty);
    expect(reader.layoutPoint().point, isNull);
    await reader.destroy();
    final restored = BlueskyReaderStore(prefs, _source);
    expect(restored.state.tab, 0);
    expect(restored.restorePosts([_maya]), isEmpty);
    expect(restored.options('following').filtered, isFalse);
    await restored.destroy();
  });

  test('an unchanged source preserves the current reading snapshot', () async {
    final reader = BlueskyReaderStore(PrefServiceCache(), _source);
    addTearDown(reader.destroy);
    reader.remember(PluginReadingPosition(posts: [_post('one')], anchor: _post('one').uri));
    reader.changeSource(_source);
    expect(_ids(reader.restorePosts([_maya])), ['one']);
  });

  test('restored posts intersect current authors and reposters by stable DID or handle', () async {
    final prefs = PrefServiceCache();
    final reader = BlueskyReaderStore(prefs, _source);
    reader.remember(
      PluginReadingPosition(
        posts: [
          _post('original'),
          _post('renamed', fields: {'handle': 'renamed.example'}),
          _post('boosted', fields: {'handle': 'other.example', 'did': 'did:plc:other', 'repostedByDid': _maya.did}),
          _post(
            'boost-handle',
            fields: {'handle': 'other.example', 'did': 'did:plc:other', 'repostedByHandle': 'MAYA.BSKY.SOCIAL'},
          ),
          _post('unfollowed', fields: {'handle': 'other.example', 'did': 'did:plc:other'}),
        ],
        anchor: _post('original').uri,
      ),
    );
    expect(_ids(reader.restorePosts([_maya])), ['original', 'renamed', 'boosted', 'boost-handle']);
    expect(reader.restorePosts([]), isEmpty);
    await reader.destroy();
  });

  test('empty identifiers never match unrelated cached posts', () async {
    final reader = BlueskyReaderStore(PrefServiceCache(), _source);
    addTearDown(reader.destroy);
    reader.remember(
      PluginReadingPosition(
        posts: [
          _post('unrelated', fields: {'did': ''}),
        ],
        anchor: _post('unrelated').uri,
      ),
    );
    expect(reader.restorePosts([const BlueskyAccount(handle: '', name: '', did: 'did:plc:elsewhere')]), isEmpty);
  });

  test('disabled position memory stores preferences but no post snapshot', () async {
    final prefs = PrefServiceCache(defaults: {optionFeedReadingPosition: false});
    final reader = BlueskyReaderStore(prefs, _source);
    reader.configure('likes', const BlueskyReaderOptions(content: BlueskyReaderContent.videos));
    reader.remember(PluginReadingPosition(posts: [_post('private')], anchor: _post('private').uri));
    expect(reader.restorePosts([_maya]), isEmpty);
    await reader.destroy();
    final payload = jsonDecode(prefs.get<String>(blueskyReaderPreference)!) as Map;
    expect(payload['posts'], isEmpty);
    final restored = BlueskyReaderStore(prefs, _source);
    expect(restored.options('likes').content, BlueskyReaderContent.videos);
    expect(restored.layoutPoint().point, isNull);
    await restored.destroy();
  });

  test('disabling position memory immediately prevents restoring an existing snapshot', () async {
    final prefs = PrefServiceCache(defaults: {optionFeedReadingPosition: true});
    final reader = BlueskyReaderStore(prefs, _source);
    reader.remember(PluginReadingPosition(posts: [_post('one')], anchor: _post('one').uri));
    await reader.destroy();
    final restored = BlueskyReaderStore(prefs, _source);
    await prefs.set(optionFeedReadingPosition, false);
    expect(restored.restorePosts([_maya]), isEmpty);
    expect(restored.layoutPoint().point, isNull);
    await restored.destroy();
  });

  test('malformed snapshot data cannot prevent opening a fresh reader', () async {
    for (final raw in [
      '{broken',
      'null',
      '[]',
      '{"version":99}',
      jsonEncode({
        'version': 1,
        'source': _source,
        'tab': 99,
        'posts': [false, {}, 17],
        'options': {
          'following': {'content': 4, 'order': [], 'hideReplies': 'true'},
        },
      }),
    ]) {
      final reader = BlueskyReaderStore(PrefServiceCache(defaults: {blueskyReaderPreference: raw}), _source);
      expect(reader.restorePosts([_maya]), isEmpty);
      expect(reader.options('following').content, BlueskyReaderContent.all);
      expect(reader.options('following').hideReplies, isFalse);
      expect(reader.state.tab, inInclusiveRange(0, 3));
      await reader.destroy();
    }
  });

  test('malformed records are skipped while valid snapshots and bounded offsets remain readable', () async {
    final raw = jsonEncode({
      'version': 1,
      'source': _source,
      'leading': -100000,
      'posts': [
        null,
        false,
        {'uri': 'at://bad', 'url': 'file:///etc/passwd'},
        _post('good').toJson(),
      ],
    });
    final reader = BlueskyReaderStore(PrefServiceCache(defaults: {blueskyReaderPreference: raw}), _source);
    addTearDown(reader.destroy);
    expect(_ids(reader.restorePosts([_maya])), ['good']);
    expect(reader.layoutPoint().point?.leading, -10000);
  });

  testWidgets('filter sheet remains operable at 320px with enlarged RTL text', (tester) async {
    tester.view.physicalSize = const Size(320, 1100);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final harness = BlueReadingHarness();
    final store = BlueskyReaderStore(harness.prefs, _source);
    final controller = ScrollController();
    await tester.pumpWidget(
      harness.app(
        Provider<BlueskyReaderStore>.value(
          value: store,
          child: Scaffold(
            body: BlueskyReaderView(posts: [_post('original')], slot: 'likes', controller: controller),
          ),
        ),
        scale: 2,
        rtl: true,
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Filters'));
    await tester.pumpAndSettle();
    expect(find.text('Filters and sorting apply to posts already loaded on this device.'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.enterText(find.byType(TextField), 'missing');
    await tester.pumpAndSettle();
    expect(store.options('likes').query, 'missing');
    final photos = find.widgetWithText(ChoiceChip, 'Photos');
    await tester.ensureVisible(photos);
    await tester.tap(photos);
    await tester.pumpAndSettle();
    expect(store.options('likes').content, BlueskyReaderContent.images);
    expect(tester.widget<ChoiceChip>(photos).selected, isTrue);
    final reposts = find.widgetWithText(SwitchListTile, 'Hide reposts');
    await tester.ensureVisible(reposts);
    await tester.tap(reposts);
    await tester.pumpAndSettle();
    expect(store.options('likes').hideReposts, isTrue);
    final reset = find.widgetWithText(TextButton, 'Reset filters').last;
    await tester.ensureVisible(reset);
    await tester.tap(reset);
    await tester.pumpAndSettle();
    expect(store.options('likes').filtered, isFalse);
    expect(tester.widget<TextField>(find.byType(TextField)).controller?.text, isEmpty);
    expect(tester.takeException(), isNull);
    await harness.close(tester);
    await store.destroy();
    controller.dispose();
  });
}
