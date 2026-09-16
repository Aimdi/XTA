# Reader session checks

Use a configured test install on a real Android / GrapheneOS phone. These steps
are not evidence of a device pass until their results are recorded. No device
was attached during implementation.

## Repeatable profile/back journey

Finish setup, close update dialogs, and select an X feed. From the pinned SDK:

```bash
fvm flutter drive --profile \
  --driver=test_driver/reader_session_test.dart \
  --target=integration_test/reader_session_test.dart \
  --dart-define=XTA_TEST_PROFILE=FlutterDev
```

This opens and closes a profile during loading 30 times, fails on navigation
errors or framework exceptions, and writes actual frame traces and return
latencies to `build/reader_session_summary.json`. It uses bounded frame pumps,
so an endless spinner cannot make a settle call wait indefinitely.

For feed scrolling, run the existing `feed_scroll_perf_test.dart` driver and
record its actual numbers in `docs/perf-baseline.md`.

## Connection / lifecycle journey (manual device controls)

1. Open a group with enough accounts for multiple batches. Confirm completed
   batches become readable and loading progress continues. Scroll while more
   batches finish: the post being read should stay in place.
2. Disable connectivity from the system controls during loading, return to XTA,
   and wait for a recoverable failure. Existing posts must remain visible.
3. Restore Wi-Fi or mobile data. Only visible failed reads should retry, once;
   successful sources and authentication/rate-limit errors should not restart.
4. Repeat while switching Wi-Fi to mobile data and with a captive portal. A
   captive portal must not trigger retries before Android validates internet.
5. Background XTA for one minute and return. Check that it retries recoverable
   errors without clearing posts or restarting successful feeds.
6. During a stalled profile load, go back, subscribe to a test account, change
   its groups, then Undo. Confirm the original subscription fields and groups
   return. Repeat removal for a saved search and an enabled plugin subscription.
7. Load older group pages, induce one failed batch, then retry the failed batch.
   Older pages and the reading position must remain intact.
8. Run a 20-minute mixed session with photos/videos, profiles and group changes.
   Capture `adb shell dumpsys meminfo com.aimdi.xta` before/after and export the
   in-app diagnostics if a loading operation stalls. Do not paste account
   credentials or private content into public reports.

## Backup journey

Highlight an article, attach a note, add tags and extract text. Export Saved
posts (which includes article annotations), then restore on a separate test
install. Verify all annotations. Restore an older backup over a newer note:
newer local text must survive and missing highlights/tags should be added.
Legacy version 0/1 files must remain importable; older apps should reject new
version 2 backups instead of silently dropping annotations.

Record phone model, OS/build, XTA commit, network conditions, actual measured
results and any failures. Unit/CI passes do not substitute for these checks.
