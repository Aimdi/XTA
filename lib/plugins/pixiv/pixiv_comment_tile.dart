import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_avatar.dart';
import 'package:xta/plugins/pixiv/pixiv_comment_emoji.dart';
import 'package:xta/plugins/pixiv/pixiv_comment_models.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_sheet.dart';
import 'package:xta/plugins/plugin_comment_bubble.dart';
import 'package:xta/ui/dates.dart';

/// What a comment offers to mute: itself, and its author when Pixiv named one.
List<PixivMuteChoice> pixivCommentMuteChoices(L10n l10n, PixivComment comment) => [
  (
    icon: Icons.comments_disabled_outlined,
    label: l10n.plugin_pixiv_comment_mute,
    mute: (store) => store.muteComment(comment.id),
  ),
  if ((comment.user, pixivCommentAuthorName(comment.user)) case (final user?, final name?))
    (
      icon: Icons.person_off_outlined,
      label: l10n.plugin_pixiv_comment_mute_user(name),
      mute: (store) => store.muteAuthor(user.id),
    ),
];

const _avatarSize = 36.0;
const _stickerSize = 100.0;

/// One comment in its bubble: avatar, who wrote it, whom it answers and when,
/// then its text with emoji or its sticker, and the way into its replies.
class PixivCommentTile extends StatelessWidget {
  final PixivComment comment;
  final int depth;

  /// The reader tapped Show on a comment that links outside Pixiv.
  final bool revealed;
  final VoidCallback onReveal;

  /// Null hides "View replies": a comment without any, or the thread's own parent.
  final VoidCallback? onViewReplies;
  final ValueChanged<PixivMuteChoice> onMute;

  const PixivCommentTile({
    super.key,
    required this.comment,
    required this.onReveal,
    required this.onMute,
    this.depth = 0,
    this.revealed = false,
    this.onViewReplies,
  });

  bool get _hidden => !revealed && comment.linksOutsidePixiv;

  @override
  Widget build(BuildContext context) => CommentBubble(
    depth: depth,
    onTap: _hidden ? onReveal : null,
    builder: (context, colors) => Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _avatar(context),
        const SizedBox(width: 6),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(padding: const EdgeInsets.only(top: 4), child: _header(context, colors)),
              ..._body(context, colors),
              if (onViewReplies != null) _repliesChip(context),
            ],
          ),
        ),
        _menu(context),
      ],
    ),
  );

  Widget _avatar(BuildContext context) {
    final user = comment.user;
    final name = pixivCommentAuthorName(user) ?? L10n.of(context).plugin_pixiv_comment_unknown_user;
    final avatar = PixivAvatar(userId: user?.id ?? 0, name: name, url: user?.avatarUrl, size: _avatarSize);
    return Semantics(
      button: user != null,
      label: name,
      excludeSemantics: true,
      child: InkResponse(
        onTap: user == null ? null : () => openPixivUser(context, user.id),
        radius: kMinInteractiveDimension / 2,
        child: SizedBox.square(
          dimension: kMinInteractiveDimension,
          child: Center(child: avatar),
        ),
      ),
    );
  }

  /// "Name · To someone · when", the name set apart so a thread reads by speaker.
  Widget _header(BuildContext context, CommentBubbleColors colors) {
    final l10n = L10n.of(context);
    final replyTo = pixivCommentAuthorName(comment.replyTo);
    final date = comment.date;
    return Text.rich(
      TextSpan(
        children: [
          TextSpan(
            text: pixivCommentAuthorName(comment.user) ?? l10n.plugin_pixiv_comment_unknown_user,
            style: TextStyle(fontWeight: FontWeight.w700, color: colors.text),
          ),
          if (replyTo != null) TextSpan(text: ' · ${l10n.plugin_pixiv_comment_reply_to(replyTo)}'),
          if (date != null) TextSpan(text: ' · ${createCompactDate(date)}'),
        ],
      ),
      style: Theme.of(context).textTheme.labelMedium!.copyWith(color: colors.muted),
    );
  }

  List<Widget> _body(BuildContext context, CommentBubbleColors colors) {
    if (_hidden) return [_hiddenLink(context, colors)];
    final sticker = comment.stampUrl;
    return [
      if (comment.text.isNotEmpty)
        Padding(
          padding: const EdgeInsets.only(top: 4),
          child: SelectionArea(
            child: PixivCommentText(
              text: comment.text,
              style: Theme.of(context).textTheme.bodyMedium!.copyWith(color: colors.text, height: 1.35),
            ),
          ),
        ),
      if (sticker != null) _sticker(context, sticker),
    ];
  }

  Widget _sticker(BuildContext context, String url) {
    final pixels = (_stickerSize * MediaQuery.devicePixelRatioOf(context)).ceil();
    return Padding(
      padding: const EdgeInsets.only(top: 6),
      child: Semantics(
        image: true,
        label: L10n.of(context).plugin_pixiv_comment_sticker,
        child: SizedBox.square(
          dimension: _stickerSize,
          child: PixivNetworkImage(url: url, fit: BoxFit.contain, cacheWidth: pixels, cacheHeight: pixels),
        ),
      ),
    );
  }

  Widget _hiddenLink(BuildContext context, CommentBubbleColors colors) {
    final l10n = L10n.of(context);
    return Row(
      children: [
        Icon(Icons.link_off, size: 16, color: colors.muted),
        const SizedBox(width: 6),
        Expanded(
          child: Text(
            l10n.plugin_pixiv_comment_hidden_link,
            style: Theme.of(context).textTheme.bodySmall!.copyWith(color: colors.muted, fontStyle: FontStyle.italic),
          ),
        ),
        TextButton(onPressed: onReveal, child: Text(l10n.plugin_pixiv_comment_show)),
      ],
    );
  }

  Widget _repliesChip(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 4),
    child: ActionChip(
      avatar: const Icon(Icons.forum_outlined, size: 18),
      label: Text(L10n.of(context).plugin_pixiv_comment_view_replies),
      onPressed: onViewReplies,
    ),
  );

  Widget _menu(BuildContext context) {
    final l10n = L10n.of(context);
    return PopupMenuButton<PixivMuteChoice>(
      tooltip: l10n.plugin_pixiv_comment_options,
      icon: const Icon(Icons.more_vert),
      onSelected: onMute,
      itemBuilder: (context) => [
        for (final choice in pixivCommentMuteChoices(l10n, comment))
          PopupMenuItem(
            value: choice,
            child: ListTile(leading: Icon(choice.icon), title: Text(choice.label), contentPadding: EdgeInsets.zero),
          ),
      ],
    );
  }
}
