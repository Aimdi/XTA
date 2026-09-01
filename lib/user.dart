import 'package:dart_twitter_api/src/utils/date_utils.dart';
import 'package:dart_twitter_api/twitter_api.dart';
import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/subscriptions/group_membership_sheet.dart';
import 'package:xta/subscriptions/subscription_unfollow.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:provider/provider.dart';

Widget _createUserAvatar(String? uri, double size, [int? cacheWidth]) {
  if (uri == null) {
    return SizedBox(width: size, height: size);
  } else {
    return ExtendedImage.network(
      // TODO: This can error if the profile image has changed... use SWR-like
      uri.replaceAll('normal', '200x200'),
      width: size,
      height: size,
      cacheWidth: cacheWidth,
      loadStateChanged: (state) {
        switch (state.extendedImageLoadState) {
          case LoadState.failed:
            return const Icon(Icons.error_outline);
          default:
            return state.completedWidget;
        }
      },
    );
  }
}

/*
Widget _expandUserAvatar(String? uri, double size) {
  if (uri == null) {
    return SizedBox(width: size, height: size);
  } else {
    return ExtendedImage.network(
      // TODO: This can error if the profile image has changed... use SWR-like
      uri.replaceAll('normal', '400x400'),
      width: size,
      height: size,
      loadStateChanged: (state) {
        switch (state.extendedImageLoadState) {
          case LoadState.failed:
            return const Icon(Icons.error_outline);
          default:
            return state.completedWidget;
        }
      },
    );
  }
}
*/

class UserAvatar extends StatelessWidget {
  final String? uri;
  final double size;

  const UserAvatar({super.key, required this.uri, this.size = 48});

  @override
  Widget build(BuildContext context) {
    final tokens = XLookTokens.maybeOf(context);
    final resolvedSize = size == 48 && tokens != null ? tokens.avatarSize : size;
    // Decode at the displayed size instead of the full 200x200 download, so
    // scrolling feeds don't pay for oversized bitmaps.
    final cacheWidth = (resolvedSize * MediaQuery.devicePixelRatioOf(context)).ceil();
    return ClipRRect(
      borderRadius: BorderRadius.circular(resolvedSize),
      child: _createUserAvatar(uri, resolvedSize, cacheWidth),
    );
  }
}

class UserTile extends StatelessWidget {
  final Subscription user;

  const UserTile({super.key, required this.user});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12.0)),
      leading: UserAvatar(uri: user.profileImageUrlHttps),
      title: Row(
        children: [
          Flexible(child: Text(user.name, maxLines: 1, overflow: TextOverflow.ellipsis)),
          if (user.verified) const SizedBox(width: 6),
          if (user.verified) Icon(Icons.verified, size: 14, color: Theme.of(context).colorScheme.primary)
        ],
      ),
      subtitle: Text('@${user.screenName}', maxLines: 1, overflow: TextOverflow.ellipsis),
      trailing: FollowButton(user: user),
      onTap: () {
        Navigator.pushNamed(context, routeProfile, arguments: ProfileScreenArguments(user.id, user.screenName, null));
      },
    );
  }
}

/// Asks which groups [user] belongs to, and saves the answer.
///
/// Follows them first when they are not followed yet: adding someone to a group
/// without following them would put a feed together out of accounts the app is
/// not watching.
Future<void> pickUserGroups(
  BuildContext context, {
  required Subscription user,
  required bool followed,
  required List<String> groupsForUser,
}) async {
  final groupsModel = context.read<GroupsModel>();
  final subscriptionsModel = context.read<SubscriptionsModel>();

  final chosen = await showGroupMembershipSheet(
    context,
    groups: groupsModel.state,
    selected: groupsForUser,
  );

  if (chosen == null) {
    return;
  }

  if (!followed) {
    await subscriptionsModel.toggleSubscribe(user, followed);
  }
  await groupsModel.saveUserGroupMembership(user.id, chosen);
}

class FollowButton extends StatelessWidget {
  final Subscription user;
  final Color? color;

  const FollowButton({super.key, required this.user, this.color});

  @override
  Widget build(BuildContext context) {
    var model = context.read<SubscriptionsModel>();

    return ScopedBuilder<SubscriptionsModel, List<Subscription>>(
      store: model,
      onState: (_, state) {
        var followed = state.any((element) => element.id == user.id);
        var inFeed = followed ? state.any((element) => element.id == user.id && element.inFeed) : false;

        var icon = followed
            ? (inFeed ? Icon(Icons.person_remove, color: color) : Icon(Icons.visibility_off))
            : Icon(Icons.person_add, color: color);
        var text = followed ? L10n.of(context).unsubscribe : L10n.of(context).subscribe;

        return PopupMenuButton<String>(
          icon: icon,
          itemBuilder: (context) => [
            PopupMenuItem(value: 'toggle_subscribe', child: Text(text)),
            PopupMenuItem(
              value: 'add_to_group',
              child: Text(L10n.of(context).add_to_group),
            ),
            // Only X accounts: whether a plugin's posts join the home timeline
            // is that plugin's own setting, so offering it here would be a
            // switch that flips nothing.
            if (followed && user is UserSubscription)
              PopupMenuItem(
                value: 'toggle_in_main_feed',
                child: Text(inFeed ? L10n.of(context).hide_from_main_feed : L10n.of(context).show_in_main_feed),
              ),
          ],
          onSelected: (value) async {
            switch (value) {
              case 'add_to_group':
                var groups = await context.read<GroupsModel>().listGroupsForUser(user.id);
                if (context.mounted) {
                  await pickUserGroups(context,
                      user: user, followed: followed, groupsForUser: groups);
                }
                break;
              case 'toggle_subscribe':
                // A plugin's own store has to do its own removals, or its tab
                // would go on listing what was just unfollowed. Unsubscribing
                // one of those used to do nothing at all.
                if (!followed || !await unfollowSubscription(context, user)) {
                  await model.toggleSubscribe(user, followed);
                  break;
                }
                await model.reloadSubscriptions();
                break;
              case 'toggle_in_main_feed':
                await model.toggleInFeed(user, inFeed);
                break;
            }
          },
        );
      },
    );
  }
}

class UserWithExtra extends User {
  Map<String, dynamic>? card;
  bool? possiblySensitive;

  UserWithExtra();

  factory UserWithExtra.fromArguments({
    String? idStr,
    String? name,
    String? screenName,
    String? location,
    Derived? derived,
    String? url,
    UserEntities? entities,
    String? description,
    bool? protected,
    bool? verified,
    Tweet? status,
    int? followersCount,
    int? friendsCount,
    int? listedCount,
    int? favoritesCount,
    int? statusesCount,
    DateTime? createdAt,
    String? profileBannerUrl,
    String? profileImageUrlHttps,
    bool? defaultProfile,
    bool? defaultProfileImage,
    List<String>? withheldInCountries,
    String? withheldScope,
    bool? possiblySensitive,
  }) {
    var userWithExtra = UserWithExtra()
      ..idStr = idStr
      ..name = name
      ..screenName = screenName
      ..location = location
      ..derived = derived
      ..url = url
      ..entities = entities
      ..description = description
      ..protected = protected
      ..verified = verified
      ..status = status
      ..followersCount = followersCount
      ..friendsCount = friendsCount
      ..listedCount = listedCount
      ..favoritesCount = favoritesCount
      ..statusesCount = statusesCount
      ..createdAt = createdAt
      ..profileBannerUrl = profileBannerUrl
      ..profileImageUrlHttps = profileImageUrlHttps
      ..defaultProfile = defaultProfile
      ..defaultProfileImage = defaultProfileImage
      ..withheldInCountries = withheldInCountries
      ..withheldScope = withheldScope
      ..possiblySensitive = possiblySensitive;

    return userWithExtra;
  }

  @override
  Map<String, dynamic> toJson() {
    var json = super.toJson();
    json['potentiallySensitive'] = possiblySensitive;

    return json;
  }

  factory UserWithExtra.fromNonLegacyJson(Map<String, dynamic> json) {
    // X keeps moving profile fields out of `legacy` and into `core` / `avatar`.
    // A response that has finished that migration must still yield a usable
    // profile, so an absent `legacy` degrades to whatever the rest carries
    // rather than throwing and taking the whole screen down.
    var userWithExtra = UserWithExtra.fromJson((json["legacy"] as Map<String, dynamic>?) ?? const <String, dynamic>{});
    userWithExtra
      ..name = json["core"]?["name"] ?? userWithExtra.name
      ..createdAt = convertTwitterDateTime(json["core"]?["created_at"]) ?? userWithExtra.createdAt
      ..screenName = json["core"]?["screen_name"] ?? userWithExtra.screenName
      ..verified = json["is_blue_verified"] ?? userWithExtra.verified
      ..profileImageUrlHttps = json["avatar"]?["image_url"] ?? userWithExtra.profileImageUrlHttps
      ..idStr = json["rest_id"] ?? userWithExtra.idStr;
    return userWithExtra;
  }

  factory UserWithExtra.fromJson(Map<String, dynamic> json) {
    var userWithExtra = UserWithExtra()
      ..idStr = json['id_str'] as String?
      ..name = json['name'] as String?
      ..screenName = json['screen_name'] as String?
      ..location = json['location'] as String?
      ..derived = json['derived'] == null ? null : Derived.fromJson(json['derived'] as Map<String, dynamic>)
      ..url = json['url'] as String?
      ..entities = json['entities'] == null ? null : UserEntities.fromJson(json['entities'] as Map<String, dynamic>)
      ..description = json['description'] as String?
      ..protected = json['protected'] as bool?
      ..verified = json['verified_type'] == "Business"
          ? true
          : json['ext_is_blue_verified'] ?? json['verified'] ?? json['is_blue_verified'] as bool?
      ..status = json['status'] == null ? null : Tweet.fromJson(json['status'] as Map<String, dynamic>)
      ..followersCount = json['followers_count'] as int?
      ..friendsCount = json['friends_count'] as int?
      ..listedCount = json['listed_count'] as int?
      ..favoritesCount = json['favorites_count'] as int?
      ..statusesCount = json['statuses_count'] as int?
      ..createdAt = convertTwitterDateTime(json['created_at'] as String?)
      ..profileBannerUrl = json['profile_banner_url'] as String?
      ..profileImageUrlHttps = json['profile_image_url_https'] as String?
      ..defaultProfile = json['default_profile'] as bool?
      ..defaultProfileImage = json['default_profile_image'] as bool?
      ..withheldInCountries = (json['withheld_in_countries'] as List<dynamic>?)?.map((e) => e as String).toList()
      ..withheldScope = json['withheld_scope'] as String?;

    userWithExtra.possiblySensitive = json['possibly_sensitive'] as bool?;

    return userWithExtra;
  }
}
