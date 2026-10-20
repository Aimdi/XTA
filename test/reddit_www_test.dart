import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/reddit/reddit_comments.dart';
import 'package:xta/plugins/reddit/reddit_www.dart';
import 'package:xta/plugins/reddit/reddit_www_comments.dart';
import 'package:xta/plugins/reddit/reddit_www_search.dart';

/// Pages www.reddit.com served on 2026-10-20, trimmed to a few posts each;
/// test/fixtures/README.md says where each one came from.
String _fixture(String name) => File('test/fixtures/Reddit/$name').readAsStringSync();

typedef RedditPostFlags = ({bool over18, bool stickied});

void main() {
  group('a feed page', () {
    test('reads a picture post out of its attributes', () {
      final page = readWwwListing(_fixture('listing_pics.html'));
      final post = page.posts.first;

      expect(page.kind, RedditWwwPageKind.read);
      expect(post.id, '1xawic7', reason: 'the t3_ prefix is the fullname, not the id');
      expect(post.title, 'Early voting today in north Dallas [oc]');
      expect(post.subreddit, 'pics');
      expect(post.author, 'd4wnn');
      expect(post.score, 12287);
      expect(post.commentCount, 411);
      expect(post.createdAt, DateTime.utc(2026, 10, 20, 16, 17, 20, 817).toLocal());
      expect(post.permalink, '/r/pics/comments/1xawic7/early_voting_today_in_north_dallas_oc/');
      expect(post.isSelf, isFalse);
      expect(post.url, 'https://i.redd.it/bm3fgrkkdnwh1.jpeg');
      expect(post.imageUrl, 'https://i.redd.it/bm3fgrkkdnwh1.jpeg');
      expect(post.flair, 'Politics');
      expect(post.previewImage, startsWith('https://preview.redd.it/'));
    });

    test('a post its media marks NSFW or pinned is read as such, as is one in an NSFW community', () {
      RedditPostFlags read(String telemetry) {
        final post = readWwwListing('''
<html><body><shreddit-app>
  <shreddit-post id="t3_a" permalink="/r/x/comments/a/t/" post-title="T" subreddit-name="x">
    <shreddit-media-lightbox-listener telemetry="$telemetry"></shreddit-media-lightbox-listener>
  </shreddit-post>
</shreddit-app></body></html>''').posts.single;
        return (over18: post.over18, stickied: post.stickied);
      }

      expect(read('{&quot;post&quot;:{&quot;nsfw&quot;:true}}'), (over18: true, stickied: false));
      expect(read('{&quot;post&quot;:{&quot;pinned&quot;:true}}'), (over18: false, stickied: true));
      expect(read('{&quot;subreddit&quot;:{&quot;nsfw&quot;:true}}'), (over18: true, stickied: false));
      expect(read('not json'), (over18: false, stickied: false));

      final pictures = readWwwListing(_fixture('listing_pics.html')).posts.first;
      expect((over18: pictures.over18, stickied: pictures.stickied), (over18: false, stickied: false));
    });

    test('a gallery carries every picture, in order, at the size its feed shows', () {
      final post = readWwwListing(_fixture('listing_pics.html')).posts[1];

      expect(post.url, 'https://www.reddit.com/gallery/1xb4rqw');
      expect(post.galleryImages, hasLength(9));
      expect(post.galleryImages.first, contains('28bh0dy5xowh1.jpeg?width=640'));
      expect(post.galleryImages[1], contains('jdl2e1y5xowh1.jpeg?width=640'), reason: 'lazy pages too');
    });

    test('a crosspost of one picture links the picture, of a gallery the original', () {
      final posts = readWwwListing(_fixture('listing_pics.html')).posts;

      final gallery = posts[2];
      expect(gallery.url, 'https://www.reddit.com/r/portugal/comments/1x9anef/protestos_no_porto_171026/');
      expect(gallery.galleryImages, hasLength(20));

      final picture = posts[3];
      expect(picture.url, 'https://i.redd.it/tlguz3aj4owh1.jpeg');
      expect(picture.imageUrl, isNotNull);
    });

    test('a video plays its stream, under its poster frame', () {
      final post = readWwwListing(_fixture('listing_aww.html')).posts.first;

      expect(post.url, 'https://v.redd.it/0570b89w9owh1');
      expect(post.videoDashUrl, startsWith('https://v.redd.it/0570b89w9owh1/HLSPlaylist.m3u8?'));
      expect(post.hasPlayableVideo, isTrue);
      expect(post.previewImage, startsWith('https://external-preview.redd.it/'));
    });

    test('a self post carries its text, links kept', () {
      final posts = readWwwListing(_fixture('listing_flutterdev_top.html')).posts;
      final post = posts[1];

      expect(post.isSelf, isTrue);
      expect(post.url, isNull);
      expect(post.domain, 'self.FlutterDev');
      expect(post.flair, 'Article');
      expect(post.selfText, startsWith('Spent a while figuring out why #Flutter text looks lighter'));
      expect(post.selfText, contains('https://birju.dev/'));
      expect(post.selfText, isNot(contains('  ')), reason: 'the markup indentation is not text');
    });

    test('a link post links its article, with its thumbnail', () {
      final post = readWwwListing(_fixture('listing_android.html')).posts.single;

      expect(post.isSelf, isFalse);
      expect(post.url, 'https://sammyguru.com/galaxy-s27-ultra-snapdragon-overheating/');
      expect(post.domain, 'sammyguru.com');
      expect(post.thumbnailUrl, startsWith('https://external-preview.redd.it/'));
      expect(post.flair, 'Rumour');
    });

    test('the next page starts after the fullname its partial names', () {
      expect(readWwwListing(_fixture('listing_pics.html')).after, 't3_1x9w7f4');
    });

    test('a profile names its next page on its posts instead', () {
      final page = readWwwListing(_fixture('profile_posts.html'));

      expect(page.posts.map((p) => p.id), ['1vgbkge', '1tvsa59', '3cxedn']);
      expect(page.posts.first.subreddit, 'u_spez', reason: 'a profile post lives in the account\'s own community');
      expect(page.after, 't3_3cxedn');
    });

    test('a community with nothing in it reads as empty, not as a failure', () {
      final page = readWwwListing('<!doctype html><html><body><shreddit-app></shreddit-app></body></html>');

      expect(page.kind, RedditWwwPageKind.read);
      expect(page.posts, isEmpty);
      expect(page.after, isNull);
    });

    test('a post missing what every post has is skipped, not guessed at', () {
      final page = readWwwListing('''
<html><body><shreddit-app>
  <shreddit-post id="t3_a" post-title="No permalink"></shreddit-post>
  <shreddit-post permalink="/r/x/comments/b/t/" post-title="No id"></shreddit-post>
  <shreddit-post id="t3_c" permalink="/r/x/comments/c/t/" post-title="Kept" subreddit-name="x"></shreddit-post>
</shreddit-app></body></html>''');

      expect(page.posts.map((p) => p.id), ['c']);
    });
  });

  group('what www.reddit.com answered with', () {
    test('its browser check is recognised', () {
      expect(redditWwwPageKind(_fixture('browser_check.html')), RedditWwwPageKind.browserCheck);
    });

    test('its network block is recognised', () {
      expect(
        redditWwwPageKind(
          '<html><body><shreddit-app>You\'ve been blocked by network security.</shreddit-app></body></html>',
        ),
        RedditWwwPageKind.blocked,
      );
    });

    test('a page with posts is read, whatever a title on it says', () {
      expect(
        redditWwwPageKind(
          '<shreddit-app><shreddit-post id="t3_a" post-title="Blocked by network security?"></shreddit-post>',
        ),
        RedditWwwPageKind.read,
      );
    });

    test('a page of the site it does not know, or no page of it at all, is unreadable', () {
      expect(redditWwwPageKind('<shreddit-app>Page not found</shreddit-app>'), RedditWwwPageKind.unreadable);
      expect(redditWwwPageKind('{"kind": "Listing"}'), RedditWwwPageKind.unreadable);
    });
  });

  group('paging cursors', () {
    test('are base64 of the fullname, with or without padding', () {
      expect(wwwCursorName('dDNfMXg3OXFvdA=='), 't3_1x79qot');
      expect(wwwCursorName('dDNfM2N4ZWRu'), 't3_3cxedn');
      expect(wwwCursorParam('t3_1x79qot'), 'dDNfMXg3OXFvdA==');
    });

    test('anything else is no cursor', () {
      expect(wwwCursorName('not base64!'), isNull);
      expect(wwwCursorName('aGVsbG8='), isNull, reason: '"hello" is no fullname');
      expect(wwwCursorName(''), isNull);
    });
  });

  group('a thread', () {
    test('nests replies under the comment they answer', () {
      final thread = readWwwThread(_fixture('comments.html'));
      final flat = flattenComments(thread.comments);

      expect(thread.kind, RedditWwwPageKind.read);
      expect(thread.comments.map((c) => c.id), ['pht7lgf', 'phride0']);
      expect(flat.map((e) => (e.comment.id, e.depth)), [
        ('pht7lgf', 0),
        ('pht92w5', 1),
        ('phride0', 0),
        ('phuyrt3', 1),
        ('more-/r/FlutterDev/comments/1xa75i8/comment/phuyrt3/', 2),
      ]);
    });

    test('reads a comment out of its attributes and its own words', () {
      final first = readWwwThread(_fixture('comments.html')).comments.first;

      expect(first.author, 'TJGhinder');
      expect(first.score, 7);
      expect(first.createdAt, DateTime.utc(2026, 10, 20, 1, 22, 39, 120).toLocal());
      expect(first.permalink, '/r/FlutterDev/comments/1xa75i8/comment/pht7lgf/');
      expect(first.body, startsWith('Loved this write up!!'));
      expect(first.body, isNot(contains('Thanks')), reason: "a reply's words are the reply's");
      expect(first.isSubmitter, isFalse);
      expect(first.replies.single.isSubmitter, isTrue, reason: 'is-op marks the post\'s author');
    });

    test('replies held back are a stub that opens their parent\'s page', () {
      final stub = flattenComments(readWwwThread(_fixture('comments.html')).comments).last.comment;

      expect(stub.isStub, isTrue);
      expect(stub.moreCount, 1);
      expect(stub.permalink, '/r/FlutterDev/comments/1xa75i8/comment/phuyrt3/');
    });

    test('top-level comments held back are said so', () {
      expect(readWwwThread(_fixture('comments.html')).moreAtRoot, isTrue);
    });

    test("a comment's own page keeps that comment and its replies", () {
      final page = '''
<html><body><shreddit-app><shreddit-comment-tree thingid="t1_child">
  <shreddit-comment thingid="t1_parent" author="a" depth="0">
    <div slot="comment"><div class="md"><p>Parent</p></div></div>
    <shreddit-comment thingid="t1_child" author="b" depth="1" parentid="t1_parent">
      <div slot="comment"><div class="md"><p>Child</p></div></div>
      <shreddit-comment thingid="t1_grandchild" author="c" depth="2" parentid="t1_child">
        <div slot="comment"><div class="md"><p>Grandchild</p></div></div>
      </shreddit-comment>
    </shreddit-comment>
  </shreddit-comment>
  <shreddit-comment thingid="t1_other" author="d" depth="0">
    <div slot="comment"><div class="md"><p>Elsewhere</p></div></div>
  </shreddit-comment>
</shreddit-comment-tree></shreddit-app></body></html>''';

      final thread = readWwwThread(page);

      expect(thread.comments.single.body, 'Child');
      expect(thread.comments.single.replies.single.body, 'Grandchild');
      expect(thread.moreAtRoot, isFalse);
    });

    test('a comment whose words are gone keeps its row for its replies', () {
      final thread = readWwwThread('''
<html><body><shreddit-app><shreddit-comment-tree>
  <shreddit-comment thingid="t1_gone" author="[deleted]" depth="0">
    <shreddit-comment thingid="t1_reply" author="b" depth="1" parentid="t1_gone">
      <div slot="comment"><div class="md"><p>Still here</p></div></div>
    </shreddit-comment>
  </shreddit-comment>
</shreddit-comment-tree></shreddit-app></body></html>''');

      final gone = thread.comments.single;
      expect(gone.isRemoved, isTrue);
      expect(gone.replies.single.body, 'Still here');
    });

    test('the browser check holds no thread', () {
      final thread = readWwwThread(_fixture('browser_check.html'));

      expect(thread.kind, RedditWwwPageKind.browserCheck);
      expect(thread.comments, isEmpty);
    });
  });

  group('search', () {
    test('posts come from their tracking JSON and their counter row', () {
      final posts = readWwwSearchPosts(_fixture('search_posts.html'));
      final first = posts.first;

      expect(posts, hasLength(3));
      expect(first.id, '1xb5vks');
      expect(first.title, '🚀 I built Flutter Version Bumper — a CLI tool to automate Flutter & Dart releases');
      expect(first.permalink, '/r/FlutterDev/comments/1xb5vks/i_built_flutter_version_bumper_a_cli_tool_to/');
      expect(first.subreddit, 'FlutterDev');
      expect(first.author, 'CandidateWhich7277');
      expect(first.score, 1);
      expect(first.commentCount, 0);
      expect(first.createdAt, DateTime.utc(2026, 10, 20, 22, 15, 2, 125).toLocal());
      expect(first.isSelf, isTrue, reason: 'a result names no link target');
      expect(posts[1].thumbnailUrl, startsWith('https://preview.redd.it/'));
    });

    test('communities carry their picture and description', () {
      final communities = readWwwSearchCommunities(_fixture('search_communities.html'));
      final first = communities.first;

      expect(communities.map((c) => c.name), ['FlutterDev', 'flutterhelp', 'FlutterApps']);
      expect(first.description, 'A community for the publishing of news and discussion about Flutter.');
      expect(first.iconUrl, 'https://styles.redditmedia.com/t5_2x3q8/styles/communityIcon_dsw8tf6mg06b1.png');
      expect(first.subscribers, isNull, reason: 'weekly visitors are not subscribers');
    });

    test('people carry their karma', () {
      final people = readWwwSearchPeople(_fixture('search_people.html'));

      expect(people.first, (name: 'spez', karma: 941063));
      expect(people, hasLength(3));
    });

    test('a page that is not a search yields nothing rather than throwing', () {
      expect(readWwwSearchPosts(_fixture('browser_check.html')), isEmpty);
      expect(readWwwSearchCommunities('<html><body>nothing</body></html>'), isEmpty);
    });
  });
}
