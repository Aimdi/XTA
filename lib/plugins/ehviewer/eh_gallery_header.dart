import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_grid.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_ui.dart';
import 'package:xta/plugins/plugin_storage.dart';
import 'package:xta/plugins/plugin_tag_chip.dart';
import 'package:xta/ui/contrast.dart';

const ehCoverWidth = 120.0;
const _ehCoverAspect = 0.7;

/// A section heading on the gallery page.
class EhSectionTitle extends StatelessWidget {
  final String text;

  const EhSectionTitle(this.text, {super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 4),
      child: Semantics(header: true, child: Text(text, style: Theme.of(context).textTheme.titleSmall)),
    );
  }
}

/// The cover on the start side and, beside it, the titles, the uploader and
/// the category.
class EhGalleryHeader extends StatelessWidget {
  final EhGallery gallery;

  /// The cover the gallery list showed, for when the detail has none or its
  /// own fails to load.
  final String? listCover;
  final ValueChanged<String> onUploader;

  const EhGalleryHeader({super.key, required this.gallery, required this.onUploader, this.listCover});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _cover(context),
          const SizedBox(width: 12),
          Expanded(child: _details(context)),
        ],
      ),
    );
  }

  Widget _cover(BuildContext context) {
    return ExcludeSemantics(
      child: ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: SizedBox(
          width: ehCoverWidth,
          height: ehCoverWidth / _ehCoverAspect,
          child: EhNetworkImage(
            url: gallery.thumbUrl ?? listCover ?? '',
            fallbackUrl: listCover,
            cacheWidth: (ehCoverWidth * MediaQuery.devicePixelRatioOf(context)).ceil(),
          ),
        ),
      ),
    );
  }

  Widget _details(BuildContext context) {
    final theme = Theme.of(context);
    final preferJapanese = ehPreferJapaneseOf(context);
    final title = gallery.titleFor(preferJapanese: preferJapanese);
    final other = gallery.titleFor(preferJapanese: !preferJapanese);
    final uploader = gallery.uploader?.trim() ?? '';
    final category = gallery.category;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
        if (other != title) ...[
          const SizedBox(height: 4),
          Text(
            other,
            maxLines: 3,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
        ],
        if (uploader.isNotEmpty) _uploader(context, uploader),
        if (category != null)
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: EhCategoryBadge(category: category),
          ),
      ],
    );
  }

  Widget _uploader(BuildContext context, String name) {
    return Padding(
      padding: const EdgeInsets.only(top: 4),
      child: TextButton.icon(
        key: const ValueKey('eh-gallery-uploader'),
        onPressed: () => onUploader(name),
        style: TextButton.styleFrom(
          minimumSize: const Size(48, 48),
          padding: const EdgeInsetsDirectional.only(start: 4, end: 8),
          alignment: AlignmentDirectional.centerStart,
        ),
        icon: const Icon(Icons.person_outline, size: 18),
        label: Text(
          name,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          semanticsLabel: L10n.of(context).plugin_eh_uploader(name),
        ),
      ),
    );
  }
}

/// What the site says about the gallery, each fact only when the site sent it.
class EhGalleryFacts extends StatelessWidget {
  final EhGalleryDetail detail;

  const EhGalleryFacts({super.key, required this.detail});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final locale = Localizations.localeOf(context).toString();
    final language = detail.language;
    final size = detail.fileSizeBytes;
    final favorited = detail.favoritedCount;
    final posted = detail.postedAt;
    final pages = detail.pageCount;
    final facts = [
      ?_rating(context, locale),
      if (pages != null) _EhFact(icon: Icons.description_outlined, text: l10n.plugin_eh_gallery_pages(pages)),
      if (language != null) _language(context, language),
      if (size != null) _fact(Icons.sd_storage_outlined, formatStorageSize(size), l10n.plugin_eh_gallery_file_size),
      if (favorited != null)
        _EhFact(
          icon: Icons.favorite_border,
          text: pluginCompactCount(favorited, locale),
          semanticsLabel: l10n.plugin_eh_gallery_favorited(favorited),
        ),
      if (posted != null)
        _fact(Icons.calendar_today_outlined, DateFormat.yMMMd(locale).format(posted), l10n.plugin_eh_gallery_posted),
    ];
    if (facts.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
      child: Wrap(spacing: 16, runSpacing: 8, crossAxisAlignment: WrapCrossAlignment.center, children: facts),
    );
  }

  Widget _fact(IconData icon, String text, String Function(String) describe) =>
      _EhFact(icon: icon, text: text, semanticsLabel: describe(text));

  Widget? _rating(BuildContext context, String locale) {
    final rating = detail.rating;
    if (rating == null) return null;
    final l10n = L10n.of(context);
    final value = NumberFormat('0.00', locale).format(rating);
    final count = detail.ratingCount;
    return _EhFact(
      key: const ValueKey('eh-gallery-rating'),
      leading: EhRatingStars(rating: rating),
      text: count == null
          ? value
          : l10n.plugin_eh_gallery_rating_value(value, NumberFormat.decimalPattern(locale).format(count)),
      semanticsLabel: count == null
          ? l10n.plugin_eh_rating(value)
          : l10n.plugin_eh_gallery_rating_semantics(value, count),
    );
  }

  Widget _language(BuildContext context, String language) {
    final l10n = L10n.of(context);
    final translated = detail.translated;
    return _EhFact(
      icon: Icons.language,
      text: language,
      trailing: translated
          ? Icon(Icons.translate, size: 14, color: Theme.of(context).colorScheme.onSurfaceVariant)
          : null,
      semanticsLabel: translated
          ? l10n.plugin_eh_gallery_language_translated(language)
          : l10n.plugin_eh_gallery_language(language),
    );
  }
}

class _EhFact extends StatelessWidget {
  final IconData? icon;
  final Widget? leading;
  final String text;
  final Widget? trailing;
  final String? semanticsLabel;

  const _EhFact({super.key, this.icon, this.leading, required this.text, this.trailing, this.semanticsLabel});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Semantics(
      container: true,
      label: semanticsLabel ?? text,
      child: ExcludeSemantics(
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            leading ?? Icon(icon, size: 16, color: theme.colorScheme.onSurfaceVariant),
            const SizedBox(width: 4),
            Flexible(child: Text(text, style: theme.textTheme.bodyMedium)),
            if (trailing != null) ...[const SizedBox(width: 4), trailing!],
          ],
        ),
      ),
    );
  }
}

/// Five stars filled to [rating], in halves.
class EhRatingStars extends StatelessWidget {
  final double rating;
  final double size;

  const EhRatingStars({super.key, required this.rating, this.size = 16});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final color = ensureContrast(const Color(0xFFF5A623), scheme.surface, minRatio: 3);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [for (var star = 0; star < 5; star++) Icon(_icon(rating - star), size: size, color: color)],
    );
  }

  static IconData _icon(double fill) => fill >= 0.75
      ? Icons.star_rounded
      : fill >= 0.25
      ? Icons.star_half_rounded
      : Icons.star_outline_rounded;
}
