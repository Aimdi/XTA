import 'package:flutter_test/flutter_test.dart';
import 'package:xta/subscriptions/group_add_sources.dart';

const _all = {
  GroupAddSource.reddit,
  GroupAddSource.threads,
  GroupAddSource.substack,
  GroupAddSource.bluesky,
  GroupAddSource.mastodon,
  GroupAddSource.rss,
};

List<GroupAddSource> _sourcesFor(
  String query, {
  Set<GroupAddSource> enabled = _all,
}) => groupAddCandidates(query, enabled: enabled).map((e) => e.source).toList();

GroupAddCandidate _only(String query) =>
    groupAddCandidates(query, enabled: _all).single;

void main() {
  group('groupAddCandidates', () {
    test('nothing typed offers nothing', () {
      expect(groupAddCandidates('   ', enabled: _all), isEmpty);
    });

    test('a bare name could be any of three, so all three are offered', () {
      expect(_sourcesFor('flutter'), [
        GroupAddSource.reddit,
        GroupAddSource.threads,
        GroupAddSource.substack,
      ]);
    });

    test('r/ names a subreddit and nothing else', () {
      expect(_only('r/flutter'), (
        source: GroupAddSource.reddit,
        value: 'flutter',
        label: 'r/flutter',
      ));
    });

    test('a Threads address names a Threads account', () {
      expect(_only('https://www.threads.com/@zuck'), (
        source: GroupAddSource.threads,
        value: 'zuck',
        label: '@zuck',
      ));
    });

    test('a Reddit address names a subreddit', () {
      expect(
        _only('https://www.reddit.com/r/flutter/').source,
        GroupAddSource.reddit,
      );
    });

    test('a bsky.app profile names a Bluesky account', () {
      expect(_only('https://bsky.app/profile/alice.bsky.social'), (
        source: GroupAddSource.bluesky,
        value: 'alice.bsky.social',
        label: '@alice.bsky.social',
      ));
    });

    test('user@instance is a Fediverse address and nothing else', () {
      expect(_only('@alice@mastodon.social'), (
        source: GroupAddSource.mastodon,
        value: 'alice@mastodon.social',
        label: '@alice@mastodon.social',
      ));
    });

    test(
      'a Fediverse profile URL is read from its path, whatever the domain',
      () {
        expect(
          _only('https://chaos.social/@alice').source,
          GroupAddSource.mastodon,
        );
      },
    );

    test(
      'a dotted name could be a Bluesky handle or a newsletter on its own domain',
      () {
        expect(_sourcesFor('alice.bsky.social'), [
          GroupAddSource.bluesky,
          GroupAddSource.substack,
        ]);
      },
    );

    test('a feed URL is offered as RSS, not as a newsletter', () {
      expect(_only('https://example.com/feed.xml'), (
        source: GroupAddSource.rss,
        value: 'https://example.com/feed.xml',
        label: 'example.com',
      ));
    });

    test('a publication address is offered as its host', () {
      final candidate = _only(
        'https://astralcodexten.substack.com/p/some-post',
      );

      expect(candidate.source, GroupAddSource.substack);
      expect(candidate.label, 'astralcodexten.substack.com');
    });

    test('a bare name is offered as its Substack address', () {
      final substack = groupAddCandidates(
        'astralcodexten',
        enabled: {GroupAddSource.substack},
      ).single;

      expect(substack.label, 'astralcodexten.substack.com');
    });

    test('a source the reader turned off is never offered', () {
      expect(_sourcesFor('flutter', enabled: {GroupAddSource.threads}), [
        GroupAddSource.threads,
      ]);
    });

    test('nothing is offered when every source is off', () {
      expect(groupAddCandidates('flutter', enabled: const {}), isEmpty);
    });

    test('every source knows its plugin and its switch', () {
      for (final source in GroupAddSource.values) {
        expect(pluginIdOfSource(source), isNotEmpty);
        expect(enabledOptionOfSource(source), isNotEmpty);
      }
    });
  });
}
