import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/article_link_card.dart';
import 'package:xta/utils/urls.dart';

void main() {
  group('articleIdIn', () {
    test('takes the id out of an article link', () {
      expect(articleIdIn('https://x.com/i/article/2080123456789'), '2080123456789');
    });

    test('twitter.com and the www hosts count too', () {
      for (final host in ['twitter.com', 'www.x.com', 'mobile.twitter.com']) {
        expect(articleIdIn('https://$host/i/article/42'), '42', reason: host);
      }
    });

    test('a trailing slug does not confuse it', () {
      expect(articleIdIn('https://x.com/i/article/42/some-title'), '42');
    });

    test('other X links are not articles', () {
      for (final url in [
        'https://x.com/someone/status/123',
        'https://x.com/i/lists/9',
        'https://x.com/i/article',
        'https://x.com/i',
      ]) {
        expect(articleIdIn(url), isNull, reason: url);
      }
    });

    test('another site hosting the same path is not an X article', () {
      expect(articleIdIn('https://example.com/i/article/42'), isNull);
    });

    test('nothing at all is not an article', () {
      expect(articleIdIn(null), isNull);
      expect(articleIdIn(''), isNull);
      expect(articleIdIn('not a url at all ::::'), isNull);
    });
  });

  group('broadcastIdIn', () {
    test('takes the id out of a broadcast link', () {
      expect(broadcastIdIn('https://x.com/i/broadcasts/1YqJvqJvqJvqJvqJvqJvqJ'), '1YqJvqJvqJvqJvqJvqJvqJ');
    });

    test('the singular /i/broadcast/ path counts too', () {
      expect(broadcastIdIn('https://x.com/i/broadcast/1abc'), '1abc');
    });

    test('pscp.tv watch links are broadcasts', () {
      expect(broadcastIdIn('https://pscp.tv/w/1abc'), '1abc');
      expect(broadcastIdIn('https://www.pscp.tv/w/1abc'), '1abc');
    });

    test('twitter.com and the www hosts count too', () {
      for (final host in ['twitter.com', 'www.x.com', 'mobile.twitter.com', 'mobile.x.com']) {
        expect(broadcastIdIn('https://$host/i/broadcasts/1abc'), '1abc', reason: host);
      }
    });

    test('a scheme-less display URL counts', () {
      expect(broadcastIdIn('x.com/i/broadcasts/1disp'), '1disp');
    });

    test('a truncated display URL is not an id', () {
      expect(broadcastIdIn('x.com/i/broadcasts/1YqJvq…'), isNull);
    });

    test('article links and statuses are not broadcasts', () {
      for (final url in [
        'https://x.com/i/article/42',
        'https://x.com/someone/status/123',
        'https://x.com/i/broadcasts',
        'https://example.com/i/broadcasts/1abc',
      ]) {
        expect(broadcastIdIn(url), isNull, reason: url);
      }
    });

    test('nothing at all is not a broadcast', () {
      expect(broadcastIdIn(null), isNull);
      expect(broadcastIdIn(''), isNull);
    });

    test('broadcastIdInText finds the link in a post body', () {
      expect(
        broadcastIdInText('watch https://x.com/i/broadcasts/1abc tonight'),
        '1abc',
      );
      expect(
        broadcastIdInText('live at x.com/i/broadcast/1txt'),
        '1txt',
      );
    });
  });

  group('spaceIdIn', () {
    test('takes the id out of a spaces link', () {
      expect(spaceIdIn('https://x.com/i/spaces/1MnxnMDeQLeJO'), '1MnxnMDeQLeJO');
    });

    test('the singular /i/space/ path counts too', () {
      expect(spaceIdIn('https://x.com/i/space/1abc'), '1abc');
    });

    test('twitter.com and the www hosts count too', () {
      for (final host in ['twitter.com', 'www.x.com', 'mobile.twitter.com', 'mobile.x.com']) {
        expect(spaceIdIn('https://$host/i/spaces/1abc'), '1abc', reason: host);
      }
    });

    test('a scheme-less display URL counts', () {
      expect(spaceIdIn('x.com/i/spaces/1disp'), '1disp');
    });

    test('a truncated display URL is not an id', () {
      expect(spaceIdIn('x.com/i/spaces/1Owx…'), isNull);
    });

    test('broadcast links are not spaces', () {
      expect(spaceIdIn('https://x.com/i/broadcasts/1abc'), isNull);
      expect(broadcastIdIn('https://x.com/i/spaces/1abc'), isNull);
    });

    test('spaceIdInText finds the link in a post body', () {
      expect(
        spaceIdInText('join https://x.com/i/spaces/1abc now'),
        '1abc',
      );
      expect(spaceIdInText('live at x.com/i/spaces/1txt'), '1txt');
    });
  });

  group('firstArticleLink', () {
    test('finds the article among ordinary links', () {
      final urls = ['https://example.com', 'https://x.com/i/article/7', 'https://x.com/i/article/8'];

      expect(firstArticleLink(urls), 'https://x.com/i/article/7');
    });

    test('a post with no article link has none', () {
      expect(firstArticleLink(['https://example.com', null]), isNull);
    });

    test('a post with no links at all has none', () {
      expect(firstArticleLink(const []), isNull);
    });
  });
}
