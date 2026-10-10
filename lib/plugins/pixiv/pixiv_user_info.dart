import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_user_list_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_user_profile.dart';
import 'package:xta/utils/urls.dart';

/// What tapping an Info row does.
sealed class PixivInfoAction {
  const PixivInfoAction();
}

/// Puts [text] on the clipboard.
class PixivInfoCopy extends PixivInfoAction {
  final String text;

  const PixivInfoCopy(this.text);
}

/// Opens [url] the way the reader opens links.
class PixivInfoLink extends PixivInfoAction {
  final String url;

  const PixivInfoLink(this.url);
}

/// Opens the profile's [kind] list.
class PixivInfoList extends PixivInfoAction {
  final PixivUserListKind kind;

  const PixivInfoList(this.kind);
}

/// One line of the Info table; [id] keys it `pixiv-profile-info-<id>`.
typedef PixivInfoRow = ({String id, String label, String value, PixivInfoAction? action});

/// The Info table for [profile]: who they are, their counts and their links,
/// leaving out whatever they did not fill in or keep private.
List<PixivInfoRow> pixivProfileInfoRows(L10n l10n, PixivUserProfile profile, String locale) {
  final number = NumberFormat.decimalPattern(locale);
  PixivInfoRow row(String id, String label, String value, [PixivInfoAction? action]) =>
      (id: id, label: label, value: value, action: action);
  PixivInfoRow? text(String id, String label, String value) => value.isEmpty ? null : row(id, label, value);
  final birthday = pixivBirthdayLabel(profile, locale);
  return [
    row('nickname', l10n.plugin_pixiv_profile_nickname, profile.user.name),
    row('id', l10n.plugin_pixiv_profile_user_id, '${profile.id}', PixivInfoCopy('${profile.id}')),
    row(
      'following',
      l10n.plugin_pixiv_profile_following,
      number.format(profile.user.followingCount),
      const PixivInfoList(PixivUserListKind.following),
    ),
    row('followers', l10n.plugin_pixiv_profile_followers, '', const PixivInfoList(PixivUserListKind.followers)),
    row('mypixiv', l10n.plugin_pixiv_profile_mypixiv, number.format(profile.user.mypixivCount)),
    row('illusts', l10n.plugin_pixiv_profile_illusts, number.format(profile.totalIllusts)),
    row('manga', l10n.plugin_pixiv_profile_manga, number.format(profile.totalManga)),
    if (profile.totalNovels > 0) row('novels', l10n.plugin_pixiv_profile_novels, number.format(profile.totalNovels)),
    row('bookmarks', l10n.plugin_pixiv_profile_public_bookmarks, number.format(profile.publicBookmarks)),
    ?text('gender', l10n.plugin_pixiv_profile_gender, profile.gender),
    ?text('region', l10n.plugin_pixiv_profile_region, profile.region),
    ?text('birthday', l10n.plugin_pixiv_profile_birthday, birthday),
    ?text('job', l10n.plugin_pixiv_profile_job, profile.job),
    ..._links(l10n, profile, row),
  ];
}

Iterable<PixivInfoRow> _links(
  L10n l10n,
  PixivUserProfile profile,
  PixivInfoRow Function(String id, String label, String value, [PixivInfoAction? action]) row,
) sync* {
  if (profile.webpage.isNotEmpty) {
    yield row('website', l10n.plugin_pixiv_profile_website, profile.webpage, PixivInfoLink(profile.webpage));
  }
  final twitter = profile.twitterLink;
  if (twitter.isNotEmpty) {
    final account = profile.twitterAccount.replaceFirst('@', '');
    yield row(
      'twitter',
      l10n.plugin_pixiv_profile_twitter,
      account.isEmpty ? twitter : '@$account',
      PixivInfoLink(twitter),
    );
  }
  if (profile.pawooUrl.isNotEmpty) {
    yield row('pawoo', l10n.plugin_pixiv_profile_pawoo, profile.pawooUrl, PixivInfoLink(profile.pawooUrl));
  }
}

/// The profile's Info tab: a two-column table whose rows copy the ID, open
/// the follow lists and open the creator's links.
class PixivProfileInfo extends StatelessWidget {
  final PixivUserProfile profile;

  const PixivProfileInfo({super.key, required this.profile});

  @override
  Widget build(BuildContext context) {
    final rows = pixivProfileInfoRows(L10n.of(context), profile, Intl.getCurrentLocale());
    return ListView.separated(
      padding: EdgeInsets.only(top: 8, bottom: 16 + MediaQuery.paddingOf(context).bottom),
      itemCount: rows.length,
      separatorBuilder: (_, _) => const Divider(height: 1, indent: 16, endIndent: 16),
      itemBuilder: (context, index) => _row(context, rows[index]),
    );
  }

  Widget _row(BuildContext context, PixivInfoRow row) {
    final theme = Theme.of(context);
    final action = row.action;
    final content = ConstrainedBox(
      constraints: const BoxConstraints(minHeight: kMinInteractiveDimension),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          spacing: 12,
          children: [
            Expanded(
              flex: 2,
              child: Text(
                row.label,
                style: theme.textTheme.bodyMedium!.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
            ),
            Expanded(flex: 3, child: Text(row.value, style: theme.textTheme.bodyLarge)),
            // The slot stays when empty so every value starts in the same column.
            SizedBox.square(
              dimension: 20,
              child: action == null ? null : Icon(_icon(action), size: 20, color: theme.colorScheme.onSurfaceVariant),
            ),
          ],
        ),
      ),
    );
    if (action == null) return content;
    return InkWell(key: ValueKey('pixiv-profile-info-${row.id}'), onTap: () => _run(context, action), child: content);
  }

  IconData _icon(PixivInfoAction action) => switch (action) {
    PixivInfoCopy() => Icons.copy_outlined,
    PixivInfoLink() => Icons.open_in_new,
    PixivInfoList() => Icons.chevron_right,
  };

  Future<void> _run(BuildContext context, PixivInfoAction action) async {
    switch (action) {
      case PixivInfoCopy(:final text):
        final messenger = ScaffoldMessenger.of(context);
        final copied = L10n.of(context).plugin_pixiv_profile_id_copied;
        await Clipboard.setData(ClipboardData(text: text));
        messenger.showSnackBar(SnackBar(content: Text(copied)));
      case PixivInfoLink(:final url):
        await openUri(context, url);
      case PixivInfoList(:final kind):
        await openPixivUserList(context, kind, userId: profile.id);
    }
  }
}
