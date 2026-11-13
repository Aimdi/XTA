# Contributing to XTA

Contributions are welcomed and encouraged! Whether you want to fix a bug, add a
feature, improve documentation, or translate the app, every contribution helps.

The app is XTA Native, in [`native/`](native): Kotlin and Compose
Multiplatform, for Android and the Linux desktop.

## Bug Reports

Before opening a bug report, please verify the following:

1. **Reproduce on the website first**: check whether the same issue occurs on
   [x.com](https://x.com) (or the network's own site) in a browser. If the site
   itself has the issue, it is outside the scope of XTA.
2. **Search existing issues**: the bug may already be reported at
   [github.com/Aimdi/XTA/issues](https://github.com/Aimdi/XTA/issues).

If the bug is specific to XTA, open an issue and include:

- A clear description of the issue
- Steps to reproduce it
- Expected vs. actual behavior
- The XTA version (Settings shows it), and your Android version or Linux
  distribution
- The network or plugin involved, and its version from the plugin store
- Relevant screenshots, or the report from Settings → Diagnostics

## Feature Requests

Open an issue describing the feature and why it would be useful. XTA is a
privacy-focused, read-only client: features that post or write to a network,
or that compromise privacy, will not be accepted.

## Pull Requests

1. Fork the repository and create a branch from the default branch.
2. Make your changes, keeping commits focused and readable. Read
   [CLAUDE.md](CLAUDE.md) first: it lists the rules (read-only, pinned
   dependencies, the plugin API, translations in every language).
3. Run the tests from `native/`:
   `./gradlew :core:test :networks:test :store:test :services:test :plugin-host:test :plugins:test :ui:desktopTest`.
   Without Gradle's network, see [`native/tool/README.md`](native/tool/README.md).
4. Test your changes on a real device or emulator, or on the Linux desktop.
5. Open a pull request with a clear description of what you changed and why.
   CI builds the APK and the Linux app, and walks every screen on an emulator.

## License

By contributing, you agree that your contributions will be licensed under
the [MIT License](/LICENSE).
