import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile_chrome.dart';
import 'package:xta/profile/profile_followed_by.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/reader_chrome.dart';
import 'package:xta/user.dart';

const double _avatarSize = 20;
const double _avatarOverlap = 6;
const double _avatarRing = 1.5;
const int _maxAvatars = 3;

/// The X-style "Followed by" line under a profile's counts; nothing at all
/// until a subscription is known to follow the profile.
class ProfileFollowedByLine extends StatefulWidget {
  final String profileId;
  final List<Subscription> subscriptions;
  final ProfileFollowedByStore Function()? createStore;

  const ProfileFollowedByLine({super.key, required this.profileId, required this.subscriptions, this.createStore});

  @override
  State<ProfileFollowedByLine> createState() => _ProfileFollowedByLineState();
}

class _ProfileFollowedByLineState extends State<ProfileFollowedByLine> {
  late final ProfileFollowedByStore _store = widget.createStore?.call() ?? ProfileFollowedByStore();

  @override
  void initState() {
    super.initState();
    _store.load(widget.profileId, widget.subscriptions);
  }

  @override
  void didUpdateWidget(covariant ProfileFollowedByLine oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.profileId != widget.profileId) {
      _store.load(widget.profileId, widget.subscriptions);
    }
  }

  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<ProfileFollowedByStore, ProfileFollowedBy>(
      store: _store,
      onLoading: (_) => const SizedBox.shrink(),
      onError: (_, _) => const SizedBox.shrink(),
      onState: (context, followedBy) =>
          followedBy.followers.isEmpty ? const SizedBox.shrink() : ProfileFollowedByRow(followedBy: followedBy),
    );
  }
}

class ProfileFollowedByRow extends StatelessWidget {
  final ProfileFollowedBy followedBy;

  const ProfileFollowedByRow({super.key, required this.followedBy});

  @override
  Widget build(BuildContext context) {
    final label = profileFollowedByLabel(L10n.of(context), followedBy.followers.map((member) => member.name).toList());
    return Semantics(
      button: true,
      child: InkWell(
        onTap: () => _open(context),
        borderRadius: BorderRadius.circular(kProfileControlRadius),
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
          child: Row(
            children: [
              _OverlappingAvatars(followedBy.followers),
              const SizedBox(width: kTweetSpace2),
              Expanded(
                child: Text(label, maxLines: 2, overflow: TextOverflow.ellipsis, style: tweetMetadataStyle(context)),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _open(BuildContext context) =>
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => ProfileFollowedByScreen(followedBy: followedBy)));
}

class _OverlappingAvatars extends StatelessWidget {
  final List<UserSubscription> followers;

  const _OverlappingAvatars(this.followers);

  @override
  Widget build(BuildContext context) {
    final shown = followers.take(_maxAvatars).toList();
    const step = _avatarSize + 2 * _avatarRing - _avatarOverlap;
    return ExcludeSemantics(
      child: SizedBox(
        width: step * (shown.length - 1) + _avatarSize + 2 * _avatarRing,
        height: _avatarSize + 2 * _avatarRing,
        child: Stack(
          children: [
            for (final (index, member) in shown.indexed.toList().reversed)
              PositionedDirectional(start: step * index, child: _ringed(context, member)),
          ],
        ),
      ),
    );
  }

  Widget _ringed(BuildContext context, UserSubscription member) => Container(
    padding: const EdgeInsets.all(_avatarRing),
    decoration: BoxDecoration(color: tweetSurfaceColor(context), shape: BoxShape.circle),
    child: UserAvatar(uri: member.profileImageUrlHttps, size: _avatarSize),
  );
}

/// The subscriptions behind the line, and how much the app could check.
class ProfileFollowedByScreen extends StatelessWidget {
  final ProfileFollowedBy followedBy;

  const ProfileFollowedByScreen({super.key, required this.followedBy});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return XtaSystemBars(
      child: Scaffold(
        appBar: AppBar(
          title: Text(l10n.profile_followed_by_title),
          surfaceTintColor: Colors.transparent,
          scrolledUnderElevation: 0,
        ),
        body: ListView(
          padding: EdgeInsets.only(bottom: MediaQuery.paddingOf(context).bottom),
          children: [
            for (final member in followedBy.followers) UserTile(user: member),
            Padding(
              padding: const EdgeInsets.all(kTweetHorizontalPadding),
              child: Text(
                l10n.profile_followed_by_basis(followedBy.total, followedBy.checked),
                style: tweetMetadataStyle(context),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
