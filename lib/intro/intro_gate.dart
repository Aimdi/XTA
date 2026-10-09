import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/intro/intro_store.dart';

/// Shows [intro] until the cards have been seen, then [home].
class IntroGate extends StatelessWidget {
  final IntroStore store;
  final Widget home;
  final Widget intro;

  const IntroGate({super.key, required this.store, required this.home, required this.intro});

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<IntroStore, bool>(store: store, onState: (_, seen) => seen ? home : intro);
  }
}

/// Replays the cards: the root swaps back to the intro, and whatever Settings
/// screens sit above it are popped so it is actually on screen.
Future<void> showIntroAgain(BuildContext context) async {
  final navigator = Navigator.of(context);
  await context.read<IntroStore>().showAgain();
  navigator.popUntil((route) => route.isFirst);
}
