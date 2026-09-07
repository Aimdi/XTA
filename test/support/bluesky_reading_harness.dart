import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/ui/x_look_theme.dart';

BlueskyPost bluePost(String id, {String? parent, bool media = false,
    bool sensitive = false, bool video = false}) => BlueskyPost(
  uri: 'at://did:plc:maya/app.bsky.feed.post/$id', cid: 'cid-$id',
  handle: 'maya.bsky.social', did: 'did:plc:maya', authorName: 'Maya Chen',
  text: switch (id) {
    'root' => 'A few sketches from the coast this weekend. I keep coming back to these colours.',
    'a' => 'The light here is beautiful. Was this just before sunset?',
    'a1' => 'Yes! Everything changed colour so quickly.',
    'b' => 'These would make a lovely little print collection.',
    _ => 'Field notes $id: quiet places and the long way home.',
  },
  url: 'https://bsky.app/profile/maya.bsky.social/post/$id',
  replyToUri: parent == null ? null : 'at://did:plc:maya/app.bsky.feed.post/$parent',
  isReply: parent != null,
  images: media ? ['https://review.example/$id.png'] : const [],
  imageIsVideo: media ? [video] : const [],
  labels: sensitive ? ['nudity'] : const [],
  replyCount: 2, likeCount: 42, repostCount: 8,
);

const blueProfile = BlueskyProfile(
  did: 'did:plc:maya', handle: 'maya.bsky.social', displayName: 'Maya Chen',
  description: 'Designer, cyclist, and collector of small observations.',
  followersCount: 1284, followsCount: 218, postsCount: 906,
);

class BlueskyReadingClient extends BlueskyClient {
  final calls = <({String? filter, String? cursor})>[];
  Completer<BlueskyProfile>? nextProfile;
  Completer<BlueskyFeedPage>? nextPage;
  Completer<BlueskyThread>? nextThread;
  bool failMore = false;
  @override
  Future<BlueskyProfile> getProfile(String actor) => nextProfile?.future ?? Future.value(blueProfile);
  @override
  Future<BlueskyFeedPage> getAuthorFeed(String actor,
      {int limit = 20, String? cursor, String? filter}) async {
    calls.add((filter: filter, cursor: cursor));
    if (cursor != null && nextPage != null) return nextPage!.future;
    if (cursor != null && failMore) throw StateError('offline');
    if (cursor != null) return const BlueskyFeedPage(posts: []);
    if (filter == kBlueskyAuthorFeedMedia) return BlueskyFeedPage(posts: [
      for (var i = 0; i < 18; i++) bluePost('image-$i', media: true, sensitive: i == 3, video: i == 1),
    ], cursor: 'media-next');
    if (filter == kBlueskyAuthorFeedReplies) return BlueskyFeedPage(posts: [
      bluePost('not-a-reply'), bluePost('a', parent: 'root'),
    ], cursor: 'replies-next');
    return BlueskyFeedPage(posts: [for (var i = 0; i < 25; i++) bluePost('post-$i')], cursor: 'posts-next');
  }
  @override
  Future<BlueskyThread> getPostThread(String uri, {int depth = 10, int parentHeight = 80}) async {
    if (nextThread != null) return nextThread!.future;
    return BlueskyThread(post: bluePost('root'), ancestors: [bluePost('context')], replies: [
      bluePost('a', parent: 'root'), bluePost('b', parent: 'root'), bluePost('a1', parent: 'a'),
    ]);
  }
}

class BlueLikes extends BlueskyLikesStore {
  BlueLikes(super.prefs);
  @override
  Future<void> load() async {}
  @override
  bool isLiked(String uri) => state.any((post) => post.uri == uri);
  @override
  Future<void> toggle(BlueskyPost post) async => update(isLiked(post.uri)
      ? state.where((liked) => liked.uri != post.uri).toList() : [post, ...state]);
}

class BlueReadingHarness {
  final prefs = PrefServiceCache(defaults: {
    optionZenMode: false, optionCalmMode: false, optionDisableAnimations: true,
    optionThemeTrueBlack: false, optionThemeTrueBlackTweetCards: false,
  });
  final client = BlueskyReadingClient();
  final accounts = BlueskyAccountsStore();
  late final likes = BlueLikes(prefs);
  BlueReadingHarness() {
    accounts.update([blueProfile.toAccount()]);
    likes.update([bluePost('root')]);
  }
  Widget app(Widget child, {bool dark = false, double scale = 1, bool rtl = false}) => PrefService(
    service: prefs,
    child: MultiProvider(providers: [
      Provider<BlueskyClient>.value(value: client),
      Provider<BlueskyAccountsStore>.value(value: accounts),
      Provider<BlueskyLikesStore>.value(value: likes),
    ], child: MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: dark ? xLookLightsOutTheme(null) : xLookLightTheme(null),
      localizationsDelegates: const [L10n.delegate, GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate, GlobalCupertinoLocalizations.delegate],
      supportedLocales: L10n.delegate.supportedLocales,
      builder: (context, child) => RepaintBoundary(key: const ValueKey('bluesky-window'),
        child: MediaQuery(data: MediaQuery.of(context).copyWith(
          textScaler: TextScaler.linear(scale), disableAnimations: true),
          child: Directionality(textDirection: rtl ? TextDirection.rtl : TextDirection.ltr, child: child!))),
      home: child,
    )),
  );
  Future<void> close(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    await likes.destroy();
    await accounts.destroy();
    client.httpClient.close();
  }
}
