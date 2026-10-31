# The PC release: starts on more systems, installs cleanly, works with a mouse and keyboard

The owner asked to improve the PC release (2026-10-30). The PC release is
the Linux desktop build (docs/desktop.md): the Arch package, its PKGBUILD
and a portable tarball on every release.

## Found

aimdi186, from its release assets and from the app running on a virtual
display:

- **It does not start on Ubuntu 22.04-based systems** (Linux Mint 21,
  Pop!_OS 22.04, Zorin OS 17, elementary OS 7). The video plugin is linked
  against `libmpv.so.2` (mpv 0.35 and later); those systems ship
  `libmpv.so.1`, so the loader stops before a window opens. The docs say the
  tarball runs on "any glibc distribution". glibc is not the limit: the
  bundle needs 2.34.
- Debian, Ubuntu and Mint users have no package: they unpack the tarball
  and copy the menu entry and icon by hand.
- **Starting XTA again opens a second copy.** Both write the same settings
  file and database, so the one that saves last wins and what was changed
  in the other is lost.
- The window always opens at 1100×820; its size and maximized state are
  forgotten.
- **The Home timeline cannot be refreshed with a mouse**: it refreshes by
  pulling down, which needs a touch drag, and has no refresh button.
- The full-screen picture viewer turns pages only with a touch drag: with
  a mouse it stays on the first picture of a post.
- Back is only the on-screen arrow: the mouse's back button, Alt+← and Esc
  do nothing.
- The menu entry has one 512 px icon and no AppStream data, which software
  centres use to show an app.

## After

### Getting it onto a PC

- **A Debian/Ubuntu package** for Debian 12 and later, Ubuntu 24.04 and
  later and Mint 22 and later: `sudo apt install ./xta_<version>_amd64.deb`
  installs XTA with its menu entry and icons, and the system's GTK 3 and
  libmpv2 with it. `sudo apt remove xta` removes it; the reader's data stays.
- **A second package for Ubuntu 22.04-based systems**,
  `xta_<version>~ubuntu22.04_amd64.deb`, built on Ubuntu 22.04 against its
  libmpv1, so XTA starts there and plays video with the system's mpv.
- **The tarball installs itself**: `./install.sh` adds the menu entry and
  icons for the current user and `./install.sh --uninstall` removes them.
- Icons at 32 to 512 px and AppStream metadata
  (`com.aimdi.xta.metainfo.xml`) in the tarball and every package.
- Every release attaches the two packages beside the tarball and the Arch
  package; `SHA256SUMS-linux` lists them all.

### Using it

- **One XTA at a time**: starting it again brings the open window to the
  front instead of opening a second copy. Without a session bus it starts
  as before.
- **The window reopens at its last size**, maximized if it was. The size is
  kept in `~/.local/share/com.aimdi.xta/window.ini`.
- **Back**: the mouse's back button and Alt+← go back from any page. Esc
  goes back too, after closing a dialog, sheet or menu first; typing in a
  page, the first Esc leaves the field.
- **Refresh**: on the desktop the Home timeline shows its refresh button,
  and F5 or Ctrl+R refreshes the timeline in view (Home, a group, a deck
  column).
- **Pictures**: in the full-screen viewers (X posts and every plugin's), ←
  and → turn the pages, and previous/next buttons sit at the sides while
  there is a picture that way.

Android is unchanged: each of these checks `isDesktop`.

### Not in this pass

Windows and macOS builds; Flatpak or AppImage; an in-app web view (articles
in the reader, X, Pixiv and Reddit sign-in); a layout for wide windows
(side navigation instead of the bottom bar); right-click menus (posts keep
their ⋯ button).

## Code

- `linux/runner/my_application.cc`: a unique application that brings its
  window forward when activated again.
- `linux/runner/window_state.{h,cc}` (new): the window's size and maximized
  state, read at start and written as they change.
- `packaging/linux/com.aimdi.xta.metainfo.xml`, `packaging/linux/install.sh`
  (new); `scripts/package_linux.sh` puts the icons, the metadata and the
  installer in the tarball; `scripts/package_deb.sh` (new) builds a package
  from that tarball; `packaging/arch/PKGBUILD.in` installs the icons and
  metadata.
- `.github/actions/build-linux`: builds the package too, for the system it
  runs on (libmpv2, or libmpv1 on Ubuntu 22.04).
- `.github/workflows/linux-desktop.yml`: also builds on Ubuntu 22.04 and
  attaches both packages.
- `.github/workflows/verify.yml`: builds both packages, installs each on its
  Ubuntu version and starts XTA on a virtual display.
- `lib/ui/desktop_navigation.dart` (new): back by mouse button and keys,
  and F5/Ctrl+R for a timeline.
- `lib/ui/desktop_pager.dart` (new): keys and buttons for a viewer's pages.
- `lib/main.dart`, `lib/home/_feed.dart`, `lib/group/_feed_shell.dart`,
  `lib/plugins/x/x_screen.dart`, the two picture viewers.
- `scripts/smoke_linux_package.sh` (new): the CI start check.
- `docs/desktop.md`, `README.md`.

## Tests

- Back: the mouse's back button, Alt+← and Esc go back from a page; Esc
  closes a dialog without leaving the page under it, even while typing in
  it; typing in a page, the first Esc leaves the field and the next goes
  back; none of them act off the desktop.
- Refresh: F5 and Ctrl+R refresh a timeline; Home's refresh button shows
  on the desktop only.
- Viewer: ← and → turn pages; the buttons show only where there is a page
  to turn to, and only on the desktop.
- Packages: CI installs each package on its Ubuntu version, starts XTA on a
  virtual display, and checks that its window opens and it keeps running;
  the package lists its files and dependencies; the metadata and menu entry
  pass `appstreamcli` and `desktop-file-validate`.
- Runner, by hand on a virtual display (documented in docs/desktop.md):
  a second start under a session bus leaves one process and one window;
  a resized window reopens at that size.
