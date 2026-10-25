# Running XTA against a stand-in for X

XTA can be built to talk to a local stand-in for X instead of x.com, so its
screens can be exercised on an emulator with no X account, no live request and
no rate limit. The stand-in answers from the recorded responses in
`test/fixtures`; the same flows then run on GitHub Actions and leave a video of
each run.

Adapted from QuaX's `tool/mock_x` (MIT): QuaX matches each request against a
full browser capture, while XTA's fixtures are trimmed bodies, so XTA's
stand-in picks a fixture by the operation and the one variable that names what
was asked for (a screen name, a post id, a community id).

## The pieces

| Piece | What it does |
|---|---|
| `lib/utils/mock_x.dart` | With `--dart-define=XTA_MOCK_X=<url>`, sends every request for x.com, api.x.com and twitter.com to `<url>/<host>/<path>` and signs with a fixed key. Other sources (Substack, Reddit, images, videos) keep using the network. The sign-in prompt is skipped, since the stand-in answers a guest. Off unless the define is set. |
| `lib/utils/mock_x_urls.dart` | The address mapping both sides share. |
| `tool/mock_x/mock_x.dart` | The stand-in's answers: `mockRoutes` maps each operation to fixtures; an unknown account, post or community gets X's "does not exist" answer; a request with a cursor gets the recorded page with its entries emptied, which ends the list; anything else gets a 501 naming what is recorded. |
| `tool/mock_x/server.dart` | Serves the stand-in on `http://localhost:8787` and logs each answer. |
| `tool/mock_x/emulator_qa.dart` | Serves the stand-in, installs an APK on the running emulator, starts it past the intro, makes x.com links open it, and runs each Maestro flow of `.maestro/` while recording the screen. |
| `.maestro/*.yaml` | The flows: a profile and its posts, a post, an account that does not exist, a community with its rules and members. |
| `.github/workflows/emulator-qa.yml` | Runs the flows on an API 34 emulator for pull requests touching these files or the screens they cover, and on demand. Videos, Maestro's output, the stand-in's log and logcat are kept as the `emulator-qa` artifact. |
| `test/mock_x_test.dart` | Sends XTA's own client calls through the real server over loopback and fails when one of them is no longer answered, so the fixtures and the app's requests cannot drift apart unnoticed. |

Debug and profile builds allow cleartext HTTP so they can reach the stand-in on
localhost (`android/app/src/{debug,profile}/AndroidManifest.xml`); release
builds do not.

## Running it

On a device or emulator, from the repository root:

```bash
fvm dart run tool/mock_x/server.dart          # listens on http://localhost:8787
adb reverse tcp:8787 tcp:8787                 # lets the device reach it
fvm flutter run --debug --dart-define=XTA_MOCK_X=http://localhost:8787
```

The whole emulator run, as CI does it:

```bash
fvm flutter build apk --profile --dart-define=XTA_MOCK_X=http://localhost:8787
fvm dart run tool/mock_x/emulator_qa.dart \
  build/app/outputs/flutter-apk/app-profile.apk .maestro emulator-output
```

The second argument can also be one `.yaml` flow. With `--port`, build the APK
with the same port. The run needs `adb`, `maestro` and `$ANDROID_HOME`; `ffmpeg`
is optional and trims Maestro's start-up from the videos. It fails when a flow
fails or the stand-in answered nothing. Flows read the app id from `APP_ID`,
which the script passes from the APK.

Without `/dev/kvm` (cloud sessions) the emulator cannot run here; the workflow
runs it on GitHub's runners instead.

## Coverage

What the stand-in holds:

- profiles: `@quax_tests`, `@quax_tests_2` and `@X`, with one recorded page of
  posts (`UserOriginalsTimeline/page.json`) for any of them, also under Posts &
  replies;
- the post x.com/Osemka8/status/2105589900078641579;
- the "Flutter" community (1472249315724771329): header, posts, members, and a
  community search;
- a guest token.

Any other account answers "User not found", any other post an empty thread.
Home, search, trends, lists and translations answer 501, and the app shows its
error for them. Adding a screen to the flows means adding its recordings to
`test/fixtures` (see its README) and a route in `mockRoutes`.

## Writing a flow

Flows match the text Flutter exposes to accessibility. A tab's label carries
its position (`About\nTab 2 of 2`), so match it with `"(?s)About.*"`; match
text inside a longer label with `"(?s).*text.*"`. Every flow starts with
`launchApp` and opens its screen with `openLink`, so flows do not depend on one
another.
