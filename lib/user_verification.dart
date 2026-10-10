/// The badges X draws after a display name: the check and, for accounts that
/// belong to an organisation, that organisation's small square avatar.
///
/// Read from a GraphQL user result. X has been moving these fields between the
/// result, its `legacy` map and a newer `verification` object, so every known
/// location is tried and any of them may be missing.
library;

import 'package:xta/utils/json.dart';

/// Which check is shown. A legacy `verified: true` with no type is drawn blue,
/// as the app always has: X no longer sends it for anyone it shows unchecked.
enum VerifiedType { none, blue, business, government }

/// X's `userLabelType` for the "Automated" bot label, which X renders as text
/// under the name rather than as an affiliation badge.
const _automatedLabelType = 'AutomatedLabel';

class Affiliation {
  final String badgeUrl;
  final String? name;
  final String? profileUrl;
  final String? labelType;

  const Affiliation({required this.badgeUrl, this.name, this.profileUrl, this.labelType});

  /// From `affiliates_highlighted_label.label`. Null without a badge image,
  /// since there is nothing to draw, and for the bot label.
  static Affiliation? fromLabel(Json label) {
    final badgeUrl = label['badge']['url'].string;
    final labelType = label['userLabelType'].string;
    if (badgeUrl == null || badgeUrl.isEmpty || labelType == _automatedLabelType) {
      return null;
    }
    return Affiliation(
      badgeUrl: badgeUrl,
      name: label['description'].string,
      profileUrl: label['url']['url'].string,
      labelType: labelType,
    );
  }

  /// The same shape [fromLabel] reads, so a cached user parses back unchanged.
  Map<String, dynamic> toLabelJson() => {
    'badge': {'url': badgeUrl},
    'description': name,
    'url': {'url': profileUrl},
    'userLabelType': labelType,
  };
}

class UserVerification {
  final VerifiedType type;
  final Affiliation? affiliation;

  const UserVerification(this.type, [this.affiliation]);

  static const none = UserVerification(VerifiedType.none);

  bool get hasBadges => type != VerifiedType.none || affiliation != null;

  /// For a user known only by the stored `verified` flag.
  factory UserVerification.fromFlag(bool? verified) =>
      verified == true ? const UserVerification(VerifiedType.blue) : none;

  /// From a GraphQL user result, or a `legacy`-shaped map such as the one
  /// [toJson] writes into a cached user.
  factory UserVerification.fromJson(Object? user) {
    final json = Json(user);
    return UserVerification(_verifiedType(json), _affiliation(json));
  }

  /// Keys merged into a serialized user, in the shape [fromJson] reads.
  Map<String, dynamic> toJson() => {
    'verified_type': switch (type) {
      VerifiedType.business => _business,
      VerifiedType.government => _government,
      _ => null,
    },
    'is_blue_verified': type == VerifiedType.blue,
    if (affiliation != null) 'affiliates_highlighted_label': {'label': affiliation!.toLabelJson()},
  };
}

const _business = 'Business';
const _government = 'Government';

VerifiedType _verifiedType(Json user) {
  final type = [
    user['verification']['verified_type'],
    user['legacy']['verified_type'],
    user['verified_type'],
  ].map((field) => field.string).nonNulls.firstOrNull;
  return switch (type) {
    _business => VerifiedType.business,
    _government => VerifiedType.government,
    _ => _isBlue(user) ? VerifiedType.blue : VerifiedType.none,
  };
}

bool _isBlue(Json user) => [
  user['is_blue_verified'],
  user['ext_is_blue_verified'],
  user['legacy']['ext_is_blue_verified'],
  user['verification']['verified'],
  user['legacy']['verified'],
  user['verified'],
].any((field) => field.boolean == true);

Affiliation? _affiliation(Json user) => [
  user['affiliates_highlighted_label']['label'],
  user['legacy']['affiliates_highlighted_label']['label'],
].map(Affiliation.fromLabel).nonNulls.firstOrNull;
