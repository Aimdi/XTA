import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_text.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/utils/urls.dart';

String? mastodonProfileWebUrl(MastodonProfile? profile) => _webUrl(profile?.url);

String? _webUrl(String? value) {
  final uri = Uri.tryParse(value?.trim() ?? '');
  if (uri == null || !const ['http', 'https'].contains(uri.scheme) || uri.host.isEmpty || uri.userInfo.isNotEmpty) {
    return null;
  }
  return uri.toString();
}

class MastodonProfileCard extends StatelessWidget {
  final MastodonProfile profile;
  final bool following;
  final VoidCallback? onFollowToggle;
  final VoidCallback? onAddToGroup;
  final ValueChanged<String>? onFieldTap;
  final ValueChanged<String>? onTagTap;

  const MastodonProfileCard({
    super.key,
    required this.profile,
    required this.following,
    this.onFollowToggle,
    this.onAddToGroup,
    this.onFieldTap,
    this.onTagTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_webUrl(profile.headerUrl) case final String header) ...[
          ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: AspectRatio(
              aspectRatio: 3,
              child: ExtendedImage.network(
                header,
                fit: BoxFit.cover,
                cacheWidth: (MediaQuery.sizeOf(context).width * MediaQuery.devicePixelRatioOf(context)).ceil(),
                loadStateChanged: (state) => state.extendedImageLoadState == LoadState.failed
                    ? ColoredBox(color: theme.colorScheme.surfaceContainerHighest)
                    : null,
              ),
            ),
          ),
          const SizedBox(height: 16),
        ],
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _avatar(context),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(profile.displayName, style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
                  SelectableText(
                    '@${profile.acct}',
                    style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                  ),
                  if (profile.bot || profile.locked)
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Text(
                        [
                          if (profile.bot) l10n.plugin_mastodon_bot,
                          if (profile.locked) l10n.plugin_mastodon_locked,
                        ].join(' · '),
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: theme.colorScheme.primary,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
        if (profile.note.trim().isNotEmpty) ...[
          const SizedBox(height: 14),
          SelectionArea(
            child: MastodonRichText(text: profile.note.trim(), style: theme.textTheme.bodyMedium, onTagTap: onTagTap),
          ),
        ],
        if (profile.fields.isNotEmpty) ...[
          const SizedBox(height: 12),
          for (final field in profile.fields) _field(context, field),
        ],
        if (profile.createdAt case final DateTime joined) ...[
          const SizedBox(height: 12),
          Text(
            l10n.joined(DateFormat.yMMMM().format(joined)),
            style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
        ],
        const SizedBox(height: 14),
        Wrap(
          spacing: 18,
          runSpacing: 6,
          children: [
            _count(context, profile.followersCount, l10n.followers),
            _count(context, profile.followingCount, l10n.following),
            _count(context, profile.statusesCount, l10n.tweets),
          ],
        ),
        if (onFollowToggle != null || onAddToGroup != null) ...[
          const SizedBox(height: 18),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              if (onFollowToggle != null)
                FilledButton.tonalIcon(
                  onPressed: onFollowToggle,
                  icon: Icon(following ? Icons.person_remove_alt_1 : Icons.person_add_alt),
                  label: Text(following ? l10n.plugin_mastodon_unfollow : l10n.plugin_mastodon_follow),
                ),
              if (onAddToGroup != null)
                OutlinedButton.icon(
                  onPressed: onAddToGroup,
                  icon: const Icon(Icons.group_add, size: 18),
                  label: Text(l10n.add_to_group),
                ),
            ],
          ),
        ],
      ],
    );
  }

  Widget _avatar(BuildContext context) {
    final fallback = FallbackAvatar(
      seed: profile.acct,
      displayName: profile.displayName,
      size: 64,
      accent: Theme.of(context).colorScheme.primary,
    );
    final avatar = _webUrl(profile.avatarUrl);
    return ClipOval(
      child: avatar == null
          ? fallback
          : ExtendedImage.network(
              avatar,
              width: 64,
              height: 64,
              fit: BoxFit.cover,
              cacheWidth: (64 * MediaQuery.devicePixelRatioOf(context)).ceil(),
              loadStateChanged: (state) => state.extendedImageLoadState == LoadState.failed ? fallback : null,
            ),
    );
  }

  Widget _field(BuildContext context, MastodonField field) {
    final theme = Theme.of(context);
    final url = _webUrl(field.url);
    final verified = url != null && field.verifiedAt != null;
    return Semantics(
      link: url != null,
      child: Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Material(
          color: verified ? theme.colorScheme.secondaryContainer.withValues(alpha: 0.4) : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
          child: InkWell(
            borderRadius: BorderRadius.circular(8),
            onTap: url == null ? null : () => onFieldTap != null ? onFieldTap!(url) : openUri(context, url),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(field.name, style: theme.textTheme.labelMedium?.copyWith(fontWeight: FontWeight.w700)),
                        const SizedBox(height: 2),
                        Text(
                          field.value,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: url == null ? null : theme.colorScheme.primary,
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (verified)
                    Tooltip(
                      message: L10n.of(context).plugin_mastodon_verified_link,
                      child: Icon(Icons.verified_outlined, size: 20, color: theme.colorScheme.primary),
                    ),
                  if (url != null && !verified)
                    Icon(Icons.open_in_new, size: 18, color: theme.colorScheme.onSurfaceVariant),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _count(BuildContext context, int count, String label) => Semantics(
    label: '${NumberFormat.decimalPattern().format(count)} $label',
    excludeSemantics: true,
    child: Text.rich(
      TextSpan(
        children: [
          TextSpan(
            text: compactCount(count),
            style: const TextStyle(fontWeight: FontWeight.w700),
          ),
          TextSpan(
            text: ' $label',
            style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant),
          ),
        ],
      ),
      style: Theme.of(context).textTheme.bodyMedium,
    ),
  );
}
