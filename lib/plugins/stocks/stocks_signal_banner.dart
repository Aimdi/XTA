import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_signal_posts.dart';
import 'package:xta/ui/motion.dart';

/// What the reader chose per signal-group post: folded or open. A post they
/// did not touch follows the "Fold signal-group posts" setting. Kept by the
/// feed, so a post folded and scrolled away is still folded on the way back.
class StocksSignalFolds extends Store<Map<String, bool>> {
  StocksSignalFolds() : super(const {});

  void choose(String id, {required bool folded}) => update({...state, id: folded});
}

/// [post] under the amber banner when [chain] has a signal group's shape,
/// else [post] as it is.
Widget stocksSignalChain(StocksSignalFolds folds, TweetChain chain, Widget post) {
  final cashtags = signalGroupCashtags(chain);
  if (cashtags == null) return post;
  return StocksSignalChain(
    key: ValueKey('signal-${chain.id}'),
    folds: folds,
    id: chain.id,
    cashtags: cashtags,
    post: post,
  );
}

class StocksSignalChain extends StatelessWidget {
  final StocksSignalFolds folds;
  final String id;
  final int cashtags;
  final Widget post;

  const StocksSignalChain({
    super.key,
    required this.folds,
    required this.id,
    required this.cashtags,
    required this.post,
  });

  @override
  Widget build(BuildContext context) {
    final byDefault = PrefService.of(context).get<bool>(optionPluginStocksFoldSignalPosts) ?? false;
    return ScopedBuilder<StocksSignalFolds, Map<String, bool>>(
      store: folds,
      distinct: (choices) => choices[id],
      onState: (context, choices) {
        final folded = choices[id] ?? byDefault;
        return XtaAnimatedSwitcher(
          animateSize: true,
          child: KeyedSubtree(
            key: ValueKey(folded),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                StocksSignalBanner(
                  cashtags: cashtags,
                  folded: folded,
                  onToggle: () => folds.choose(id, folded: !folded),
                ),
                if (!folded) post,
              ],
            ),
          ),
        );
      },
    );
  }
}

/// "Possible signal group: Telegram invite and 4 cashtags" on an amber tint.
/// Open, it ends in Hide; folded, the whole line opens the post again.
class StocksSignalBanner extends StatelessWidget {
  final int cashtags;
  final bool folded;
  final VoidCallback onToggle;

  const StocksSignalBanner({super.key, required this.cashtags, required this.folded, required this.onToggle});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final tint = Color.alphaBlend(kStockWarningColour.withValues(alpha: 0.12), theme.scaffoldBackgroundColor);
    final ink = stockWarningColour(context, background: tint);
    final line = Row(
      children: [
        Icon(Icons.warning_amber_rounded, size: 18, color: ink),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            l10n.plugin_stocks_signal_group(cashtags),
            style: theme.textTheme.bodySmall!.copyWith(fontSize: 13, fontWeight: FontWeight.w500, color: ink),
          ),
        ),
        if (folded)
          Padding(
            padding: const EdgeInsetsDirectional.symmetric(horizontal: 12),
            child: Icon(Icons.expand_more, size: 20, color: ink),
          )
        else
          TextButton(
            key: const ValueKey('stocks-signal-hide'),
            onPressed: onToggle,
            style: TextButton.styleFrom(foregroundColor: ink, minimumSize: const Size(48, 48)),
            child: Text(l10n.hide),
          ),
      ],
    );
    final banner = Container(
      color: tint,
      constraints: const BoxConstraints(minHeight: 48),
      padding: const EdgeInsetsDirectional.fromSTEB(16, 0, 4, 0),
      alignment: AlignmentDirectional.centerStart,
      child: line,
    );
    if (!folded) return banner;
    return Semantics(
      button: true,
      child: Material(
        type: MaterialType.transparency,
        child: InkWell(key: const ValueKey('stocks-signal-open'), onTap: onToggle, child: banner),
      ),
    );
  }
}
