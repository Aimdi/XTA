import 'dart:async';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/client/client.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/local_json_store.dart';

class Profile {
  final UserWithExtra user;
  final List<String> pinnedTweets;
  final bool refreshing;
  final Object? refreshError;
  final DateTime? cachedAt;
  Profile(this.user, this.pinnedTweets, {this.refreshing = false, this.refreshError, this.cachedAt});
  Profile status({bool refreshing = false, Object? error, DateTime? cachedAt}) =>
      Profile(user, pinnedTweets, refreshing: refreshing, refreshError: error, cachedAt: cachedAt);
}

const profileCacheMaxAge = Duration(minutes: 5);

class ProfileModel extends Store<Profile> {
  final JsonStore storage;
  final Future<Profile> Function(String) byId;
  final Future<Profile> Function(String) byName;
  int _generation = 0;
  bool _closed = false;
  ProfileModel({JsonStore? storage, Future<Profile> Function(String)? byId, Future<Profile> Function(String)? byName})
    : storage = storage ?? LocalJsonStore.shared,
      byId = byId ?? Twitter.getProfileById,
      byName = byName ?? Twitter.getProfileByScreenName,
      super(Profile(UserWithExtra(), []));

  @override
  dynamic get error => triple.error;

  Future<void> loadProfileById(String id) => _load('id:$id', () => byId(id));
  Future<void> loadProfileByScreenName(String name) =>
      _load('name:${name.replaceFirst('@', '').toLowerCase()}', () => byName(name));

  Future<void> _load(String key, Future<Profile> Function() fetch) async {
    final generation = ++_generation;
    bool current() => !_closed && generation == _generation;
    if (state.user.idStr == null) setLoading(true);
    final cached = await _read(key);
    if (!current()) return;
    if (cached != null) {
      update(cached.status(refreshing: true, cachedAt: cached.cachedAt), force: true);
      setLoading(false);
    }
    try {
      final profile = await fetch().timeout(const Duration(seconds: 30));
      if (!current()) return;
      update(profile, force: true);
      unawaited(_remember(profile));
    } catch (error) {
      if (!current()) return;
      if (state.user.idStr != null) {
        update(state.status(error: error, cachedAt: state.cachedAt), force: true);
      } else {
        setError(error, force: true);
      }
    } finally {
      if (current()) setLoading(false);
    }
  }

  Future<Profile?> _read(String key) async {
    try {
      final raw = await storage.read('profile:$key');
      if (raw is! Map || raw['user'] is! Map) return null;
      final at = DateTime.tryParse('${raw['at']}');
      if (at == null || DateTime.now().difference(at) > const Duration(days: 7)) return null;
      return Profile(
        UserWithExtra.fromJson(Map<String, dynamic>.from(raw['user'] as Map)),
        (raw['pinned'] as List? ?? []).whereType<String>().toList(),
        cachedAt: at,
      );
    } catch (_) {
      return null;
    }
  }

  Future<void> _remember(Profile profile) async {
    try {
      final user = profile.user;
      final data = {
        'at': DateTime.now().toIso8601String(),
        'pinned': profile.pinnedTweets,
        'user': {...user.toJson(), 'possibly_sensitive': user.possiblySensitive},
      };
      if (user.idStr != null) await storage.write('profile:id:${user.idStr}', data);
      if (user.screenName != null) await storage.write('profile:name:${user.screenName!.toLowerCase()}', data);
      final all = await storage.readPrefix('profile:');
      final keys = all.keys.toList()
        ..sort((a, b) => '${(all[a] as Map?)?['at']}'.compareTo('${(all[b] as Map?)?['at']}'));
      for (final key in keys.take((keys.length - 100).clamp(0, keys.length))) {
        await storage.remove(key);
      }
    } catch (_) {
      /* Caching must not turn a successful request into a failure. */
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _generation++;
    return super.destroy();
  }
}
