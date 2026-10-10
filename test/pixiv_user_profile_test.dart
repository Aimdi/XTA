import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:xta/generated/l10n.dart';
import 'package:flutter/widgets.dart';
import 'package:xta/plugins/pixiv/pixiv_social_api.dart';
import 'package:xta/plugins/pixiv/pixiv_user_info.dart';
import 'package:xta/plugins/pixiv/pixiv_user_profile.dart';

Map<String, Object?> _detail({Map<String, Object?> profile = const {}, Map<String, Object?>? publicity}) => {
  'user': {
    'id': 9,
    'name': 'Mika',
    'account': 'mika',
    'comment': 'Draws cats.',
    'profile_image_urls': {'medium': 'https://i.pximg.net/user-profile/9_170.jpg'},
    'is_followed': true,
  },
  'profile': {
    'webpage': 'https://example.com/mika',
    'gender': 'female',
    'birth': '1990-07-14',
    'birth_day': '07-14',
    'birth_year': 1990,
    'region': 'Tokyo, Japan',
    'job': 'Illustrator',
    'total_follow_users': 1500,
    'total_mypixiv_users': 2,
    'total_illusts': 40,
    'total_manga': 3,
    'total_novels': 1,
    'total_illust_bookmarks_public': 812,
    'background_image_url': 'https://i.pximg.net/background/9.jpg',
    'twitter_account': 'mika_draws',
    'twitter_url': 'https://twitter.com/mika_draws',
    'pawoo_url': 'https://pawoo.net/oauth_authentications/9?provider=pixiv',
    'is_premium': true,
    ...profile,
  },
  'profile_publicity':
      publicity ??
      {
        'gender': 'public',
        'region': 'public',
        'birth_day': 'public',
        'birth_year': 'public',
        'job': 'public',
        'pawoo': true,
      },
  'workspace': {'pc': 'Desktop'},
};

void main() {
  setUpAll(() => initializeDateFormatting('en'));

  group('PixivUserProfile.fromJson', () {
    test('reads the user, the counts, the links and the details', () {
      final profile = PixivUserProfile.fromJson(_detail());
      expect(profile.user.name, 'Mika');
      expect(profile.user.isFollowed, isTrue);
      expect(profile.user.followingCount, 1500);
      expect(profile.user.mypixivCount, 2);
      expect(profile.user.worksCount, 43);
      expect(profile.backgroundUrl, 'https://i.pximg.net/background/9.jpg');
      expect((profile.totalIllusts, profile.totalManga, profile.totalNovels), (40, 3, 1));
      expect(profile.publicBookmarks, 812);
      expect(profile.webpage, 'https://example.com/mika');
      expect(profile.twitterLink, 'https://twitter.com/mika_draws');
      expect(profile.pawooUrl, startsWith('https://pawoo.net/'));
      expect((profile.gender, profile.region, profile.job), ('female', 'Tokyo, Japan', 'Illustrator'));
      expect((profile.birthDay, profile.birthYear), ('07-14', 1990));
      expect(profile.isPremium, isTrue);
      expect(profile.url, 'https://www.pixiv.net/users/9');
    });

    test('profile_publicity hides what the creator keeps private', () {
      final profile = PixivUserProfile.fromJson(
        _detail(
          publicity: {
            'gender': 'private',
            'region': 'private',
            'birth_day': 'public',
            'birth_year': 'private',
            'job': 'mypixiv',
            'pawoo': false,
          },
        ),
      );
      expect(profile.gender, isEmpty);
      expect(profile.region, isEmpty);
      expect(profile.birthDay, '07-14');
      expect(profile.birthYear, isNull);
      expect(profile.job, 'Illustrator');
      expect(profile.pawooUrl, isEmpty);
    });

    test('a missing profile does not throw and reads as empty', () {
      final profile = PixivUserProfile.fromJson({
        'user': {'id': 9, 'name': 'Mika', 'account': 'mika'},
      });
      expect(profile.id, 9);
      expect(profile.backgroundUrl, isNull);
      expect((profile.totalIllusts, profile.totalManga, profile.publicBookmarks), (0, 0, 0));
      expect(profile.webpage, isEmpty);
      expect(profile.twitterLink, isEmpty);
      expect(profile.birthYear, isNull);
      expect(profile.defaultWorkType, PixivWorkType.illust);
      expect(PixivUserProfile.fromJson(null).id, 0);
      expect(PixivUserProfile.fromJson('reshaped').id, 0);
    });

    test('a reshaped profile keeps what still parses and drops what is not a link', () {
      final profile = PixivUserProfile.fromJson(
        _detail(
          profile: {
            'total_illusts': '7',
            'total_manga': '12',
            'background_image_url': '',
            'webpage': 'javascript:alert(1)',
            'twitter_url': null,
            'birth_year': 0,
            'gender': 42,
          },
          publicity: {},
        ),
      );
      expect((profile.totalIllusts, profile.totalManga), (7, 12));
      expect(profile.defaultWorkType, PixivWorkType.manga);
      expect(profile.hasBothWorkTypes, isTrue);
      expect(profile.backgroundUrl, isNull);
      expect(profile.webpage, isEmpty);
      expect(profile.twitterLink, 'https://x.com/mika_draws');
      expect(profile.birthYear, isNull);
      expect(profile.gender, isEmpty);
    });
  });

  test('the birthday shows only the parts that are public', () {
    PixivUserProfile birth(String day, int? year) =>
        PixivUserProfile(user: PixivUserProfile.fromJson(_detail()).user, birthDay: day, birthYear: year);
    expect(pixivBirthdayLabel(birth('07-14', 1990), 'en'), 'July 14, 1990');
    expect(pixivBirthdayLabel(birth('07-14', null), 'en'), 'July 14');
    expect(pixivBirthdayLabel(birth('', 1990), 'en'), '1990');
    expect(pixivBirthdayLabel(birth('', null), 'en'), '');
    expect(pixivBirthdayLabel(birth('13-40', null), 'en'), '');
  });

  group('the Info table', () {
    late L10n l10n;

    setUpAll(() async => l10n = await L10n.load(const Locale('en')));

    test('copies the ID, opens the lists and the links, and leaves out empty fields', () {
      final profile = PixivUserProfile.fromJson(_detail(profile: {'job': '', 'total_novels': 0}));
      final rows = pixivProfileInfoRows(l10n, profile, 'en');
      final byId = {for (final row in rows) row.id: row};
      expect(byId.keys, isNot(contains('job')));
      expect(byId.keys, isNot(contains('novels')));
      expect(byId['nickname']!.value, 'Mika');
      expect(byId['id']!.action, isA<PixivInfoCopy>().having((a) => a.text, 'text', '9'));
      expect(byId['following']!.value, '1,500');
      expect(
        byId['following']!.action,
        isA<PixivInfoList>().having((a) => a.kind, 'kind', PixivUserListKind.following),
      );
      expect(
        byId['followers']!.action,
        isA<PixivInfoList>().having((a) => a.kind, 'kind', PixivUserListKind.followers),
      );
      expect(byId['mypixiv']!.action, isNull);
      expect(byId['birthday']!.value, 'July 14, 1990');
      expect(byId['twitter']!.value, '@mika_draws');
      expect(byId['website']!.action, isA<PixivInfoLink>().having((a) => a.url, 'url', 'https://example.com/mika'));
      expect(byId['pawoo']!.action, isA<PixivInfoLink>());
    });

    test('a bare profile still has its name, ID and counts', () {
      final profile = PixivUserProfile.fromJson({
        'user': {'id': 9, 'name': 'Mika', 'account': 'mika'},
      });
      final ids = [for (final row in pixivProfileInfoRows(l10n, profile, 'en')) row.id];
      expect(ids, ['nickname', 'id', 'following', 'followers', 'mypixiv', 'illusts', 'manga', 'bookmarks']);
    });
  });
}
