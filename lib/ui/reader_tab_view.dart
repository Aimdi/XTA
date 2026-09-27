import 'package:flutter/material.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/reader_swipe_navigation.dart';

/// Tab content with the same release and cancellation policy as Home.
/// The existing TabController still drives buttons, lazy pages and restoration.
class ReaderTabView extends StatefulWidget {
  final TabController? controller;
  final List<Widget> children;

  const ReaderTabView({super.key, this.controller, required this.children});

  @override
  State<ReaderTabView> createState() => _ReaderTabViewState();
}

class _ReaderTabViewState extends State<ReaderTabView> with TickerProviderStateMixin {
  TabController? _source;
  TabController? _reduced;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _bind();
  }

  @override
  void didUpdateWidget(ReaderTabView oldWidget) {
    super.didUpdateWidget(oldWidget);
    _bind();
  }

  void _bind() {
    final source = widget.controller ?? DefaultTabController.of(context);
    if (_source != source) {
      _source?.removeListener(_sync);
      _source = source..addListener(_sync);
    }
    final reduce = xtaReduceMotion(context);
    if (!reduce || _reduced?.length != source.length) {
      _reduced?.dispose();
      _reduced = null;
    }
    // TabBarView uses its controller's fixed animationDuration, ignoring the
    // duration of animateTo. A presentation controller preserves its mounted
    // pages while making reduced-motion changes jump immediately.
    if (reduce) {
      _reduced ??= TabController(
        length: source.length,
        initialIndex: source.index,
        animationDuration: Duration.zero,
        vsync: this,
      );
    }
    _sync();
  }

  void _sync() {
    if (_reduced != null && _reduced!.index != _source!.index) _reduced!.index = _source!.index;
  }

  @override
  Widget build(BuildContext context) {
    return ReaderTabNavigation(
      controller: _source!,
      child: TabBarView(
        controller: _reduced ?? _source,
        physics: const NeverScrollableScrollPhysics(),
        children: widget.children,
      ),
    );
  }

  @override
  void dispose() {
    _source?.removeListener(_sync);
    _reduced?.dispose();
    super.dispose();
  }
}

/// The same controller adapter for readers that build only their active pane.
class ReaderTabNavigation extends StatelessWidget {
  final TabController controller;
  final Widget child;

  const ReaderTabNavigation({super.key, required this.controller, required this.child});

  @override
  Widget build(BuildContext context) {
    final tabs = controller;
    return AnimatedBuilder(
      animation: tabs,
      child: child,
      builder: (context, child) => ReaderSwipeNavigation(
        index: tabs.index,
        count: tabs.length,
        identity: tabs,
        onChanged: (index) {
          if (tabs.indexIsChanging || tabs.index == index) return false;
          tabs.animateTo(index, duration: xtaMotionDuration(context, tabs.animationDuration));
          return true;
        },
        child: child!,
      ),
    );
  }
}
