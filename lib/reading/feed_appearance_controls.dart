import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/reading/feed_appearance_scope.dart';
import 'package:xta/reading/feed_appearance_store.dart';

Future<void> showFeedAppearance(BuildContext context, FeedIdentity feed, {FeedAppearanceStore? store, String? label}) {
  final shared = store ?? FeedAppearanceStore.forPrefs(PrefService.of(context, listen: false));
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    constraints: BoxConstraints(maxHeight: MediaQuery.sizeOf(context).height * .85),
    builder: (context) => SafeArea(
      top: false,
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              title: Text(L10n.of(context).feed_appearance),
              subtitle: Text(label ?? feedAppearanceLabel(context, feed, shared)),
              trailing: IconButton(
                tooltip: L10n.of(context).close,
                onPressed: () => Navigator.pop(context),
                icon: const Icon(Icons.close),
              ),
            ),
            FeedAppearanceControls(feed: feed, store: shared),
          ],
        ),
      ),
    ),
  );
}

class FeedAppearanceAction extends StatelessWidget {
  final FeedIdentity feed;
  final FeedAppearanceStore store;
  final String? label;
  const FeedAppearanceAction({super.key, required this.feed, required this.store, this.label});
  @override
  Widget build(BuildContext context) => ListTile(
    key: const ValueKey('feed-appearance-action'),
    minTileHeight: 48,
    leading: const Icon(Icons.palette_outlined),
    title: Text(L10n.of(context).feed_appearance),
    subtitle: label == null ? null : Text(label!),
    onTap: () {
      Navigator.pop(context);
      showFeedAppearance(context, feed, store: store, label: label);
    },
  );
}

class _ControlsWrites extends Store<bool> {
  bool _closed = false;
  _ControlsWrites() : super(false);
  Future<bool> run(Future<bool> Function() write) async {
    if (_closed || state) return false;
    update(true);
    final saved = await write();
    if (!_closed) update(false);
    return saved;
  }

  @override
  Future<void> destroy() async {
    _closed = true;
    await super.destroy();
  }
}

class FeedAppearanceControls extends StatefulWidget {
  final FeedIdentity feed;
  final FeedAppearanceStore store;
  const FeedAppearanceControls({super.key, required this.feed, required this.store});
  @override
  State<FeedAppearanceControls> createState() => _FeedAppearanceControlsState();
}

class _FeedAppearanceControlsState extends State<FeedAppearanceControls> {
  final _writes = _ControlsWrites();
  Future<void> _modify(FeedAppearance Function(FeedAppearance) edit) async {
    final message = L10n.of(context).appearance_save_failed;
    final saved = await _writes.run(() => widget.store.modify(widget.feed, edit));
    if (mounted && !saved) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  Widget _choice<T>(String key, String label, T? value, List<DropdownMenuItem<T>> items, ValueChanged<T?> change) =>
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(label, style: Theme.of(context).textTheme.titleSmall),
            ConstrainedBox(
              constraints: const BoxConstraints(minHeight: 48),
              child: DropdownButton<T>(
                key: ValueKey(key),
                value: value,
                isExpanded: true,
                itemHeight: null,
                items: items,
                hint: Text(L10n.of(context).appearance_source_default),
                onChanged: _writes.state ? null : change,
              ),
            ),
          ],
        ),
      );

  @override
  Widget build(BuildContext context) => ScopedBuilder<_ControlsWrites, bool>(
    store: _writes,
    onState: (_, _) => ScopedBuilder<FeedAppearanceStore, Map<FeedIdentity, FeedAppearance>>(
      store: widget.store,
      onState: (context, _) {
        final value = widget.store.appearance(widget.feed);
        final l10n = L10n.of(context);
        final boolItems = [
          DropdownMenuItem<bool>(value: null, child: Text(l10n.appearance_source_default)),
          DropdownMenuItem<bool>(value: true, child: Text(l10n.show)),
          DropdownMenuItem<bool>(value: false, child: Text(l10n.hide)),
        ];
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            _choice(
              'appearance-preset',
              l10n.feed_appearance,
              value.preset,
              [
                DropdownMenuItem<FeedPreset>(value: null, child: Text(l10n.appearance_source_default)),
                DropdownMenuItem(value: FeedPreset.compact, child: Text(l10n.appearance_compact)),
                DropdownMenuItem(value: FeedPreset.gallery, child: Text(l10n.appearance_gallery)),
                DropdownMenuItem(value: FeedPreset.reading, child: Text(l10n.home_sources_reading)),
              ],
              (preset) => _modify(
                (old) => FeedAppearance(
                  preset: preset,
                  counts: old.counts,
                  linkPreviews: old.linkPreviews,
                  media: old.media,
                ),
              ),
            ),
            _choice(
              'appearance-counts',
              l10n.appearance_counts,
              value.counts,
              boolItems,
              (counts) => _modify(
                (old) => FeedAppearance(
                  preset: old.preset,
                  counts: counts,
                  linkPreviews: old.linkPreviews,
                  media: old.media,
                ),
              ),
            ),
            _choice(
              'appearance-links',
              l10n.appearance_links,
              value.linkPreviews,
              boolItems,
              (links) => _modify(
                (old) => FeedAppearance(preset: old.preset, counts: old.counts, linkPreviews: links, media: old.media),
              ),
            ),
            _choice(
              'appearance-media',
              l10n.media,
              value.media,
              boolItems,
              (media) => _modify(
                (old) => FeedAppearance(
                  preset: old.preset,
                  counts: old.counts,
                  linkPreviews: old.linkPreviews,
                  media: media,
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: TextButton.icon(
                key: const ValueKey('appearance-reset'),
                style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
                onPressed: _writes.state ? null : () => _modify((_) => const FeedAppearance()),
                icon: const Icon(Icons.restore),
                label: Text(l10n.appearance_reset),
              ),
            ),
            if (_writes.state) const LinearProgressIndicator(),
          ],
        );
      },
    ),
  );
  @override
  void dispose() {
    _writes.destroy();
    super.dispose();
  }
}
