import 'package:flutter_test/flutter_test.dart';
import 'package:xta/group/feed_source_reload.dart';

void main() {
  group('which sources a feed must ask again', () {
    // The regression this guards: a group's map only lists sources that still
    // have members, so removing the last subreddit dropped Reddit from the map
    // entirely — and the loop over the new keys never fired for it, leaving its
    // posts in the feed for good.
    test('a source whose last member was removed is asked again, so its posts leave', () {
      final changed = sourcesNeedingReload(
        before: {
          'reddit': ['r/flutter'],
          'threads': ['zuck'],
        },
        after: {
          'threads': ['zuck'],
        },
      );

      expect(changed, {'reddit'});
    });

    test('a source that gained its first member is asked', () {
      final changed = sourcesNeedingReload(
        before: {
          'threads': ['zuck'],
        },
        after: {
          'threads': ['zuck'],
          'reddit': ['r/flutter'],
        },
      );

      expect(changed, {'reddit'});
    });

    test('a source whose members changed is asked', () {
      final changed = sourcesNeedingReload(
        before: {
          'reddit': ['r/flutter'],
        },
        after: {
          'reddit': ['r/flutter', 'r/dartlang'],
        },
      );

      expect(changed, {'reddit'});
    });

    test('nothing changed means nothing is refetched', () {
      final changed = sourcesNeedingReload(
        before: {
          'reddit': ['r/flutter'],
          'threads': ['zuck'],
        },
        after: {
          'reddit': ['r/flutter'],
          'threads': ['zuck'],
        },
      );

      expect(changed, isEmpty);
    });
  });

  group('the ids one source is asked for', () {
    test('are the group members, for an ordinary group', () {
      expect(
        sourceIdsFor(memberIds: ['r/flutter'], isCombinedFeed: false, inHomeFeed: true, homeFeedIds: ['r/dartlang']),
        ['r/flutter'],
      );
    });

    test('include every followed one on the combined feed, when asked for there', () {
      expect(
        sourceIdsFor(memberIds: ['r/flutter'], isCombinedFeed: true, inHomeFeed: true, homeFeedIds: ['r/dartlang']),
        ['r/flutter', 'r/dartlang'],
      );
    });

    // The second regression: the combined feed only asked sources that had
    // members, so a source turned on for the home timeline whose accounts were
    // all hidden from it contributed nothing at all.
    test('are the home-feed ones even when the group has no members of its own', () {
      expect(sourceIdsFor(memberIds: const [], isCombinedFeed: true, inHomeFeed: true, homeFeedIds: ['r/dartlang']), [
        'r/dartlang',
      ]);
    });

    test('are only the members when the source is not in the home feed', () {
      expect(
        sourceIdsFor(memberIds: ['r/flutter'], isCombinedFeed: true, inHomeFeed: false, homeFeedIds: ['r/dartlang']),
        ['r/flutter'],
      );
    });

    test('never repeat an id that is both a member and followed', () {
      expect(
        sourceIdsFor(memberIds: ['r/flutter'], isCombinedFeed: true, inHomeFeed: true, homeFeedIds: ['r/flutter']),
        ['r/flutter'],
      );
    });

    test('are empty when there is nothing to ask for', () {
      expect(
        sourceIdsFor(memberIds: const [], isCombinedFeed: false, inHomeFeed: false, homeFeedIds: const []),
        isEmpty,
      );
    });
  });
}
