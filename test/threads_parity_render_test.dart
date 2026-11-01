import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/threads/threads_likes_store.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/bluesky_reading_images.dart';
import 'support/feed_ink_harness.dart';
import 'support/review_fonts.dart';

/// Renders Threads as the second parity pass draws it — pictures along a row,
/// the opened post with its date line — for review beside X:
///   fvm flutter test test/threads_parity_render_test.dart --dart-define=RENDER_THREADS=true
const _render = bool.fromEnvironment('RENDER_THREADS');

final _postedAt = DateTime.now().subtract(const Duration(minutes: 19));

ThreadsPost _post({required String id, required String text, int pictures = 0, String? topic}) => ThreadsPost(
  id: id,
  handle: 'maya.makes',
  authorName: 'Maya Chen',
  text: text,
  url: 'https://www.threads.com/@maya.makes/post/C$id',
  publishedAt: _postedAt,
  likeCount: 214,
  replyCount: 18,
  repostCount: 6,
  topicTag: topic,
  images: [for (var i = 0; i < pictures; i++) 'https://cdn.example/$id-$i.jpg'],
  imageAspects: [for (var i = 0; i < pictures; i++) i == 1 ? 1.5 : 0.8],
);

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await loadReviewFonts();
  });

  for (final (name, dark, locale) in const [
    ('threads-parity-black-de', true, Locale('de')),
    ('threads-parity-light-en', false, Locale('en')),
  ]) {
    testWidgets(name, skip: !_render, (tester) async {
      await installBlueskyReadingImages(tester);
      final accounts = ThreadsAccountsStore();
      final likes = ThreadsLikesStore(PrefServiceCache());
      addTearDown(() async {
        await accounts.destroy();
        await likes.destroy();
      });
      await pumpInk(
        tester,
        MultiProvider(
          providers: [
            Provider<ThreadsAccountsStore>.value(value: accounts),
            Provider<ThreadsLikesStore>.value(value: likes),
          ],
          child: Scaffold(
            body: ListView(
              children: [
                ThreadsPostCard(
                  post: _post(id: '1', text: 'Three glazes from the same kiln, three moods.', pictures: 3),
                  showSourceBadge: false,
                ),
                ThreadsPostCard(
                  post: _post(id: '2', text: 'The light was right at seven, and the kiln held.', topic: 'Ceramics'),
                  showSourceBadge: false,
                  openOnTap: false,
                  focal: true,
                ),
              ],
            ),
          ),
        ),
        theme: dark ? xLookLightsOutTheme(null) : xLookLightTheme(null),
        locale: locale,
      );
      await settleBlueskyReadingImages(tester);
      expect(tester.takeException(), isNull);
      await expectLater(
        find.byKey(const ValueKey('ink-render')),
        matchesGoldenFile('../review-artifacts/renders/$name.png'),
      );
    });
  }
}
