import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/plugin_link_post.dart';
import 'package:xta/plugins/plugin_post_actions.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/ui/errors.dart';

/// The page that names the post: on its host, else the file itself.
String booruShareUrl(BooruPost post) => post.hostPageUrl ?? post.originalUrl;

/// What "save on this device" keeps. Same id from the feed card and the viewer.
PluginPostArchive booruPostArchive(BooruPost post) => PluginLinkPost(
  source: pluginIdBooru,
  url: booruShareUrl(post),
  author: post.host,
  text: post.tagLine,
  images: [post.isVideo ? post.thumbnailUrl : post.displayUrl],
).archive;

/// The original file, videos included: the shared download path skips
/// anything marked as video.
PluginMediaItem booruDownloadItem(BooruPost post) =>
    PluginMediaItem(url: post.displayUrl, downloadUrl: post.originalUrl, shareUrl: booruShareUrl(post));

Future<void> openBooruLink(String url) async {
  final uri = Uri.tryParse(url.trim());
  if (uri == null || !uri.hasScheme) return;
  await launchUrl(uri, mode: LaunchMode.externalApplication);
}

Future<void> copyBooruText(BuildContext context, String text) async {
  final message = L10n.of(context).plugin_booru_copied;
  await Clipboard.setData(ClipboardData(text: text));
  if (context.mounted) showSnackBar(context, icon: '📋', message: message);
}

enum _PostMenu { share, copyLink, copyTags, host, source, file }

/// Download, save and the rest of a post's actions, for the viewer's app bar.
class BooruPostActions extends StatelessWidget {
  final BooruPost post;

  const BooruPostActions({super.key, required this.post});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton(
          key: const ValueKey('booru-download'),
          tooltip: l10n.download,
          icon: const Icon(Icons.download_outlined),
          onPressed: () => downloadPluginMediaItem(context, booruDownloadItem(post), sourceName: pluginIdBooru),
        ),
        _SaveButton(archive: booruPostArchive(post)),
        PopupMenuButton<_PostMenu>(
          key: const ValueKey('booru-post-menu'),
          onSelected: (action) => _run(context, action),
          itemBuilder: (context) => [
            _item(_PostMenu.share, Icons.share_outlined, l10n.share_link),
            _item(_PostMenu.copyLink, Icons.link, l10n.plugin_booru_copy_link),
            if (post.tags.isNotEmpty) _item(_PostMenu.copyTags, Icons.sell_outlined, l10n.plugin_booru_copy_tags),
            if (post.hostPageUrl != null) _item(_PostMenu.host, Icons.public, l10n.plugin_booru_open_on_host),
            if (_source != null) _item(_PostMenu.source, Icons.open_in_new, l10n.plugin_booru_open_source),
            _item(_PostMenu.file, Icons.insert_drive_file_outlined, l10n.plugin_booru_open_file),
          ],
        ),
      ],
    );
  }

  String? get _source {
    final source = post.source?.trim();
    return source == null || source.isEmpty ? null : source;
  }

  PopupMenuItem<_PostMenu> _item(_PostMenu value, IconData icon, String label) => PopupMenuItem(
    value: value,
    child: ListTile(leading: Icon(icon), title: Text(label), contentPadding: EdgeInsets.zero),
  );

  Future<void> _run(BuildContext context, _PostMenu action) => switch (action) {
    _PostMenu.share => SharePlus.instance.share(ShareParams(text: booruShareUrl(post))),
    _PostMenu.copyLink => copyBooruText(context, booruShareUrl(post)),
    _PostMenu.copyTags => copyBooruText(context, post.tagLine),
    _PostMenu.host => openBooruLink(post.hostPageUrl!),
    _PostMenu.source => openBooruLink(_source!),
    _PostMenu.file => openBooruLink(post.originalUrl),
  };
}

class _SaveButton extends StatelessWidget {
  final PluginPostArchive archive;

  const _SaveButton({required this.archive});

  @override
  Widget build(BuildContext context) {
    final model = context.read<SavedTweetModel?>();
    if (model == null) return const SizedBox.shrink();
    final l10n = L10n.of(context);
    return ScopedBuilder<SavedTweetModel, List<SavedTweet>>(
      store: model,
      onState: (context, _) {
        final saved = model.isSaved(archive.id);
        return IconButton(
          key: const ValueKey('booru-save'),
          tooltip: saved ? l10n.unsave_from_this_device : l10n.save_on_this_device,
          isSelected: saved,
          icon: const Icon(Icons.bookmark_border),
          selectedIcon: const Icon(Icons.bookmark),
          onPressed: () => saved ? model.deleteSavedTweet(archive.id) : savePluginPost(context, archive),
        );
      },
    );
  }
}
