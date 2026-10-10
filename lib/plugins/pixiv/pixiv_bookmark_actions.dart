import 'dart:async';

import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_api.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_download.dart';
import 'package:xta/plugins/pixiv/pixiv_haptics.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_user_store.dart';

/// Where a bookmark write left the work, and whether it also followed the author.
typedef PixivBookmarkOutcome = ({bool bookmarked, bool followedAuthor});

const PixivBookmarkOutcome _removed = (bookmarked: false, followedAuthor: false);

/// Pixiv's popularity tags such as `1000users入り` or `東方100users入り`,
/// which say how many bookmarked a work, not what it is.
final _popularityTag = RegExp(r'\d+users入り');

/// The work's own tag names to file a bookmark under, popularity tags left out.
List<String> pixivAutoBookmarkTags(PixivIllust illust) => [
  for (final tag in illust.tags)
    if (!_popularityTag.hasMatch(tag.name)) tag.name,
];

/// The visibility a bookmark made without the editor gets, for works and novels alike.
String pixivDefaultBookmarkRestrict(BasePrefService prefs) =>
    prefs.get<bool>(optionPluginPixivDefaultPrivateBookmark) == true ? 'private' : 'public';

PixivUser _authorOf(PixivIllust illust) => PixivUser(
  id: illust.userId,
  name: illust.userName,
  account: illust.userAccount,
  comment: '',
  avatarUrl: illust.userAvatarUrl,
  isFollowed: illust.userIsFollowed,
);

/// Every bookmark write: the heart, the editor and bookmarking after a save.
/// It applies the reader's Bookmarking settings — default visibility,
/// auto-tags, following the author and saving the work.
class PixivBookmarkActions {
  final PixivBookmarkApi api;
  final PixivBookmarkStore bookmarks;
  final PixivFollowStore follows;
  final BasePrefService prefs;

  /// Saves every page of a work, for save-after-bookmark.
  final Future<void> Function(PixivIllust illust) download;

  const PixivBookmarkActions({
    required this.api,
    required this.bookmarks,
    required this.follows,
    required this.prefs,
    required this.download,
  });

  factory PixivBookmarkActions.of(BuildContext context) => PixivBookmarkActions(
    api: PixivBookmarkApi.of(context),
    bookmarks: context.read<PixivBookmarkStore>(),
    follows: context.read<PixivFollowStore>(),
    prefs: PrefService.of(context, listen: false),
    download: (illust) async {
      if (context.mounted) await downloadAllPixivPages(context, illust);
    },
  );

  bool _on(String pref) => prefs.get<bool>(pref) == true;

  /// The visibility a bookmark made without the editor gets.
  String get defaultRestrict => pixivDefaultBookmarkRestrict(prefs);

  /// The work's bookmark as Pixiv has it now. A card loaded before the work
  /// was bookmarked or removed elsewhere is brought in line, so the heart, the
  /// count and whether the next write is a new bookmark follow Pixiv.
  Future<PixivBookmarkDetail> detail(PixivIllust illust) async {
    final detail = await api.detail(illust.id);
    final stale = bookmarks.isBookmarked(illust) != detail.isBookmarked;
    if (stale && !bookmarks.isBusy(illust.id)) bookmarks.mark(illust.id, detail.isBookmarked);
    return detail;
  }

  /// Bookmarks [illust] or re-files the bookmark it has. Tags from the editor
  /// win; without them the work's own tags go along when auto-tag is on. A new
  /// bookmark may also save the work (never when [fromSave]) and follow its author.
  Future<PixivBookmarkOutcome> bookmark(
    PixivIllust illust, {
    String? restrict,
    List<String>? tags,
    bool fromSave = false,
  }) async {
    final isNew = !bookmarks.isBookmarked(illust);
    await api.add(illust.id, restrict: restrict ?? defaultRestrict, tags: tags ?? autoTags(illust));
    bookmarks.mark(illust.id, true);
    if (!isNew) return (bookmarked: true, followedAuthor: false);
    if (!fromSave && _on(optionPluginPixivDownloadAfterBookmark)) unawaited(download(illust));
    return (bookmarked: true, followedAuthor: await _followAuthor(illust));
  }

  /// The tags a bookmark made without the editor is filed under.
  List<String> autoTags(PixivIllust illust) =>
      _on(optionPluginPixivAutoTagBookmarks) ? pixivAutoBookmarkTags(illust) : const [];

  /// A follow that fails leaves the bookmark standing; only success is reported.
  Future<bool> _followAuthor(PixivIllust illust) async {
    final author = _authorOf(illust);
    final own = author.id == api.client.storedUserId;
    if (!_on(optionPluginPixivFollowAfterBookmark) || own || follows.isFollowed(author)) return false;
    try {
      return await follows.toggle(author);
    } catch (_) {
      return false;
    }
  }

  Future<PixivBookmarkOutcome> unbookmark(PixivIllust illust) async {
    await api.delete(illust.id);
    bookmarks.mark(illust.id, false);
    return _removed;
  }

  /// The heart: bookmarks with the defaults, or removes the bookmark. Null
  /// when a write for this work is already on its way.
  Future<PixivBookmarkOutcome?> toggle(PixivIllust illust) =>
      bookmarks.exclusive(illust.id, () => bookmarks.isBookmarked(illust) ? unbookmark(illust) : bookmark(illust));

  /// Bookmark-after-save: bookmarks a work just saved when the setting is on
  /// and it is not bookmarked yet. Null when nothing was written.
  Future<PixivBookmarkOutcome?> ensureBookmarked(PixivIllust illust) async {
    if (!_on(optionPluginPixivBookmarkAfterDownload) || bookmarks.isBookmarked(illust)) return null;
    return bookmarks.exclusive(illust.id, () => bookmark(illust, fromSave: true));
  }
}

/// How a bookmark write answers the reader. Built before the write, so it
/// still reports when the screen that started it has gone.
class PixivBookmarkFeedback {
  final ScaffoldMessengerState messenger;
  final L10n l10n;
  final PixivHaptics haptics;
  final BasePrefService prefs;

  PixivBookmarkFeedback.of(BuildContext context)
    : messenger = ScaffoldMessenger.of(context),
      l10n = L10n.of(context),
      haptics = PixivHaptics.of(context),
      prefs = PrefService.of(context, listen: false);

  /// The light buzz of a bookmark write that landed.
  void landed() => haptics.play(prefs, PixivHaptic.light);

  /// A light buzz, and the author's name when the bookmark also followed them.
  void succeeded(PixivIllust illust, PixivBookmarkOutcome outcome) {
    landed();
    if (outcome.followedAuthor && messenger.mounted) {
      messenger.showSnackBar(SnackBar(content: Text(l10n.plugin_pixiv_bookmark_followed_author(illust.userName))));
    }
  }

  void failed(Object error) {
    if (messenger.mounted) messenger.showSnackBar(SnackBar(content: Text(pixivErrorMessage(l10n, error))));
  }
}

/// Runs one bookmark [write], of a work or a novel, and tells the reader how
/// it went: [landed] with what the write left, the reason when it fails. A
/// null outcome means nothing was written.
Future<void> runPixivBookmarkWrite<O extends Object>(
  BuildContext context,
  Future<O?> Function() write,
  void Function(PixivBookmarkFeedback feedback, O outcome) landed,
) async {
  final feedback = PixivBookmarkFeedback.of(context);
  try {
    final outcome = await write();
    if (outcome != null) landed(feedback, outcome);
  } catch (error) {
    feedback.failed(error);
  }
}

Future<void> _runWorkWrite(
  BuildContext context,
  PixivIllust illust,
  Future<PixivBookmarkOutcome?> Function(PixivBookmarkActions actions) write,
) => runPixivBookmarkWrite(
  context,
  () => write(PixivBookmarkActions.of(context)),
  (feedback, outcome) => feedback.succeeded(illust, outcome),
);

/// What the heart does on a tap.
Future<void> togglePixivBookmark(BuildContext context, PixivIllust illust) =>
    _runWorkWrite(context, illust, (actions) => actions.toggle(illust));

/// Bookmarks [illust] after it was saved, when the reader asked for that.
Future<void> bookmarkPixivAfterSave(BuildContext context, PixivIllust illust) =>
    _runWorkWrite(context, illust, (actions) => actions.ensureBookmarked(illust));
