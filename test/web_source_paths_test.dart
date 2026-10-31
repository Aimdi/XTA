import 'package:flutter_test/flutter_test.dart';
import 'package:html/parser.dart' as html;
import 'package:xta/plugins/websources/web_source_paths.dart';

void main() {
  group('JSON paths', () {
    const page = {
      'data': {
        'children': [
          {
            'title': 'First',
            'score': 12,
            'tags': ['a', 'b'],
          },
          {'title': 'Second', 'nsfw': false},
        ],
      },
    };

    test('read keys, indexes and every element', () {
      expect(jsonValuesAt(page, 'data.children[0].title'), ['First']);
      expect(jsonValuesAt(page, r'$.data.children[1].title'), ['Second']);
      expect(jsonValuesAt(page, 'data.children[*].title'), ['First', 'Second']);
      expect(jsonValuesAt(page, 'data.children[0].tags[*]'), ['a', 'b']);
      expect(jsonValuesAt(page, r'$'), [page]);
    });

    test('a path that leads nowhere gives nothing', () {
      expect(jsonValuesAt(page, 'data.missing.title'), isEmpty);
      expect(jsonValuesAt(page, 'data.children[5].title'), isEmpty);
      expect(jsonValuesAt(page, 'data.children.title'), isEmpty);
      expect(jsonValuesAt('text', 'a'), isEmpty);
    });

    test('numbers and booleans are text; maps and lists are not', () {
      expect(jsonText(12), '12');
      expect(jsonText(false), 'false');
      expect(jsonText({'a': 1}), isNull);
      expect(jsonText(null), isNull);
    });

    test('only well-formed paths are paths', () {
      expect(isJsonPath('data.children[*].title'), isTrue);
      expect(isJsonPath(r'$'), isTrue);
      expect(isJsonPath('a..b'), isFalse);
      expect(isJsonPath('a[x]'), isFalse);
      expect(isJsonPath('a b'), isFalse);
      expect(isJsonPath(''), isFalse);
    });

    test('keys may follow an index without a dot', () {
      expect(isJsonPath('images[0]url'), isTrue);
      expect(
        jsonValuesAt({
          'images': [
            {'url': 'a.jpg'},
          ],
        }, 'images[0]url'),
        ['a.jpg'],
      );
    });

    test('a long path that is not one is refused at once', () {
      expect(isJsonPath('${'a' * 4000}{'), isFalse);
      expect(isJsonPath('${'a[0]b' * 4000}@'), isFalse);
    }, timeout: const Timeout(Duration(seconds: 10)));
  });

  group('templates', () {
    test('fill every placeholder, or give nothing', () {
      String? values(String name) => {'post.id': '42', 'name': 'a b'}[name];

      expect(fillTemplate('https://lemmy.world/post/{post.id}', values), 'https://lemmy.world/post/42');
      expect(fillTemplate('{post.id}-{missing}', values), isNull);
      expect(templatePlaceholders('{a}/{b.c}'), ['a', 'b.c']);
      expect(isTemplate('plain.path'), isFalse);
    });

    test('escape values inside an address, but not a whole address', () {
      expect(fillTemplate('https://x.org/s?q={name}', (_) => 'a b&c', encode: true), 'https://x.org/s?q=a%20b%26c');
      expect(fillTemplate('{link}', (_) => 'https://x.org/a b', encode: true), 'https://x.org/a b');
      expect(fillTemplate('https://x.org/?after={cursor}', (_) => '', encode: true), 'https://x.org/?after=');
    });
  });

  group('HTML rules', () {
    final document = html.parse('''
      <ol>
        <li class="story" data-id="s1">
          <a class="title" href="/s/s1">  A   story </a>
          <span class="by">ann</span>
          <div class="body"><p>Hello <b>there</b></p></div>
        </li>
      </ol>''');
    final item = document.querySelector('li.story')!;

    test('read text, attributes, the item itself and markup', () {
      expect(htmlValuesAt(item, 'a.title'), ['A story']);
      expect(htmlValuesAt(item, 'a.title@href'), ['/s/s1']);
      expect(htmlValuesAt(item, '@data-id'), ['s1']);
      expect(htmlValuesAt(item, '.body@html'), ['<p>Hello <b>there</b></p>']);
      expect(htmlValuesAt(item, '.missing'), isEmpty);
    });

    test('split a rule at its attribute only', () {
      expect(htmlRule('a.title@href'), (selector: 'a.title', attribute: 'href'));
      expect(htmlRule('@data-id'), (selector: '', attribute: 'data-id'));
      expect(htmlRule("a[href^='mailto:x@y.org']"), (selector: "a[href^='mailto:x@y.org']", attribute: null));
    });

    test('a selector this parser cannot read matches nothing', () {
      expect(isCssSelector('li.story > a.title'), isTrue);
      expect(isCssSelector('li[[['), isFalse);
      expect(htmlSelectAll(document.documentElement!, 'li[[['), isEmpty);
    });
  });
}
