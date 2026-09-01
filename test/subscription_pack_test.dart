import 'package:flutter_test/flutter_test.dart';
import 'package:xta/group/subscription_pack.dart';

void main() {
  test('round-trips a pack', () {
    const pack = SubscriptionPack(
      name: 'Tech',
      members: [
        PackMember(type: 'user', id: '783214', screenName: 'X'),
        PackMember(type: 'search', id: 'dart lang'),
      ],
    );

    final decoded = decodeSubscriptionPack(encodeSubscriptionPack(pack));

    expect(decoded.name, 'Tech');
    expect(decoded.members, hasLength(2));
    expect(decoded.members.first.screenName, 'X');
    expect(decoded.members.last.type, 'search');
  });

  test('rejects unknown format', () {
    expect(
      () => SubscriptionPack.fromJson({'format': 'other', 'v': 1, 'name': 'x', 'members': []}),
      throwsFormatException,
    );
  });

  test('rejects unknown version', () {
    expect(
      () => SubscriptionPack.fromJson({'format': 'xta-pack', 'v': 2, 'name': 'x', 'members': []}),
      throwsFormatException,
    );
  });
}
