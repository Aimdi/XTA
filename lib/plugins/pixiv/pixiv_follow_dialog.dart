import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_social_api.dart';
import 'package:xta/plugins/pixiv/pixiv_user_profile.dart';
import 'package:xta/plugins/pixiv/pixiv_user_store.dart';

/// What the follow dialog was closed with: follow with [restrict], or unfollow.
typedef PixivFollowDecision = ({bool follow, String restrict});

/// The follow as Pixiv reports it, and where the Private switch now stands.
typedef PixivFollowDraft = ({PixivFollowDetail detail, bool private});

/// The follow dialog's state: the follow detail once loaded, then the switch.
class PixivFollowDraftStore extends Store<PixivFollowDraft?> {
  final Future<PixivFollowDetail> Function() loadDetail;

  PixivFollowDraftStore(this.loadDetail) : super(null);

  /// A creator not yet followed starts on Private: the dialog is how a reader
  /// asks for a private follow, since a plain tap already follows publicly.
  Future<void> load() => execute(() async {
    final detail = await loadDetail();
    return (detail: detail, private: detail.isFollowed ? detail.isPrivate : true);
  });

  void setPrivate(bool private) {
    final draft = state;
    if (draft != null) update((detail: draft.detail, private: private));
  }
}

/// Asks how to follow [user] and applies the answer through the app-wide
/// [PixivFollowStore]; [onChanged] hears whether the reader now follows.
Future<void> editPixivFollow(BuildContext context, PixivUser user, {ValueChanged<bool>? onChanged}) async {
  final follows = context.read<PixivFollowStore>();
  final api = PixivSocialApi.of(context);
  final messenger = ScaffoldMessenger.of(context);
  final l10n = L10n.of(context);
  final decision = await showDialog<PixivFollowDecision>(
    context: context,
    builder: (_) => PixivFollowDialog(user: user, loadDetail: () => api.followDetail(user.id)),
  );
  if (decision == null) return;
  try {
    final followed = decision.follow
        ? await follows.follow(user, restrict: decision.restrict)
        : await follows.unfollow(user);
    onChanged?.call(followed);
  } catch (error) {
    messenger.showSnackBar(SnackBar(content: Text(pixivErrorMessage(l10n, error))));
  }
}

/// Follow privately or publicly, change an existing follow, or unfollow.
class PixivFollowDialog extends StatefulWidget {
  final PixivUser user;
  final Future<PixivFollowDetail> Function() loadDetail;

  const PixivFollowDialog({super.key, required this.user, required this.loadDetail});

  @override
  State<PixivFollowDialog> createState() => _PixivFollowDialogState();
}

class _PixivFollowDialogState extends State<PixivFollowDialog> {
  late final PixivFollowDraftStore _draft = PixivFollowDraftStore(widget.loadDetail)..load();

  @override
  void dispose() {
    _draft.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => TripleBuilder<PixivFollowDraftStore, PixivFollowDraft?>(
    store: _draft,
    builder: (context, triple) {
      final l10n = L10n.of(context);
      final draft = triple.isLoading ? null : triple.state;
      return AlertDialog(
        key: const ValueKey('pixiv-follow-dialog'),
        title: Text(widget.user.name, maxLines: 2, overflow: TextOverflow.ellipsis),
        content: _content(context, draft, triple.isLoading ? null : triple.error),
        actions: [
          if (draft?.detail.isFollowed == true)
            TextButton(
              key: const ValueKey('pixiv-follow-dialog-unfollow'),
              style: TextButton.styleFrom(foregroundColor: Theme.of(context).colorScheme.error),
              onPressed: () => Navigator.pop(context, (follow: false, restrict: '')),
              child: Text(l10n.plugin_pixiv_unfollow),
            ),
          TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
          FilledButton(
            key: const ValueKey('pixiv-follow-dialog-confirm'),
            onPressed: draft == null
                ? null
                : () => Navigator.pop(context, (follow: true, restrict: draft.private ? 'private' : 'public')),
            child: Text(draft?.detail.isFollowed == true ? l10n.save : l10n.plugin_pixiv_follow),
          ),
        ],
      );
    },
  );

  Widget _content(BuildContext context, PixivFollowDraft? draft, Object? error) {
    final l10n = L10n.of(context);
    if (draft != null) {
      return SwitchListTile(
        key: const ValueKey('pixiv-follow-dialog-private'),
        contentPadding: EdgeInsets.zero,
        title: Text(l10n.plugin_pixiv_follow_privately),
        subtitle: Text(l10n.plugin_pixiv_follow_privately_hint),
        value: draft.private,
        onChanged: _draft.setPrivate,
      );
    }
    if (error != null) {
      return Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(pixivErrorMessage(l10n, error)),
          const SizedBox(height: 8),
          TextButton.icon(onPressed: _draft.load, icon: const Icon(Icons.refresh), label: Text(l10n.retry)),
        ],
      );
    }
    return const SizedBox(height: 96, child: Center(child: CircularProgressIndicator()));
  }
}
