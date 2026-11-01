import 'package:flutter/material.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/ui/contrast.dart';

/// A list's title and, on the right, what the reader should know about it:
/// `Indizes  ● Geschlossen, Schluss Fr., 30.10.` or `Meine Watchlist  2 Ticker`.
class StocksSectionHeader extends StatelessWidget {
  final String title;
  final String? status;

  /// A green dot before [status] for markets trading now, a grey one for
  /// closed ones; none when the status is not about a market.
  final bool? live;

  const StocksSectionHeader({super.key, required this.title, this.status, this.live});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final background = theme.scaffoldBackgroundColor;
    return Semantics(
      header: true,
      child: Padding(
        padding: const EdgeInsetsDirectional.fromSTEB(16, 16, 16, 8),
        // The title keeps what it needs, up to three fifths beside a state,
        // which takes the rest, its dot beside it at the end of the line.
        child: LayoutBuilder(
          builder: (context, constraints) => Row(
            children: [
              ConstrainedBox(
                constraints: BoxConstraints(maxWidth: constraints.maxWidth * (status == null ? 1 : 0.6)),
                child: Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.titleSmall!.copyWith(fontSize: 15, fontWeight: FontWeight.w700),
                ),
              ),
              if (status != null) ...[
                const SizedBox(width: 12),
                Expanded(
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      if (live != null) ...[
                        ExcludeSemantics(
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: live! ? ensureContrast(kStockUpColour, background) : muted.withValues(alpha: 0.7),
                            ),
                            child: const SizedBox.square(dimension: 6),
                          ),
                        ),
                        const SizedBox(width: 6),
                      ],
                      Flexible(
                        child: Text(
                          status!,
                          maxLines: 2,
                          textAlign: TextAlign.end,
                          style: theme.textTheme.labelSmall!.copyWith(fontSize: 12, color: muted),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
