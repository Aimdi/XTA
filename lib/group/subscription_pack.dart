import 'dart:convert';

import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/users_model.dart';

/// One member of an exported subscription pack.
class PackMember {
  final String type;
  final String id;
  final String? screenName;

  const PackMember({required this.type, required this.id, this.screenName});

  Map<String, dynamic> toJson() => {'type': type, 'id': id, if (screenName != null) 'screen_name': screenName};

  factory PackMember.fromJson(Map<String, dynamic> json) => PackMember(
    type: (json['type'] as String?) ?? '',
    id: (json['id'] as String?) ?? '',
    screenName: json['screen_name'] as String?,
  );
}

/// Portable export of one group's members — no credentials.
class SubscriptionPack {
  static const format = 'xta-pack';
  static const version = 1;

  final String name;
  final List<PackMember> members;

  const SubscriptionPack({required this.name, required this.members});

  Map<String, dynamic> toJson() => {
    'format': format,
    'v': version,
    'name': name,
    'members': members.map((member) => member.toJson()).toList(growable: false),
  };

  factory SubscriptionPack.fromJson(Map<String, dynamic> json) {
    if (json['format'] != format) {
      throw FormatException('unsupported pack format: ${json['format']}');
    }
    final packVersion = json['v'] as int?;
    if (packVersion != version) {
      throw FormatException('unsupported pack version: $packVersion');
    }

    final members = (json['members'] as List<dynamic>? ?? const [])
        .whereType<Map>()
        .map((row) => PackMember.fromJson(Map<String, dynamic>.from(row)))
        .toList(growable: false);

    return SubscriptionPack(name: (json['name'] as String?) ?? '', members: members);
  }
}

String encodeSubscriptionPack(SubscriptionPack pack) => jsonEncode(pack.toJson());

SubscriptionPack decodeSubscriptionPack(String raw) =>
    SubscriptionPack.fromJson(jsonDecode(raw) as Map<String, dynamic>);

PackMember? packMemberFrom(Subscription subscription) => switch (subscription) {
  UserSubscription(:final id, :final screenName) => PackMember(type: 'user', id: id, screenName: screenName),
  SearchSubscription(:final id) => PackMember(type: 'search', id: id),
  _ => null,
};

SubscriptionPack packFromSubscriptions(String name, Iterable<Subscription> subscriptions) {
  final members = subscriptions.map(packMemberFrom).whereType<PackMember>().toList(growable: false);
  return SubscriptionPack(name: name, members: members);
}

/// Creates missing subscription rows, then a new group holding every pack member.
Future<int> importSubscriptionPack(SubscriptionPack pack, GroupsModel groups, SubscriptionsModel subscriptions) async {
  final database = await Repository.writable();
  final memberIds = <String>{};

  for (final member in pack.members) {
    switch (member.type) {
      case 'user':
        final exists = subscriptions.state.any((s) => s.id == member.id);
        if (!exists) {
          await database.insert(tableSubscription, {
            'id': member.id,
            'screen_name': member.screenName ?? member.id,
            'name': member.screenName ?? member.id,
            'profile_image_url_https': null,
            'verified': 0,
          });
        }
        memberIds.add(member.id);
      case 'search':
        final exists = subscriptions.state.any((s) => s.id == member.id);
        if (!exists) {
          await database.insert(tableSearchSubscription, {'id': member.id});
        }
        memberIds.add(member.id);
      default:
        continue;
    }
  }

  await subscriptions.reloadSubscriptions();
  await groups.saveGroup(null, pack.name, defaultGroupIcon, null, memberIds);
  return memberIds.length;
}
