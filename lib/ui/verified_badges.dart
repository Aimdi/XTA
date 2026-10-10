import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/user_verification.dart';
import 'package:xta/utils/urls.dart';

const _businessGold = Color(0xFFE2B719);
const _governmentGrey = Color(0xFF829AAB);

/// The check and the affiliation avatar X shows after a name, scaled with the
/// text. Nothing at all for an account with neither.
///
/// Placed after a `Flexible` name in a row, the name ellipsizes and the badges
/// keep their size. [gap] separates the badges, and precedes the first one
/// unless [leadingGap] is false (for a parent that spaces its children).
class VerifiedBadges extends StatelessWidget {
  final UserVerification verification;
  final double size;
  final double gap;
  final bool leadingGap;

  const VerifiedBadges({super.key, required this.verification, this.size = 18, this.gap = 4, this.leadingGap = true});

  @override
  Widget build(BuildContext context) {
    final scaled = MediaQuery.textScalerOf(context).scale(size);
    final affiliation = verification.affiliation;
    final badges = [
      if (verification.type != VerifiedType.none) VerifiedCheck(type: verification.type, size: scaled),
      if (affiliation != null) AffiliationBadge(affiliation: affiliation, size: scaled),
    ];
    if (badges.isEmpty) {
      return const SizedBox.shrink();
    }
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        for (final (index, badge) in badges.indexed) ...[if (index > 0 || leadingGap) SizedBox(width: gap), badge],
      ],
    );
  }
}

class VerifiedCheck extends StatelessWidget {
  final VerifiedType type;
  final double size;

  const VerifiedCheck({super.key, required this.type, required this.size});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.maybeOf(context);
    final (color, label) = switch (type) {
      VerifiedType.business => (_businessGold, l10n?.verified_organization),
      VerifiedType.government => (_governmentGrey, l10n?.verified_government),
      _ => (Theme.of(context).colorScheme.primary, l10n?.verified_account),
    };
    return Icon(Icons.verified, size: size, color: color, semanticLabel: label);
  }
}

/// The organisation's avatar as a small rounded square. Opens that
/// organisation's profile when its link is an X profile.
class AffiliationBadge extends StatelessWidget {
  final Affiliation affiliation;
  final double size;

  const AffiliationBadge({super.key, required this.affiliation, required this.size});

  @override
  Widget build(BuildContext context) {
    final screenName = xProfileScreenName(affiliation.profileUrl);
    final name = affiliation.name ?? screenName;
    final badge = GestureDetector(
      onTap: screenName == null ? null : () => _openProfile(context, screenName),
      behavior: HitTestBehavior.opaque,
      // Sized here, not by the image, so a badge that fails to load neither
      // shifts the row nor stops being a tap target.
      child: SizedBox.square(dimension: size, child: _image(context)),
    );
    return Semantics(
      label: name == null ? null : L10n.maybeOf(context)?.affiliated_with(name),
      button: screenName != null,
      image: true,
      excludeSemantics: true,
      child: name == null ? badge : Tooltip(message: name, excludeFromSemantics: true, child: badge),
    );
  }

  Widget _image(BuildContext context) {
    return ExtendedImage.network(
      affiliation.badgeUrl,
      width: size,
      height: size,
      fit: BoxFit.cover,
      cacheWidth: (size * MediaQuery.devicePixelRatioOf(context)).ceil(),
      shape: BoxShape.rectangle,
      borderRadius: BorderRadius.circular(3),
      border: Border.all(color: Theme.of(context).colorScheme.outlineVariant),
      loadStateChanged: (state) => switch (state.extendedImageLoadState) {
        LoadState.failed => const SizedBox.shrink(),
        _ => null,
      },
    );
  }

  void _openProfile(BuildContext context, String screenName) {
    Navigator.pushNamed(context, routeProfile, arguments: ProfileScreenArguments(null, screenName, null));
  }
}
