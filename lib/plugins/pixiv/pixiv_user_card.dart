import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

const pixivPreviewWorks = 3;

typedef PixivFollowState = ({bool followed, bool busy});

/// Whether the reader follows one creator, and whether a change is on its way.
class PixivFollowStore extends Store<PixivFollowState> {
  final PixivClient client;
  final int userId;

  var _closed = false;

  PixivFollowStore(this.client, this.userId, {required bool followed}) : super((followed: followed, busy: false));

  /// Follows publicly or unfollows; a failure puts the button back and rethrows.
  Future<void> toggle() async {
    if (state.busy) return;
    final was = state.followed;
    _set((followed: was, busy: true));
    try {
      await (was ? client.unfollowUser(userId) : client.followUser(userId));
      _set((followed: !was, busy: false));
    } catch (_) {
      _set((followed: was, busy: false));
      rethrow;
    }
  }

  /// A follow still on its way when the button goes away lands nowhere.
  void _set(PixivFollowState next) {
    if (!_closed) update(next);
  }

  @override
  Future<void> destroy() {
    _closed = true;
    return super.destroy();
  }
}

/// The works a preview row may show: none the reader muted, and none their
/// Show R-18 / Hide AI choices keep out of the feeds — dropped, not blurred.
List<PixivIllust> pixivVisiblePreviewWorks(
  List<PixivIllust> works, {
  required PixivMuteState mute,
  required bool showR18,
  required bool hideAi,
}) => [
  for (final work in mute.filter(works))
    if ((showR18 || !work.isR18) && !(hideAi && work.isAi)) work,
].take(pixivPreviewWorks).toList();

/// Follow / Unfollow for one creator, public follows only, with a 48dp target
/// and a spinner while Pixiv answers.
class PixivFollowButton extends StatefulWidget {
  final PixivUser user;
  final ValueChanged<bool>? onChanged;

  const PixivFollowButton({super.key, required this.user, this.onChanged});

  @override
  State<PixivFollowButton> createState() => _PixivFollowButtonState();
}

class _PixivFollowButtonState extends State<PixivFollowButton> {
  late PixivFollowStore _store;

  @override
  void initState() {
    super.initState();
    _store = _storeFor(widget.user);
  }

  PixivFollowStore _storeFor(PixivUser user) =>
      PixivFollowStore(context.read<PixivClient>(), user.id, followed: user.isFollowed);

  /// A new creator, or a reload that says otherwise about this one, starts over.
  @override
  void didUpdateWidget(covariant PixivFollowButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    final state = _store.state;
    if (oldWidget.user.id != widget.user.id || (!state.busy && state.followed != widget.user.isFollowed)) {
      _store.destroy();
      _store = _storeFor(widget.user);
    }
  }

  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  Future<void> _toggle() async {
    final l10n = L10n.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final store = _store;
    try {
      await store.toggle();
      if (mounted) widget.onChanged?.call(store.state.followed);
    } catch (error) {
      messenger.showSnackBar(SnackBar(content: Text(pixivErrorMessage(l10n, error))));
    }
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<PixivFollowStore, PixivFollowState>(
    key: ObjectKey(_store),
    store: _store,
    onState: (context, state) => _button(context, state),
  );

  Widget _button(BuildContext context, PixivFollowState state) {
    final l10n = L10n.of(context);
    final icon = state.busy
        ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
        : Icon(state.followed ? Icons.person_remove_outlined : Icons.person_add_alt_1_outlined);
    return ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 180),
      child: FilledButton.tonalIcon(
        key: ValueKey('pixiv-follow-${widget.user.id}'),
        style: FilledButton.styleFrom(minimumSize: const Size(kMinInteractiveDimension, kMinInteractiveDimension)),
        onPressed: state.busy ? null : _toggle,
        icon: icon,
        label: Text(
          state.followed ? l10n.plugin_pixiv_unfollow : l10n.plugin_pixiv_follow,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
      ),
    );
  }
}

/// A creator as Pixiv previews them in recommendations, search and follow
/// lists: avatar, name, @account, three recent works and a follow button.
class PixivUserPreviewCard extends StatelessWidget {
  final PixivUserPreview preview;

  const PixivUserPreviewCard({super.key, required this.preview});

  @override
  Widget build(BuildContext context) {
    final user = preview.user;
    return Card.outlined(
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        key: ValueKey('pixiv-user-card-${user.id}'),
        onTap: () => openPixivUser(context, user.id),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              _header(context, user),
              ScopedBuilder<PixivMuteStore, PixivMuteState>(
                store: context.read<PixivMuteStore>(),
                onState: (context, mute) => _works(context, _visibleWorks(context, mute)),
              ),
            ],
          ),
        ),
      ),
    );
  }

  List<PixivIllust> _visibleWorks(BuildContext context, PixivMuteState mute) {
    final client = context.read<PixivClient>();
    return pixivVisiblePreviewWorks(preview.illusts, mute: mute, showR18: client.showR18, hideAi: client.hideAi);
  }

  Widget _header(BuildContext context, PixivUser user) {
    final theme = Theme.of(context);
    return Row(
      children: [
        _avatar(context, user),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                user.name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.titleSmall!.copyWith(fontWeight: FontWeight.w700),
              ),
              Text(
                '@${user.account}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
            ],
          ),
        ),
        const SizedBox(width: 8),
        PixivFollowButton(user: user),
      ],
    );
  }

  Widget _avatar(BuildContext context, PixivUser user) {
    const size = 40.0;
    final avatar = user.avatarUrl;
    final pixels = (size * MediaQuery.devicePixelRatioOf(context)).ceil();
    return ClipOval(
      child: avatar == null
          ? FallbackAvatar(
              seed: '${user.id}',
              displayName: user.name,
              size: size,
              accent: Theme.of(context).colorScheme.primary,
            )
          : SizedBox.square(
              dimension: size,
              child: PixivNetworkImage(url: avatar, fit: BoxFit.cover, cacheWidth: pixels, cacheHeight: pixels),
            ),
    );
  }

  Widget _works(BuildContext context, List<PixivIllust> works) {
    if (works.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Row(
        spacing: 4,
        children: [
          for (var index = 0; index < pixivPreviewWorks; index++)
            Expanded(
              child: AspectRatio(
                aspectRatio: 1,
                child: index < works.length ? _work(context, works, index) : const SizedBox.shrink(),
              ),
            ),
        ],
      ),
    );
  }

  Widget _work(BuildContext context, List<PixivIllust> works, int index) {
    final work = works[index];
    return Semantics(
      button: true,
      label: work.title,
      excludeSemantics: true,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: Stack(
          fit: StackFit.expand,
          children: [
            ColoredBox(color: Theme.of(context).colorScheme.surfaceContainerHighest),
            PixivNetworkImage(url: work.thumbnailUrl, fit: BoxFit.cover),
            Material(
              type: MaterialType.transparency,
              child: InkWell(
                key: ValueKey('pixiv-user-card-work-${work.id}'),
                onTap: () => openPixivIllustFromList(context, works, index),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
