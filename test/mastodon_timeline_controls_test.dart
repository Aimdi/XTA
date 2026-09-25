import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_reading_store.dart';
import 'package:xta/plugins/mastodon/mastodon_timeline_controls.dart';
import 'support/mastodon_harness.dart';

MastodonPost entry(
  String id, {
  String? text,
  bool boost = false,
  bool reply = false,
  bool media = false,
  int? day,
  int? boostedDay,
  String? url,
}) => MastodonPost(
  id: id,
  acct: 'a@social.example',
  authorName: 'Alice',
  text: text ?? id,
  url: url ?? 'https://social.example/@a/$id',
  boosted: boost,
  replyToId: reply ? 'parent' : null,
  images: media ? ['https://media.example/$id.png'] : [],
  publishedAt: day == null ? null : DateTime.utc(2026, 9, day),
  timelineAt: boostedDay == null ? null : DateTime.utc(2026, 9, boostedDay),
);

void main() {
  test('combined filters use all query words and preserve source ordering', () {
    final posts = [
      entry('boost', text: 'alice flower garden', boost: true, media: true),
      entry('reply', text: 'flower garden', reply: true, media: true),
      entry('plain', text: 'flower garden'),
      entry('match', text: 'A FLOWER in the garden', media: true),
      entry('miss', text: 'flower', media: true),
    ];
    expect(
      filterMastodonTimeline(
        posts,
        const MastodonTimelineOptions(
          query: '  garden  flower Alice ',
          content: MastodonContentFilter.media,
          hideBoosts: true,
          hideReplies: true,
        ),
      ).map((p) => p.id),
      ['match'],
    );
    expect(posts.length, 5);
  });

  test('links match URL-only posts and embedded cards but not ordinary text', () {
    final posts = [
      entry('url', text: 'https://example.org'),
      entry('text'),
      const MastodonPost(
        id: 'card',
        acct: 'a',
        authorName: 'A',
        text: '',
        url: 'https://social.example/3',
        linkCard: MastodonLinkCard(url: 'https://example.org', title: 'A card'),
      ),
    ];
    expect(
      filterMastodonTimeline(
        posts,
        const MastodonTimelineOptions(content: MastodonContentFilter.links),
      ).map((p) => p.id),
      ['url', 'card'],
    );
  });

  test('dates sort by boost time with stable ties and unknown dates last', () {
    final posts = [
      entry('unknown'),
      entry('old', day: 1),
      entry('boost', day: 1, boostedDay: 9),
      entry('same-a', day: 3),
      entry('same-b', day: 3),
    ];
    expect(
      filterMastodonTimeline(
        posts,
        const MastodonTimelineOptions(order: MastodonTimelineOrder.oldest),
      ).map((p) => p.id),
      ['old', 'same-a', 'same-b', 'boost', 'unknown'],
    );
    expect(
      filterMastodonTimeline(
        posts,
        const MastodonTimelineOptions(order: MastodonTimelineOrder.newest),
      ).map((p) => p.id),
      ['boost', 'same-a', 'same-b', 'old', 'unknown'],
    );
    expect(posts.first.id, 'unknown');
  });

  test('canonical duplicates collapse without colliding between hosts', () {
    final posts = [
      entry('one'),
      entry('duplicate', url: 'https://SOCIAL.example/@a/one/?tracking=1'),
      entry('one', url: 'https://other.example/@a/one'),
    ];
    expect(filterMastodonTimeline(posts, const MastodonTimelineOptions()).length, 2);
  });

  test('preferences persist per timeline across surfaces but never save search text', () async {
    final prefs = PrefServiceCache();
    final first = MastodonTimelineControlsStore(prefs);
    first.select(
      'client:1',
      const MastodonTimelineOptions(query: 'private query', hideBoosts: true, order: MastodonTimelineOrder.oldest),
    );
    first.select('home:2', const MastodonTimelineOptions(hideReplies: true));
    await first.flush();
    expect(prefs.get<String>(mastodonTimelinePreference), isNot(contains('private query')));
    final second = MastodonTimelineControlsStore(prefs);
    expect(second.options('home:1').hideBoosts, true);
    expect(second.options('home:1').order, MastodonTimelineOrder.oldest);
    expect(second.options('home:1').query, '');
    expect(second.options('client:2').hideReplies, true);
    expect(second.options('client:0').activeFilters, 0);
    second.reset('home:1');
    expect(second.options('client:1').order, MastodonTimelineOrder.feed);
    await first.destroy();
    await second.destroy();
  });

  test('malformed stored choices keep all posts visible', () async {
    final prefs = PrefServiceCache();
    await prefs.set(mastodonTimelinePreference, '{"0":{"content":"missing","order":false,"hideBoosts":[]}}');
    final store = MastodonTimelineControlsStore(prefs);
    expect(store.options('client:0').activeFilters, 0);
    expect(store.options('client:0').order, MastodonTimelineOrder.feed);
    await store.destroy();
  });

  testWidgets('filter sheet applies locally and preserves complete reading snapshot', (tester) async {
    final harness = MastodonHarness();
    await tester.pumpWidget(harness.app());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Filters'));
    await tester.pumpAndSettle();
    expect(find.text('Filters and sorting apply to posts already loaded on this device.'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'piano');
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    final cards = tester.widgetList<MastodonPostCard>(find.byType(MastodonPostCard));
    expect(cards, isNotEmpty);
    expect(cards.every((card) => card.post.text.contains('piano')), true);
    expect(harness.client.searches, 0);
    await tester.pump(const Duration(milliseconds: 400));
    final raw = jsonDecode(harness.prefs.get<String>(mastodonReadingPreference)!);
    final remembered = raw['points']['client:0']['posts'] as List;
    expect(remembered.any((post) => !(post['text'] as String).contains('piano')), true);
    await harness.close(tester);
  });

  testWidgets('empty local results offer reset and preserve the underlying feed', (tester) async {
    final harness = MastodonHarness();
    await tester.pumpWidget(harness.app());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Filters'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'no-such-post');
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(find.text('No items match these filters'), findsOneWidget);
    expect(find.byType(MastodonPostCard), findsNothing);
    await tester.tap(find.text('Reset filters'));
    await tester.pumpAndSettle();
    expect(find.byType(MastodonPostCard), findsWidgets);
    expect(harness.explore.state.posts.length, samplePosts.length);
    await harness.close(tester);
  });

  testWidgets('timeline controls and sheet fit compact RTL at enlarged text', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final harness = MastodonHarness();
    await tester.pumpWidget(harness.app(scale: 1.6, rtl: true));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Filters'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'piano');
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await harness.close(tester);
  });
}
