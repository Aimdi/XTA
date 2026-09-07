import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_archive.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/saved/folder_picker.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/tweet/tweet_footer.dart';

class MastodonBookmark extends StatelessWidget {
  final MastodonPost post;
  const MastodonBookmark({super.key, required this.post});

  Future<void> _toggle(BuildContext context, SavedTweetModel model) async {
    final id = mastodonArchiveId(post);
    if (model.isSaved(id)) {
      await model.deleteSavedTweet(id);
    } else {
      await fileSavedTweet(context, tweetId: id, userId: post.acct,
        content: mastodonArchiveBlob(post), folderId: rememberedSaveFolder(PrefService.of(context, listen: false)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final model = context.read<SavedTweetModel?>();
    if (model == null) return const SizedBox.shrink();
    return ScopedBuilder<SavedTweetModel, List<SavedTweet>>(store: model,
      onState: (context, saved) {
        final selected = model.isSaved(mastodonArchiveId(post));
        return GestureDetector(
          onLongPress: () => showSaveToFolderSheet(context, tweetId: mastodonArchiveId(post), userId: post.acct,
            content: mastodonArchiveBlob(post)),
          child: tweetFooterIconButton(context,
          selected ? Icons.bookmark : Icons.bookmark_border,
          selected ? Theme.of(context).colorScheme.primary : Theme.of(context).colorScheme.onSurfaceVariant,
          null,
          () => _toggle(context, model),
          selected ? L10n.of(context).unsave_from_this_device : L10n.of(context).save_on_this_device,
        ));
      },
    );
  }
}
