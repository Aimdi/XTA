import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/search/loaded_feed_search.dart';
import 'package:xta/tweet/interleaved_items.dart';

void main() {
  test('loaded text includes names, handles, expanded URLs and quoted posts', () {
    final tweet = TweetWithCard()
      ..fullText = 'Reading a long article'
      ..user = (User()
        ..name = 'Maya'
        ..screenName = 'mayac')
      ..entities = Entities.fromJson({
        'urls': [
          {
            'expanded_url': 'https://example.org/climate',
            'display_url': 'example.org/climate',
            'url': 'https://t.co/abc',
          },
        ],
      })
      ..quotedStatusWithCard = (TweetWithCard()..fullText = 'Quoted science');
    final entries = loadedFeedEntries(
      [
        TweetChain(id: '1', isPinned: false, tweets: [tweet]),
      ],
      const [],
      null,
    );
    expect(entries.single.matches('MAYA climate'), isTrue);
    expect(entries.single.matches('quoted SCIENCE'), isTrue);
    expect(entries.single.matches('not-present'), isFalse);
  });
  test('plugin snapshots remain searchable without fetching or building posts', () {
    var builds = 0;
    final entries = loadedFeedEntries(const [], [
      InterleavedItem(
        date: DateTime(2026),
        linkUrl: 'https://example.org/story',
        source: 'rss',
        snapshot: {'author': 'Alice', 'text': 'Offline notes'},
        build: (_) {
          builds++;
          return const Text('fixture');
        },
      ),
    ], null);
    expect(entries.single.matches('alice offline'), isTrue);
    expect(entries.single.matches('example.org'), isTrue);
    expect(entries.single.matches('  '), isTrue);
    expect(builds, 0);
  });
}
