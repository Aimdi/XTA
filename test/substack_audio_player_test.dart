import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/podcast_store.dart';
import 'package:xta/plugins/substack/substack_audio_player.dart';

import 'support/continuity_player_fake.dart';

void main() {
  testWidgets('the player row picks the playback speed from the offered ones', (tester) async {
    final store = PodcastStore(
      prefs: PrefServiceCache(),
      createPlayer: FakeContinuityPlayer.new,
      observeLifecycle: false,
    );
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: Scaffold(
          body: Provider<PodcastStore>.value(
            value: store,
            child: const SubstackAudioPlayer(url: 'https://cdn.example/e.mp3', title: 'Episode'),
          ),
        ),
      ),
    );

    expect(find.byTooltip(L10n.current.playback_speed), findsOneWidget);
    await tester.tap(find.text('1×'));
    await tester.pumpAndSettle();
    expect(find.byType(CheckedPopupMenuItem<double>), findsNWidgets(podcastSpeeds.length));
    await tester.tap(find.widgetWithText(CheckedPopupMenuItem<double>, '1.5×'));
    await tester.pumpAndSettle();

    expect(store.state.speed, 1.5);
    expect(find.text('1.5×'), findsOneWidget);
  });
}
