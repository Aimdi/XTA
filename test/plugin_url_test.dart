import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';
import 'package:xta/plugins/plugin_url.dart';

void main() {
  final mastodonHosts = mastodonLinkHosts(kMastodonDefaultInstances);

  group('parseBlueskyLink', () {
    test('parses a profile and a post', () {
      expect(
        parseBlueskyLink('https://bsky.app/profile/alice.bsky.social'),
        isA<BlueskyProfileLink>().having(
          (link) => link.actor,
          'actor',
          'alice.bsky.social',
        ),
      );
      final post = parseBlueskyLink(
        'https://www.bsky.app/profile/alice.bsky.social/post/3kabc',
      );
      expect(
        post,
        isA<BlueskyPostLink>()
            .having((link) => link.actor, 'actor', 'alice.bsky.social')
            .having((link) => link.rkey, 'rkey', '3kabc'),
      );
      expect(
        (post as BlueskyPostLink).atUri,
        'at://alice.bsky.social/app.bsky.feed.post/3kabc',
      );
    });

    test('parses a DID profile', () {
      expect(
        parseBlueskyLink('https://bsky.app/profile/did:plc:abcdefg'),
        isA<BlueskyProfileLink>().having(
          (link) => link.actor,
          'actor',
          'did:plc:abcdefg',
        ),
      );
    });
  });

  group('parseThreadsLink', () {
    test('parses a profile and a post', () {
      expect(
        parseThreadsLink('https://www.threads.net/@zuck'),
        isA<ThreadsProfileLink>().having(
          (link) => link.handle,
          'handle',
          'zuck',
        ),
      );
      expect(
        parseThreadsLink('https://www.threads.com/@zuck/post/Dabc123'),
        isA<ThreadsPostLink>()
            .having((link) => link.handle, 'handle', 'zuck')
            .having((link) => link.url, 'url', contains('/post/Dabc123')),
      );
    });

    test('ignores short /t/ links', () {
      expect(parseThreadsLink('https://www.threads.net/t/Dabc123'), isNull);
    });
  });

  group('parseInstagramLink', () {
    test('parses a profile and ignores posts', () {
      expect(
        parseInstagramLink('https://www.instagram.com/nasa/'),
        isA<InstagramProfileLink>().having(
          (link) => link.handle,
          'handle',
          'nasa',
        ),
      );
      expect(parseInstagramLink('https://www.instagram.com/p/AbC123/'), isNull);
      expect(
        parseInstagramLink('https://www.instagram.com/reel/AbC123/'),
        isNull,
      );
      expect(parseInstagramLink('https://www.instagram.com/explore/'), isNull);
    });
  });

  group('parseTikTokLink', () {
    test('parses a profile and a video', () {
      expect(
        parseTikTokLink('https://www.tiktok.com/@nasa'),
        isA<TikTokProfileLink>().having(
          (link) => link.handle,
          'handle',
          'nasa',
        ),
      );
      expect(
        parseTikTokLink('https://www.tiktok.com/@nasa/video/1234567890'),
        isA<TikTokVideoLink>()
            .having((link) => link.id, 'id', '1234567890')
            .having((link) => link.handle, 'handle', 'nasa'),
      );
    });

    test('ignores short links', () {
      expect(parseTikTokLink('https://vm.tiktok.com/ZMabc/'), isNull);
    });
  });

  group('parseRedditLink', () {
    test('parses a subreddit, a thread, a user, and redd.it', () {
      expect(
        parseRedditLink('https://www.reddit.com/r/dartlang'),
        isA<RedditSubredditLink>().having(
          (link) => link.name,
          'name',
          'dartlang',
        ),
      );
      expect(
        parseRedditLink(
          'https://old.reddit.com/r/dartlang/comments/abc123/hello_world',
        ),
        isA<RedditThreadLink>()
            .having((link) => link.id, 'id', 'abc123')
            .having((link) => link.subreddit, 'subreddit', 'dartlang')
            .having(
              (link) => link.permalink,
              'permalink',
              '/r/dartlang/comments/abc123/',
            ),
      );
      expect(
        parseRedditLink('https://www.reddit.com/u/spez'),
        isA<RedditUserLink>().having((link) => link.name, 'name', 'spez'),
      );
      expect(
        parseRedditLink('https://redd.it/abc123'),
        isA<RedditThreadLink>().having(
          (link) => link.permalink,
          'permalink',
          '/comments/abc123/',
        ),
      );
    });

    test('ignores media CDNs', () {
      expect(parseRedditLink('https://i.redd.it/abc.jpg'), isNull);
      expect(parseRedditLink('https://v.redd.it/abc'), isNull);
    });
  });

  group('parsePixivWebLink', () {
    test('parses artwork and user URLs but not bare ids', () {
      expect(
        parsePixivWebLink('https://www.pixiv.net/artworks/123'),
        isA<PixivWebLink>().having(
          (link) => link.ref,
          'ref',
          isA<PixivArtworkLinkRef>().having((ref) => ref.id, 'id', 123),
        ),
      );
      expect(parsePixivWebLink('123'), isNull);
    });
  });

  group('parseMastodonLink', () {
    test('parses a known instance and ignores Medium-style hosts', () {
      expect(
        parseMastodonLink(
          'https://mastodon.social/@gargron',
          knownHosts: mastodonHosts,
        ),
        isA<MastodonProfileLink>().having(
          (link) => link.acct,
          'acct',
          'gargron@mastodon.social',
        ),
      );
      expect(
        parseMastodonLink(
          'https://mastodon.social/@gargron/123456',
          knownHosts: mastodonHosts,
        ),
        isA<MastodonStatusLink>().having(
          (link) => link.statusId,
          'statusId',
          '123456',
        ),
      );
      expect(
        parseMastodonLink(
          'https://medium.com/@someone/a-story',
          knownHosts: mastodonHosts,
        ),
        isNull,
      );
    });
  });

  test('parsePluginLink tries networks in order', () {
    expect(
      parsePluginLink('https://bsky.app/profile/alice.bsky.social'),
      isA<BlueskyProfileLink>(),
    );
    expect(
      parsePluginLink(
        'https://mastodon.social/@gargron',
        mastodonHosts: mastodonHosts,
      ),
      isA<MastodonProfileLink>(),
    );
    expect(parsePluginLink('https://example.com/'), isNull);
  });
}
