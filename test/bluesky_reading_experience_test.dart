import 'dart:async';
import 'dart:io';
import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_content_warning.dart';
import 'package:xta/plugins/bluesky/bluesky_media_grid.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_store.dart';
import 'package:xta/plugins/bluesky/bluesky_thread_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_thread_store.dart';
import 'package:xta/plugins/plugin_profile_tabs.dart';
import 'package:xta/utils/json.dart';
import 'support/bluesky_reading_harness.dart';
import 'support/reader_review_harness.dart' show reviewImageBytes, ReviewImageOverrides;

Map<String, Object?> _view(String id, {String? parent}) => {
  'uri': 'at://did:plc:maya/app.bsky.feed.post/$id',
  'author': {'did': 'did:plc:maya', 'handle': 'maya.bsky.social'},
  'record': {'text': id, if (parent != null) 'reply': {'parent': {'uri': parent}},
    'labels': {'values': [{'val': 'nudity'}]}},
  'labels': [{'val': 'sexual', 'neg': true}, {'val': 'graphic-media'}],
};

void main() {
  test('reply metadata and active warnings survive local-like snapshots', () {
    final parsed = blueskyPostFromView(_view('root', parent: bluePost('parent').uri))!;
    final restored = BlueskyPost.fromSnapshot(parsed.toJson());
    expect(restored.replyToUri, bluePost('parent').uri);
    expect(restored.labels, containsAll(['graphic-media', 'nudity']));
    expect(restored.labels, isNot(contains('sexual')));
    expect(restored.sensitive, isTrue);
    expect(BlueskyPost.fromSnapshot({'uri': 'old'}).sensitive, isFalse);
    expect(blueskyLabelsOf(const Json({'labels': false}), const Json(null)), isEmpty);
  });

  test('thread topology supplies a parent when a record omits it', () {
    final parsed = parseBlueskyThread({'thread': {'post': _view('root'), 'replies': [
      {'post': _view('child'), 'replies': [{'post': _view('grandchild')}]},
    ]}})!;
    expect(parsed.replies.first.replyToUri, parsed.post.uri);
    expect(parsed.replies.last.replyToUri, parsed.replies.first.uri);
  });

  test('branch collapse retains siblings, orphans and cyclic posts exactly once', () {
    final thread = BlueskyThread(post: bluePost('root'), replies: [
      bluePost('a', parent: 'root'), bluePost('b', parent: 'root'),
      bluePost('a1', parent: 'a'), bluePost('orphan', parent: 'missing'),
      bluePost('cycle-a', parent: 'cycle-b'), bluePost('cycle-b', parent: 'cycle-a'),
      bluePost('a', parent: 'root'), bluePost('root'),
    ]);
    final branches = blueskyReplyBranches(thread);
    final expanded = blueskyVisibleReplies(branches, {});
    expect(expanded.take(3).map((row) => row.branch.post.uri), [
      bluePost('a').uri, bluePost('a1').uri, bluePost('b').uri,
    ]);
    expect(expanded.map((row) => row.branch.post.uri).toSet().length, 6);
    expect(expanded.first.branch.descendants, 1);
    final collapsed = blueskyVisibleReplies(branches, {bluePost('a').uri});
    expect(collapsed.map((row) => row.branch.post.uri), isNot(contains(bluePost('a1').uri)));
    expect(collapsed.map((row) => row.branch.post.uri), contains(bluePost('b').uri));
  });

  test('paging result stays in Replies when Media is selected in flight', () async {
    final client = BlueskyReadingClient();
    final likes = BlueLikes(PrefServiceCache());
    final store = BlueskyProfileStore(client, likes, blueProfile.handle);
    addTearDown(store.destroy);
    addTearDown(likes.destroy);
    addTearDown(client.httpClient.close);
    await store.refresh();
    await store.select(PluginProfileFeedTab.replies);
    expect(store.state.feed.posts.map((post) => post.uri), [bluePost('a').uri]);
    client.nextPage = Completer<BlueskyFeedPage>();
    final paging = store.loadMore();
    await store.select(PluginProfileFeedTab.media);
    client.nextPage!.complete(BlueskyFeedPage(posts: [
      bluePost('unrelated'), bluePost('a2', parent: 'a'), bluePost('a2', parent: 'a'),
    ], cursor: 'replies-final'));
    await paging;
    expect(store.state.selected, PluginProfileFeedTab.media);
    expect(store.state.feed.posts.every((post) => post.hasMedia), isTrue);
    final replies = store.state.feeds[PluginProfileFeedTab.replies]!;
    expect(replies.posts.map((post) => post.uri), [bluePost('a').uri, bluePost('a2').uri]);
    expect(replies.cursor, 'replies-final');
  });

  test('paging errors retain cards and wait for explicit retry', () async {
    final client = BlueskyReadingClient()..failMore = true;
    final likes = BlueLikes(PrefServiceCache());
    final store = BlueskyProfileStore(client, likes, blueProfile.handle);
    addTearDown(store.destroy);
    addTearDown(likes.destroy);
    addTearDown(client.httpClient.close);
    await store.refresh();
    final original = store.state.feed.posts;
    await store.loadMore();
    expect(store.state.feed.posts, original);
    expect(store.state.feed.error, isNotNull);
    final calls = client.calls.length;
    await store.loadMore();
    expect(client.calls.length, calls);
    client.failMore = false;
    await store.load(store.state.selected, more: true);
    expect(store.state.feed.error, isNull);
    expect(store.state.feed.cursor, isNull);
  });

  test('refresh keeps visible cards and other tab data while the profile loads', () async {
    final client = BlueskyReadingClient();
    final likes = BlueLikes(PrefServiceCache());
    final store = BlueskyProfileStore(client, likes, blueProfile.handle);
    addTearDown(store.destroy);
    addTearDown(likes.destroy);
    addTearDown(client.httpClient.close);
    await store.refresh();
    final posts = store.state.feed.posts;
    await store.select(PluginProfileFeedTab.media);
    final media = store.state.feed.posts;
    client.nextProfile = Completer<BlueskyProfile>();
    final refresh = store.refresh();
    expect(store.state.feed.posts, media);
    expect(store.state.loading, isTrue);
    client.nextProfile!.complete(blueProfile);
    await refresh;
    expect(store.state.selected, PluginProfileFeedTab.media);
    expect(store.state.feeds[PluginProfileFeedTab.posts]!.posts, posts);
  });

  test('thread refresh after disposal cannot publish a late response', () async {
    final client = BlueskyReadingClient()..nextThread = Completer<BlueskyThread>();
    final store = BlueskyThreadStore(client, bluePost('root'));
    final load = store.refresh();
    await store.destroy();
    client.nextThread!.complete(BlueskyThread(post: bluePost('changed')));
    await load;
    expect(store.state.thread.post.uri, bluePost('root').uri);
    client.httpClient.close();
  });

  testWidgets('sensitive grid and card do not mount media until revealed', (tester) async {
    final h = BlueReadingHarness();
    addTearDown(() => h.close(tester));
    final previous = HttpOverrides.current;
    final bytes = await tester.runAsync(reviewImageBytes);
    HttpOverrides.global = ReviewImageOverrides(bytes!);
    addTearDown(() => HttpOverrides.global = previous);
    final post = bluePost('private', media: true, sensitive: true);
    await tester.pumpWidget(h.app(Scaffold(body: SizedBox(width: 160, height: 160,
      child: BlueskyMediaTile(post: post)))));
    await tester.pumpAndSettle();
    expect(find.byType(ExtendedImage), findsNothing);
    expect(find.text('Content warning'), findsOneWidget);
    await tester.pumpWidget(h.app(Scaffold(body: SingleChildScrollView(child: BlueskyPostCard(post: post)))));
    await tester.pumpAndSettle();
    expect(find.byType(ExtendedImage), findsNothing);
    expect(find.byType(BlueskyContentWarning), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('bluesky-content-warning')));
    await tester.pump();
    await tester.runAsync(() async {
      await Future<void>.delayed(const Duration(milliseconds: 80));
    });
    await tester.pumpAndSettle();
    expect(find.byType(ExtendedImage), findsOneWidget);
  });

  testWidgets('conversation highlights the opened post and collapses a branch', (tester) async {
    tester.view.physicalSize = const Size(390, 1100);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final h = BlueReadingHarness();
    addTearDown(() => h.close(tester));
    await tester.pumpWidget(h.app(BlueskyThreadScreen(post: bluePost('root'))));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('bluesky-thread-selected')), findsOneWidget);
    expect(find.text(bluePost('context').text), findsNothing);
    expect(find.text(bluePost('a1').text), findsOneWidget);
    await tester.tap(find.byKey(ValueKey('bluesky-collapse-${bluePost('a').uri}')));
    await tester.pumpAndSettle();
    expect(find.text(bluePost('a1').text), findsNothing);
    expect(find.text(bluePost('b').text), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('bluesky-thread-context')));
    await tester.pumpAndSettle();
    expect(find.text(bluePost('context').text), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('profile names local likes accurately and retains Posts position', (tester) async {
    final h = BlueReadingHarness();
    addTearDown(() => h.close(tester));
    await tester.pumpWidget(h.app(const BlueskyProfileScreen(actor: 'maya.bsky.social')));
    await tester.pumpAndSettle();
    await tester.drag(find.byType(CustomScrollView).last, const Offset(0, -850));
    await tester.pumpAndSettle();
    final postsKey = const PageStorageKey('bluesky-profile-maya.bsky.social-posts');
    final postsScroll = find.descendant(of: find.byKey(postsKey), matching: find.byType(Scrollable)).first;
    final offset = tester.state<ScrollableState>(postsScroll).position.pixels;
    expect(offset, greaterThan(100));
    await tester.tap(find.byIcon(Icons.bookmark_border).first);
    await tester.pumpAndSettle();
    expect(find.text('Likes'), findsOneWidget);
    expect(find.text('Saved'), findsNothing);
    expect(find.text('Likes stay on this device. Nothing is sent anywhere.'), findsOneWidget);
    expect(find.text(bluePost('root').text), findsOneWidget);
    await tester.tap(find.byIcon(Icons.wysiwyg_outlined).first);
    await tester.pumpAndSettle();
    expect(tester.state<ScrollableState>(postsScroll).position.pixels, closeTo(offset, 2));
    expect(tester.takeException(), isNull);
  });
}
