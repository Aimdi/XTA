import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';

const _thumbSize = 132.0;
const _maxWorks = 12;

/// The author's other works as a row under an illust; nothing at all when there are none.
class PixivAuthorWorks extends StatefulWidget {
  final PixivIllust illust;

  const PixivAuthorWorks({super.key, required this.illust});

  @override
  State<PixivAuthorWorks> createState() => _PixivAuthorWorksState();
}

class _PixivAuthorWorksState extends State<PixivAuthorWorks> {
  late final PixivIllustListStore _works;
  late final Future<void> _loading;

  @override
  void initState() {
    super.initState();
    final client = context.read<PixivClient>();
    final mute = context.read<PixivMuteStore>();
    final seed = widget.illust;
    _works = PixivIllustListStore(
      ({nextUrl}) => client.userIllusts(seed.userId, nextUrl: nextUrl),
      filter: (illusts) => [
        for (final illust in mute.filter(illusts))
          if (illust.id != seed.id) illust,
      ],
    );
    _loading = _works.refresh();
  }

  @override
  void dispose() {
    // A load still in flight writes to the store when it lands; destroy it after.
    unawaited(_loading.whenComplete(_works.destroy));
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<PixivIllustListStore, List<PixivIllust>>(
    store: _works,
    onState: (context, works) => works.isEmpty ? const SizedBox.shrink() : _strip(context, works),
  );

  Widget _strip(BuildContext context, List<PixivIllust> works) {
    final theme = Theme.of(context);
    final shown = works.take(_maxWorks).toList();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        InkWell(
          key: const ValueKey('pixiv-author-works-header'),
          onTap: () => openPixivUser(context, widget.illust.userId),
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: kMinInteractiveDimension),
            child: Padding(
              padding: const EdgeInsetsDirectional.fromSTEB(16, 8, 12, 8),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      L10n.of(context).plugin_pixiv_more_by(widget.illust.userName),
                      style: theme.textTheme.titleMedium!.copyWith(fontWeight: FontWeight.w700),
                    ),
                  ),
                  Icon(Icons.chevron_right, color: theme.colorScheme.onSurfaceVariant),
                ],
              ),
            ),
          ),
        ),
        SizedBox(
          height: _thumbSize,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: shown.length,
            separatorBuilder: (_, _) => const SizedBox(width: 8),
            itemBuilder: (context, index) => _thumb(context, shown, index),
          ),
        ),
        const SizedBox(height: 8),
      ],
    );
  }

  Widget _thumb(BuildContext context, List<PixivIllust> shown, int index) {
    final illust = shown[index];
    void open() => openPixivIllustFromList(context, shown, index);
    return Semantics(
      button: true,
      label: illust.title,
      onTap: open,
      excludeSemantics: true,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: SizedBox.square(
          dimension: _thumbSize,
          child: Stack(
            fit: StackFit.expand,
            children: [
              ColoredBox(color: Theme.of(context).colorScheme.surfaceContainerHighest),
              PixivNetworkImage(
                url: illust.thumbnailUrl,
                fit: BoxFit.cover,
                cacheWidth: (_thumbSize * MediaQuery.devicePixelRatioOf(context)).ceil(),
                loadStateChanged: (state) => pixivTileLoadState(context, state),
              ),
              Material(
                type: MaterialType.transparency,
                child: InkWell(key: ValueKey('pixiv-author-work-${illust.id}'), onTap: open),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
