/// Who the group's X members follow, remembered for days.
///
/// One `Following` page per member costs a request from a generous bucket and
/// barely changes week to week, so it is kept across launches in the reader
/// state sidecar instead of being asked again every time Discover opens.
library;

import 'package:dart_twitter_api/twitter_api.dart' show User;
import 'package:xta/client/client.dart';
import 'package:xta/group/future_pool.dart';
import 'package:xta/plugins/account_posts.dart';
import 'package:xta/utils/local_json_store.dart';

const String discoveryFollowsPrefix = 'discovery:follows:';
const Duration discoveryFollowsTtl = Duration(days: 3);

/// How long a read is still trusted for who-follows-whom after it stops being
/// fresh enough for Discover: a following list barely changes in a month.
const Duration discoveryFollowsRetention = Duration(days: 30);

class DiscoveryFollow {
  final String id;
  final String handle;
  final String name;
  final String? avatarUrl;
  final String? bio;

  const DiscoveryFollow({required this.id, required this.handle, required this.name, this.avatarUrl, this.bio});

  Map<String, Object?> toJson() => {'id': id, 'handle': handle, 'name': name, 'avatar': avatarUrl, 'bio': bio};

  static DiscoveryFollow? fromJson(Object? json) {
    if (json is! Map || json['id'] is! String) return null;
    return DiscoveryFollow(
      id: json['id'] as String,
      handle: json['handle'] is String ? json['handle'] as String : '',
      name: json['name'] is String ? json['name'] as String : '',
      avatarUrl: json['avatar'] is String ? json['avatar'] as String : null,
      bio: json['bio'] is String ? json['bio'] as String : null,
    );
  }
}

typedef DiscoveryFollowsByMember = Map<String, List<DiscoveryFollow>>;

/// What one read produced: follows per member read, and the failure that cut
/// the sample short, if any. Members that failed are simply absent.
typedef DiscoveryFollowsResult = ({DiscoveryFollowsByMember follows, Object? error});

typedef DiscoveryFollowsFetch = Future<List<DiscoveryFollow>> Function(String memberId);

/// One member's following list as last read, newest follow first.
typedef RememberedFollows = ({DateTime at, List<DiscoveryFollow> follows});

class DiscoveryFollowsCache {
  static final shared = DiscoveryFollowsCache();

  final JsonStore storage;
  final DiscoveryFollowsFetch fetch;
  final Duration ttl;
  final Duration retention;
  final DateTime Function() now;
  final Map<String, RememberedFollows> _memory = {};
  Future<void>? _restored;

  DiscoveryFollowsCache({
    JsonStore? storage,
    this.fetch = fetchXFollowing,
    this.ttl = discoveryFollowsTtl,
    this.retention = discoveryFollowsRetention,
    this.now = DateTime.now,
  }) : storage = storage ?? LocalJsonStore.shared;

  bool _fresh(String member) {
    final entry = _memory[member];
    return entry != null && now().difference(entry.at) <= ttl;
  }

  /// [members] never read first, then the longest unread; ties keep their order.
  List<String> prioritize(List<String> members) => prioritizeByAttempt(members, (member) => _memory[member]?.at);

  /// Follows of [members]: everything still remembered, plus X asked for up to
  /// [maxFetches] of the members whose answer is missing or old.
  Future<DiscoveryFollowsResult> read(
    List<String> members, {
    required int maxFetches,
    Duration? timeout,
    void Function(DiscoveryFollowsByMember soFar)? onPartial,
  }) async {
    await (_restored ??= _restore());
    final ordered = prioritize(members);
    final pending = ordered.where((member) => !_fresh(member)).take(maxFetches).toList();
    Object? error;
    await mapWithConcurrency(pending, 2, (member) async {
      try {
        await _refresh(member, timeout);
        onPartial?.call(_known(ordered));
      } catch (e) {
        error = e;
      }
    });
    return (follows: _known(ordered), error: error);
  }

  /// Every member's follows read within [retention], without asking X.
  Future<Map<String, RememberedFollows>> remembered() async {
    await (_restored ??= _restore());
    return {
      for (final entry in _memory.entries)
        if (now().difference(entry.value.at) <= retention) entry.key: entry.value,
    };
  }

  /// Keeps a following list the app read anyway, so it counts as this member's latest read.
  Future<void> remember(String member, List<DiscoveryFollow> follows) async {
    await (_restored ??= _restore());
    final at = now();
    _memory[member] = (at: at, follows: follows);
    await storage.write('$discoveryFollowsPrefix$member', {
      'at': at.toIso8601String(),
      'users': [for (final follow in follows) follow.toJson()],
    });
  }

  DiscoveryFollowsByMember _known(List<String> members) => {
    for (final member in members)
      if (_memory[member] case final entry?) member: entry.follows,
  };

  Future<void> _refresh(String member, Duration? timeout) async {
    final request = fetch(member);
    await remember(member, await (timeout == null ? request : request.timeout(timeout)));
  }

  Future<void> _restore() async {
    final stored = await storage.readPrefix(discoveryFollowsPrefix);
    for (final entry in stored.entries) {
      final value = entry.value;
      if (value is! Map || value['users'] is! List) continue;
      final at = DateTime.tryParse('${value['at']}');
      if (at == null || now().difference(at) > retention) continue;
      final follows = (value['users'] as List).map(DiscoveryFollow.fromJson).whereType<DiscoveryFollow>().toList();
      _memory.putIfAbsent(entry.key.substring(discoveryFollowsPrefix.length), () => (at: at, follows: follows));
    }
  }
}

/// One page of who [memberId] follows, through the registered `Following` operation.
Future<List<DiscoveryFollow>> fetchXFollowing(String memberId) async {
  final page = await Twitter.friendsList(memberId, 100);
  return discoveryFollowsOf(page.users ?? const []);
}

/// X users as remembered follows, in the order X listed them; users without an id are dropped.
List<DiscoveryFollow> discoveryFollowsOf(Iterable<User> users) => [
  for (final user in users)
    if (user.idStr case final String id when id.isNotEmpty)
      DiscoveryFollow(
        id: id,
        handle: user.screenName ?? '',
        name: user.name ?? '',
        avatarUrl: user.profileImageUrlHttps,
        bio: user.description,
      ),
];
