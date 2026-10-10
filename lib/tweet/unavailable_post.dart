import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/links/link_opening.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/utils/urls.dart';

/// A post X will not show — deleted, withheld, or from an account that went
/// private or was suspended.
///
/// When its author and id are known, one tap looks it up in the Wayback
/// Machine, through the same link opening as a link in a post, so the
/// reader's browser setting decides where it opens.
class UnavailablePostTile extends StatelessWidget {
  final String message;
  final String? screenName;
  final String? id;

  const UnavailablePostTile({super.key, required this.message, this.screenName, this.id});

  Uri? get archiveUri {
    final screenName = this.screenName;
    final id = this.id;
    if (screenName == null || id == null) {
      return null;
    }
    return waybackSearchUri(screenName, id);
  }

  @override
  Widget build(BuildContext context) {
    final archive = archiveUri;
    return TweetStateTile(
      icon: Icons.info_outline,
      message: message,
      action: archive == null
          ? null
          : TextButton.icon(
              onPressed: () => openPostLink(context, archive.toString()),
              icon: const Icon(Icons.history),
              label: Text(L10n.of(context).open_on_web_archive),
            ),
    );
  }
}
