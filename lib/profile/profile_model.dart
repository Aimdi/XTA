import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/client/client.dart';
import 'package:xta/user.dart';

class Profile {
  final UserWithExtra user;
  final List<String> pinnedTweets;

  Profile(this.user, this.pinnedTweets);
}

/// How long a fetched profile stands in for the next request for it.
///
/// Opening a profile, reading something and coming back is one of the
/// commonest things a reader does, and it cost a fresh UserByScreenName every
/// time even when the timeline underneath came from disk. Short enough that a
/// rename or a new avatar appears on the visit after next.
const profileCacheMaxAge = Duration(minutes: 5);

/// Bounded, so a long session of profile-hopping cannot grow it without limit.
const _profileCacheMaxEntries = 50;

class ProfileModel extends Store<Profile> {
  ProfileModel() : super(Profile(UserWithExtra(), []));

  static final Map<String, ({Profile profile, DateTime at})> _byScreenName = {};

  static void _remember(String key, Profile profile) {
    if (_byScreenName.length >= _profileCacheMaxEntries) {
      _byScreenName.remove(_byScreenName.keys.first);
    }

    _byScreenName[key] = (profile: profile, at: DateTime.now());
  }

  static Profile? _recall(String key) {
    final cached = _byScreenName[key];
    if (cached == null) {
      return null;
    }

    if (DateTime.now().difference(cached.at) >= profileCacheMaxAge) {
      _byScreenName.remove(key);
      return null;
    }

    return cached.profile;
  }

  Future<void> loadProfileById(String id) async {
    await execute(() async => await Twitter.getProfileById(id));
  }

  Future<void> loadProfileByScreenName(String screenName) async {
    final key = screenName.replaceFirst('@', '').toLowerCase();

    final cached = _recall(key);
    if (cached != null) {
      update(cached, force: true);
      return;
    }

    await execute(() async {
      final profile = await Twitter.getProfileByScreenName(screenName);
      _remember(key, profile);

      return profile;
    });
  }
}
