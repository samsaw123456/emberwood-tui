# Installation and distribution

The distribution targets glibc-based Linux and macOS on x86-64 or ARM64. The release is the
same portable archive on all four targets. It contains the game JAR and its
sources; the installer supplies a private, checksum-verified Temurin **25.0.5+8**
JDK for the current platform. It never changes system Java. Missing Linux system
packages are installed automatically; this step uses sudo when the current user
is not root. An already prepared machine needs no administrator access.

## For players

```sh
curl -fsSL https://github.com/samsaw123456/emberwood-tui/releases/download/v1.0.2/install.sh | bash
```

To inspect before executing, download `install.sh` from the release, read it, then
run `bash install.sh`. A local checkout can be installed with `./install.sh`.
Both routes install under `~/.local` and create `~/.local/bin/emberwood-tui`.
Add that bin directory to `PATH` if the installer reports it is missing.

The first installation needs an internet connection and Bash. If the `curl`
command or certificate store is absent, download `install.sh` in a browser and run
`bash ~/Downloads/install.sh`; a fetch command cannot repair itself before the
script is obtained. The installer automatically prepares missing utilities,
certificates, fontconfig/fonts and ALSA through apt, dnf, pacman or zypper on
supported glibc Linux systems. It installs a terminal if a graphical session has
none usable by the desktop launcher; an SSH/headless system does not get a desktop environment. Package-manager
errors stop installation before game files are replaced. No TLS verification is
disabled, and no full-system upgrade is requested. Stock macOS supplies the
required utilities and Terminal.app; a damaged system installation or PATH gets a
specific error rather than an unnecessary Homebrew installation.

`./install.sh --dependencies-only` prepares just these prerequisites.
`./install.sh --dependencies-only --offline` only checks play requirements and
never downloads or installs packages. The source launcher uses this same
preflight. An offline installed game does not need a downloader or CA bundle.

The installer verifies the downloaded
game archive against the release's SHA256SUMS, and Java against the pinned vendor
hash in `tools/jdk-downloads.tsv`. HTTPS and the publisher's release account are
the trust roots; the adjacent release checksum detects corruption, not a
compromised publisher. After installation the game runs offline.

Linux gets an **Emberwood Terminal** application-menu item with the original fox
icon, registered with `Terminal=true` so the desktop opens its configured terminal.
Menu launches pass `--desktop`, asking the terminal for 120×40 cells before the
game starts. A terminal that disallows window resizing keeps its existing size
and shows the normal minimum-size instruction when necessary.
macOS gets `~/Applications/Emberwood Terminal.app`, which opens the game in
Terminal.app. Starting with v1.0.2, `./install.sh --pin` also pins the default
user installation to the active GNOME dock. The public one-shot equivalent is
`curl -fsSL https://github.com/samsaw123456/emberwood-tui/releases/download/v1.0.2/install.sh | bash -s -- --pin`.
This explicit
option preserves all existing favorites and their order and is idempotent;
ordinary installs leave dock preferences unchanged. It requires a writable GNOME
desktop session and the default application-menu location, and rejects unsupported
platforms, custom prefixes or headless sessions before installation. On other
desktops, use the launcher's **Add to Favorites** action; on macOS, drag the
installed application to the Dock. A graphical desktop
and installed terminal emulator are required for menu launching; the command
itself also works in an SSH terminal. The terminal must support UTF-8 and ANSI
color. Original artwork and game fonts are bundled; the installer supplies
missing Linux fontconfig and fallback fonts. No image protocol or separate game
GUI window is required.

`./install.sh --prefix '/absolute/custom directory'` installs an isolated copy.
Its menu files live under that prefix, so nonstandard prefixes are not necessarily
searched by your desktop. The macOS app then lives in `PREFIX/Applications`.
Reinstall to update. Settings remain separate from application files: Linux uses
`$XDG_CONFIG_HOME/emberwood-tui` or `~/.config/emberwood-tui`; macOS uses
`~/Library/Application Support/Emberwood Terminal`. A candidate
game must run `--version` successfully before the previous application is replaced.
Interrupted installations clean up their staging directory; a lock belonging to a
departed installer is recovered on the next attempt. An active installer keeps
its lock. Archive extraction keeps ownership with the installing user.

To uninstall the default Linux installation, remove `~/.local/bin/emberwood-tui`,
`~/.local/share/emberwood-tui`,
`~/.local/share/applications/emberwood-terminal.desktop`, and
the `emberwood-terminal.png` files under
`~/.local/share/icons/hicolor/*/apps/`. Remove its dock favorite first if pinned.
On macOS also remove `~/Applications/Emberwood Terminal.app`. The private Java
cache is `~/.cache/emberwood-tui` (or `$XDG_CACHE_HOME/emberwood-tui`). Removing
that cache requires downloading Java again on the next online launch; an offline
launch cannot run without it. Settings are deliberately kept unless you separately
remove them.

## For contributors

`./emberwood-tui` prepares the runtime, builds changed source when needed, and
starts the game. `./emberwood-tui --build` prepares without launching;
`./emberwood-tui --offline` refuses downloads and requires a current JAR and cached
runtime. `./emberwood-tui --version` exercises the real Java entry point. Zed tasks
provide Play, Build, and Check commands. Builds use the checked-in Gradle wrapper.

`EMBERWOOD_TUI_JAVA_HOME=/path/to/jdk25` opts into an existing complete Java 25
installation; it must include `javac`, `jlink`, and the desktop module used for
offscreen image decoding. `EMBERWOOD_TUI_CACHE_DIR` changes the private cache.
Neither setting mutates the global environment. `.release-build` identifies an
installed/release JAR so extraction timestamps do not trigger an unnecessary
source build. Source checkouts do not have that marker. Source launcher freshness checks and
builds are serialized with a PID-aware lock so simultaneous launches cannot run
a partially written JAR. Wait for direct `./gradlew` builds to finish before
launching the game; direct Gradle invocations do not participate in this lock.

Run `./tools/package.sh` after `./gradlew check fatJar` to create:

- `dist/emberwood-tui-1.0.2.tar.gz`
- `dist/install.sh`
- `dist/SHA256SUMS`

Attach all three to the matching `v1.0.2` GitHub release. The version in `VERSION`
and `install.sh` must agree. The workflow compiles/tests and creates archives on
Linux and macOS; it does not publish automatically.

## Verification

`python3 tools/test_install.py` first proves concurrent source launch ordering
against a deliberately slow compiler fixture that exposes a partial JAR. It then
checks verified runtime extraction under a cache path containing spaces and a
backslash, including offline reuse and temporary-file cleanup. It
installs into a temporary path containing spaces
and shell-significant punctuation, runs the installed JAR offline, validates the
desktop entry or macOS plist, reinstalls, and verifies a failed Java preparation
does not damage an existing installation. Results are written to
`build/installer-evidence.json`. `bash -n` and ShellCheck cover shell syntax and
common quoting/control-flow mistakes. Actual GUI launches and platform-specific
results belong in `docs/VERIFICATION.md`; passing a Linux test does not establish
that a macOS launcher has run.

On Linux and macOS, this test also opens the installed public game in a real PTY, presses
M, observes the sound label change and the saved preference, exits with Q, then
reinstalls and opens the game again to prove that the saved preference is loaded.
This checks preference survival through an actual update, rather than only
checking that a settings directory still exists.

CI runs Kotlin checks on four platform/architecture targets and uses the pinned
tools in `tools/lint-requirements.txt` for ShellCheck and Ruff. The Linux x86-64
and both macOS jobs also run the complete ANSI control inventory and upload captures even
if an assertion fails. CI does not claim a physical macOS terminal or audio-device
playtest.

The published v1.0.0 installer was also tested directly with `curl | bash` on
Linux, using an empty private Java cache and paths containing spaces,
backslashes and shell punctuation. It installed the matching release JAR without
a source build, then passed twenty installed CLI/PTY cases including offline
play. The release verification archive retains its logs, checksums and captures;
see [the verification record](VERIFICATION.md).

For v1.0.1, the public installer was downloaded and piped into Bash inside a fresh
minimal Ubuntu 24.04.5 image. Real missing dependencies, the private Java cache
and the public release were installed in 20.331 seconds. Installed play then
passed with networking disabled, and all six actual ANSI captures were inspected.
Host packages remained unchanged. See `build/quality-review/public-release` in
the separate release verification archive. This is additional evidence beyond
the isolated package-manager fixtures and four-platform installation CI.
