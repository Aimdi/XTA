import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_credentials.dart';
import 'package:xta/plugins/booru/booru_site_profiles.dart';

const _danbooru = 'https://danbooru.donmai.us';
const _e621 = 'https://e621.net';

PrefServiceCache _onDanbooru() => PrefServiceCache(
  cache: {
    optionPluginBooruHost: _danbooru,
    optionPluginBooruMaxRating: 'q',
    optionPluginBooruMutedTags: '["gore"]',
    optionPluginBooruLogin: 'reader',
    optionPluginBooruApiKey: 'secret-key',
    optionPluginBooruCredentialsHost: _danbooru,
  },
);

void main() {
  test('each site gets back its own rating cap, blacklist, login and key', () async {
    final prefs = _onDanbooru();

    expect(await swapBooruSiteProfile(prefs, from: _danbooru, to: _e621), isFalse);
    expect(
      prefs.get<String>(optionPluginBooruMutedTags),
      '["gore"]',
      reason: 'a new site starts with the blacklist in use',
    );
    await prefs.set(optionPluginBooruHost, _e621);
    await prefs.set(optionPluginBooruMaxRating, 'e');
    await prefs.set(optionPluginBooruMutedTags, '["scat"]');
    await saveBooruCredential(prefs, optionPluginBooruLogin, 'fox');

    expect(await swapBooruSiteProfile(prefs, from: _e621, to: _danbooru), isTrue);
    await prefs.set(optionPluginBooruHost, _danbooru);
    expect(prefs.get<String>(optionPluginBooruMaxRating), 'q');
    expect(prefs.get<String>(optionPluginBooruMutedTags), '["gore"]');
    expect(booruCredential(prefs, optionPluginBooruLogin), 'reader');
    expect(booruCredential(prefs, optionPluginBooruApiKey), 'secret-key');

    expect(await swapBooruSiteProfile(prefs, from: _danbooru, to: _e621), isTrue);
    await prefs.set(optionPluginBooruHost, _e621);
    expect(prefs.get<String>(optionPluginBooruMaxRating), 'e');
    expect(prefs.get<String>(optionPluginBooruMutedTags), '["scat"]');
    expect(booruCredential(prefs, optionPluginBooruLogin), 'fox');
  });

  test('staying on a site swaps nothing', () async {
    final prefs = _onDanbooru();
    expect(await swapBooruSiteProfile(prefs, from: _danbooru, to: _danbooru), isFalse);
    expect(prefs.get<String>(optionPluginBooruSiteSettings), isNull);
  });

  test('logins are kept where exports and crash reports leave them out; ratings and blacklists are not', () {
    expect(isSecretPrefKey(optionPluginBooruSiteCredentials), isTrue);
    expect(isSecretPrefKey(optionPluginBooruSiteSettings), isFalse);
  });

  test('a removed site takes what it kept with it', () async {
    final prefs = _onDanbooru();
    await swapBooruSiteProfile(prefs, from: _danbooru, to: _e621);

    await forgetBooruSiteProfile(prefs, _danbooru);

    expect(await swapBooruSiteProfile(prefs, from: _e621, to: _danbooru), isFalse);
    expect(prefs.get<String>(optionPluginBooruSiteCredentials), isNot(contains('secret-key')));
  });
}
