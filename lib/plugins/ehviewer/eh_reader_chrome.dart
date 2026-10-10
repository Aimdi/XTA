import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_reader_sheets.dart';
import 'package:xta/plugins/ehviewer/eh_reader_store.dart';
import 'package:xta/ui/motion.dart';

const _barColor = Color(0xD9000000);

/// The reader's app bar and page bar, laid over the pages; the space between lets taps through.
class EhReaderChrome extends StatelessWidget {
  final String title;
  final EhReaderState state;
  final int total;
  final ValueChanged<int> onScrub;
  final ValueChanged<int> onJump;
  final VoidCallback onCounter;
  final VoidCallback onPickMode;

  const EhReaderChrome({
    super.key,
    required this.title,
    required this.state,
    required this.total,
    required this.onScrub,
    required this.onJump,
    required this.onCounter,
    required this.onPickMode,
  });

  @override
  Widget build(BuildContext context) => Column(
    children: [
      _EhChromeSlide(
        visible: state.chromeVisible,
        hidden: const Offset(0, -1),
        child: AppBar(
          backgroundColor: _barColor,
          foregroundColor: Colors.white,
          surfaceTintColor: Colors.transparent,
          title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
        ),
      ),
      const Spacer(),
      _EhChromeSlide(visible: state.chromeVisible, hidden: const Offset(0, 1), child: _pageBar(context)),
    ],
  );

  Widget _pageBar(BuildContext context) {
    final l10n = L10n.of(context);
    return Material(
      color: _barColor,
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsetsDirectional.fromSTEB(4, 4, 4, 4),
          child: Row(
            children: [
              Expanded(child: total > 1 ? _slider(l10n) : const SizedBox.shrink()),
              ConstrainedBox(
                constraints: BoxConstraints(maxWidth: MediaQuery.sizeOf(context).width * 0.4),
                child: Tooltip(
                  message: l10n.plugin_eh_jump_to_page,
                  child: TextButton(
                    key: const ValueKey('eh-reader-counter'),
                    style: TextButton.styleFrom(foregroundColor: Colors.white),
                    onPressed: onCounter,
                    child: Text(
                      l10n.plugin_eh_page_of(state.shownPage, total),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ),
              ),
              IconButton(
                key: const ValueKey('eh-reader-mode'),
                color: Colors.white,
                tooltip: l10n.plugin_eh_reader_mode,
                onPressed: onPickMode,
                icon: Icon(ehReadingModeIcon(state.mode)),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// Runs the way the pages do, so page 1 sits on the right when reading right to left.
  Widget _slider(L10n l10n) => Directionality(
    textDirection: state.mode.reversed ? TextDirection.rtl : TextDirection.ltr,
    child: Slider(
      key: const ValueKey('eh-reader-slider'),
      min: 1,
      max: total.toDouble(),
      divisions: total - 1,
      value: state.shownPage.clamp(1, total).toDouble(),
      label: '${state.shownPage}',
      semanticFormatterCallback: (value) => l10n.plugin_eh_page_of(value.round(), total),
      onChanged: (value) => onScrub(value.round()),
      onChangeEnd: (value) => onJump(value.round()),
    ),
  );
}

class _EhChromeSlide extends StatelessWidget {
  final bool visible;
  final Offset hidden;
  final Widget child;

  const _EhChromeSlide({required this.visible, required this.hidden, required this.child});

  @override
  Widget build(BuildContext context) => ExcludeSemantics(
    excluding: !visible,
    child: IgnorePointer(
      ignoring: !visible,
      child: AnimatedSlide(
        offset: visible ? Offset.zero : hidden,
        duration: xtaMotionDuration(context, kXtaMotionStandard),
        curve: Curves.easeOutCubic,
        child: child,
      ),
    ),
  );
}
