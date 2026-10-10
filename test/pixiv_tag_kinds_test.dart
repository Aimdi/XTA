import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_tag_kinds.dart';
import 'package:xta/plugins/plugin_tag_chip.dart';

List<String> _names(List<PixivKindedTag> tags) => [for (final entry in tags) entry.tag.name];

Map<String, PluginTagKind> _kinds(List<String> names) => {
  for (final entry in pixivKindedTags([for (final name in names) PixivTag(name: name)])) entry.tag.name: entry.kind,
};

void main() {
  group('character series', () {
    test('reads the series out of ASCII and full-width parentheses', () {
      expect(pixivCharacterSeries('マルチャーナ(勝利の女神:NIKKE)'), '勝利の女神:NIKKE');
      expect(pixivCharacterSeries('ホシノ（ブルーアーカイブ）'), 'ブルーアーカイブ');
      expect(pixivCharacterSeries('Name (Series)'), 'Series');
    });

    test('needs both a name and a series', () {
      for (final tag in ['(勝利の女神:NIKKE)', 'マルチャーナ()', '（）', ' (Series)', 'Name( )', 'マルチャーナ', '(・ω・)']) {
        expect(pixivCharacterSeries(tag), isNull, reason: tag);
      }
    });

    test('mismatched parentheses name no series', () {
      expect(pixivCharacterSeries('Name(Series）'), isNull);
      expect(pixivCharacterSeries('Name（Series)'), isNull);
    });
  });

  group('tag kinds', () {
    test('a Name(Series) tag is a character and its series a copyright', () {
      expect(_kinds(['マルチャーナ(勝利の女神:NIKKE)', '勝利の女神:NIKKE', '水着']), {
        'マルチャーナ(勝利の女神:NIKKE)': PluginTagKind.character,
        '勝利の女神:NIKKE': PluginTagKind.copyright,
        '水着': PluginTagKind.general,
      });
    });

    test('full-width parentheses name a character and a copyright too', () {
      expect(_kinds(['ホシノ（ブルーアーカイブ）', 'ブルーアーカイブ']), {
        'ホシノ（ブルーアーカイブ）': PluginTagKind.character,
        'ブルーアーカイブ': PluginTagKind.copyright,
      });
    });

    test('a series no other tag names stays a character without a copyright', () {
      expect(_kinds(['アリス(ブルーアーカイブ)', 'ブルアカ']), {
        'アリス(ブルーアーカイブ)': PluginTagKind.character,
        'ブルアカ': PluginTagKind.general,
      });
    });

    test('Pixiv\'s original tag is a copyright', () {
      expect(pixivTagKind('オリジナル', const {}), PluginTagKind.copyright);
    });

    test('ratings, AI and bookmark milestones are meta', () {
      for (final tag in ['R-18', 'R-18G', 'AI生成', '1000users入り', '50users入り', '10000users入り']) {
        expect(pixivTagKind(tag, const {}), PluginTagKind.meta, reason: tag);
      }
    });

    test('anything unsure is general', () {
      for (final tag in ['女の子', 'r-18', 'R-18G生成', 'users入り', '1000users', 'AIイラスト', '1000users入り!']) {
        expect(pixivTagKind(tag, const {}), PluginTagKind.general, reason: tag);
      }
    });
  });

  test('tags are ordered copyright, character, general, meta and keep Pixiv\'s order within a kind', () {
    final tags = [
      for (final name in [
        'R-18',
        '女の子',
        'マルチャーナ(勝利の女神:NIKKE)',
        '1000users入り',
        'オリジナル',
        '水着',
        'ホシノ（ブルーアーカイブ）',
        '勝利の女神:NIKKE',
      ])
        PixivTag(name: name),
    ];
    expect(_names(pixivKindedTags(tags)), [
      'オリジナル',
      '勝利の女神:NIKKE',
      'マルチャーナ(勝利の女神:NIKKE)',
      'ホシノ（ブルーアーカイブ）',
      '女の子',
      '水着',
      'R-18',
      '1000users入り',
    ]);
  });

  test('a work without tags has none', () {
    expect(pixivKindedTags(const []), isEmpty);
  });
}
