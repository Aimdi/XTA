import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_models.dart';

/// Whether moving the rating cap from [current] to [next] lets adult posts
/// in for the first time: Questionable and Explicit need the reader to say
/// they are an adult who wants them.
bool booruRatingNeedsConsent(BooruRating current, BooruRating next) =>
    next.exceeds(BooruRating.sensitive) && !current.exceeds(BooruRating.sensitive);

/// Asks the reader to confirm they are 18 or older and want adult posts.
/// [site] names a host whose posts are mostly explicit. Anything but the
/// confirm button, the back button included, is a no.
Future<bool> confirmBooruAdultContent(BuildContext context, {String? site}) async {
  final l10n = L10n.of(context);
  final confirmed = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(l10n.plugin_booru_adult_title),
      content: Text([if (site != null) l10n.plugin_booru_adult_site(site), l10n.plugin_booru_adult_body].join('\n\n')),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
        FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.plugin_booru_adult_confirm)),
      ],
    ),
  );
  return confirmed ?? false;
}
