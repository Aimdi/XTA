import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/accounts.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_model.dart';
import 'package:xta/intro/intro_account_page.dart';
import 'package:xta/intro/intro_appearance_page.dart';
import 'package:xta/intro/intro_illustrations.dart';
import 'package:xta/intro/intro_page_frame.dart';
import 'package:xta/intro/intro_sources_page.dart';
import 'package:xta/intro/intro_store.dart';
import 'package:xta/ui/confirm_close.dart';
import 'package:xta/ui/motion.dart';

/// The first-launch cards. Swipe or Next moves on, Skip leaves everything as
/// it is, and system Back steps to the previous card until the first one,
/// where it asks before closing the app exactly as Home does.
class IntroScreen extends StatefulWidget {
  final int initialPage;
  final Future<List<Account>> Function() accountsLoader;

  const IntroScreen({super.key, this.initialPage = 0, this.accountsLoader = getAccounts});

  @override
  State<IntroScreen> createState() => _IntroScreenState();
}

class _IntroScreenState extends State<IntroScreen> {
  late final PageController _pager = PageController(initialPage: widget.initialPage);
  late final IntroPageStore _page = IntroPageStore(widget.initialPage);
  late final IntroAccountsStore _accounts = IntroAccountsStore(widget.accountsLoader);

  @override
  void initState() {
    super.initState();
    unawaited(_accounts.refresh());
  }

  @override
  void dispose() {
    _pager.dispose();
    _page.destroy();
    _accounts.destroy();
    super.dispose();
  }

  Future<void> _show(int page) async {
    if (xtaReduceMotion(context)) {
      _pager.jumpToPage(page);
      return;
    }
    await _pager.animateToPage(page, duration: kXtaMotionNavigation, curve: Curves.easeOutCubic);
  }

  void _next() => unawaited(_show(_page.state + 1));

  /// Leaving by Skip is finishing too: a source toggled on an earlier card
  /// must reach Home's tabs now, not on the next launch.
  void _skip() => unawaited(_finish());

  bool _finishing = false;

  /// Runs once however often the button is tapped while it works.
  Future<void> _finish() async {
    if (_finishing) return;
    _finishing = true;
    final home = context.read<HomeModel>();
    final intro = context.read<IntroStore>();
    try {
      await home.loadPages();
      await intro.markSeen();
    } finally {
      _finishing = false;
    }
  }

  void _onPopInvoked(bool didPop, Object? result) {
    if (didPop) return;
    if (_page.state > 0) {
      unawaited(_show(_page.state - 1));
    } else {
      unawaited(confirmCloseApp(context));
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: _onPopInvoked,
      child: PageView(
        controller: _pager,
        physics: const PageScrollPhysics(),
        onPageChanged: _page.show,
        children: [
          IntroWelcomePage(page: 0, onNext: _next, onSkip: _skip),
          IntroDevicePage(page: 1, onNext: _next, onSkip: _skip),
          IntroAppearancePage(page: 2, onNext: _next, onSkip: _skip),
          IntroAccountPage(page: 3, accounts: _accounts, onNext: _next, onSkip: _skip),
          IntroSourcesPage(page: 4, onFinish: _finish),
        ],
      ),
    );
  }
}

/// Welcome: what XTA is, and what it never does.
class IntroWelcomePage extends StatelessWidget {
  final int page;
  final VoidCallback onNext;
  final VoidCallback onSkip;

  const IntroWelcomePage({super.key, required this.page, required this.onNext, required this.onSkip});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return IntroPageFrame(
      page: page,
      title: l10n.intro_welcome_title,
      body: l10n.intro_welcome_body,
      onSkip: onSkip,
      card: const IntroOrbitIllustration(),
      footer: IntroButtonRow.next(l10n, onNext),
    );
  }
}

/// Stays on this device: where the reader's own data lives.
class IntroDevicePage extends StatelessWidget {
  final int page;
  final VoidCallback onNext;
  final VoidCallback onSkip;

  const IntroDevicePage({super.key, required this.page, required this.onNext, required this.onSkip});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return IntroPageFrame(
      page: page,
      title: l10n.intro_device_title,
      body: l10n.intro_device_body,
      footnote: l10n.intro_device_footnote,
      onSkip: onSkip,
      card: const IntroPhoneIllustration(),
      footer: IntroButtonRow.next(l10n, onNext),
    );
  }
}
