import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_labels.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/plugin_storage.dart';

typedef BooruFact = ({IconData icon, String text, String? label});

/// Score, votes, favorites, rating, size, file and who posted it when: each
/// one the host sent. [label] is read aloud where the text alone is unclear.
List<BooruFact> booruPostFacts(BooruPost post, L10n l10n, String locale) {
  final numbers = NumberFormat.decimalPattern(locale);
  final up = post.upScore;
  final down = post.downScore;
  final rating = post.rating;
  final created = post.createdAt;
  final uploader = post.uploader;
  final date = created == null ? null : DateFormat.yMMMd(locale).add_jm().format(created.toLocal());
  return [
    if (post.score case final score?)
      (icon: Icons.thumb_up_alt_outlined, text: numbers.format(score), label: l10n.plugin_booru_score(score)),
    if (up != null && down != null)
      (
        icon: Icons.thumbs_up_down_outlined,
        text: '+${numbers.format(up)} −${numbers.format(down)}',
        label: l10n.plugin_booru_votes(up, down),
      ),
    if (post.favCount case final favs?)
      (icon: Icons.favorite_border, text: numbers.format(favs), label: l10n.plugin_booru_favorites(favs)),
    if (rating != null) (icon: Icons.shield_outlined, text: booruRatingLabel(l10n, rating), label: null),
    if (post.width > 0 && post.height > 0)
      (icon: Icons.aspect_ratio, text: '${post.width} × ${post.height}', label: null),
    if (_fileText(post) case final file?) (icon: Icons.insert_drive_file_outlined, text: file, label: null),
    if (date != null) (icon: Icons.schedule, text: date, label: l10n.plugin_booru_posted_on(date)),
    if (uploader != null) (icon: Icons.person_outline, text: uploader, label: l10n.plugin_booru_uploaded_by(uploader)),
  ];
}

String? _fileText(BooruPost post) {
  final ext = post.fileExt?.toUpperCase();
  final size = post.fileSize == null ? null : formatStorageSize(post.fileSize!);
  final parts = [?ext, ?size];
  return parts.isEmpty ? null : parts.join(' · ');
}

class BooruPostFacts extends StatelessWidget {
  final BooruPost post;

  const BooruPostFacts({super.key, required this.post});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final locale = Localizations.localeOf(context).toString();
    final facts = booruPostFacts(post, L10n.of(context), locale);
    final muted = theme.colorScheme.onSurfaceVariant;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
      child: Wrap(
        spacing: 16,
        runSpacing: 8,
        children: [
          for (final fact in facts)
            Semantics(
              label: fact.label ?? fact.text,
              excludeSemantics: true,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(fact.icon, size: 16, color: muted),
                  const SizedBox(width: 4),
                  Flexible(
                    child: Text(fact.text, style: theme.textTheme.bodyMedium, overflow: TextOverflow.ellipsis),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
