# Installation and distribution

The distribution targets glibc-based Linux and macOS on x86-64 or ARM64. The release is the
same portable archive on all four targets. It contains the game JAR and its
sources; the installer supplies a private, checksum-verified Temurin **25.0.5+8**
JDK for the current platform. It never changes system Java or asks for sudo.

## For players

```sh
curl -fsSL https://github.com/samsaw123456/emberwood-tui/releases/download/v1.0.0/install.sh | bash
```

To inspect before executing, download `install.sh` from the release, read it, then
run `bash install.sh`. A local checkout can be installed with `./install.sh`.
Both routes install under `~/.local` and create `~/.local/bin/emberwood-tui`.
Add that bin directory to `PATH` if the installer reports it is missing.

The first installation needs an internet connection, Bash, `tar`, `awk`, `mktemp`,
`curl` or `wget`, and `sha256sum` or `shasum`. These are standard Linux/macOS tools;
missing tools produce an explicit message. The installer verifies the downloaded
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
Terminal.app. Pin either launcher to your dock or favorites. A graphical desktop
and installed terminal emulator are required for menu launching; the command
itself also works in an SSH terminal. The terminal must support UTF-8 and ANSI
color. The game does not need an image protocol, GUI window, or system font install.

`./install.sh --prefix '/absolute/custom directory'` installs an isolated copy.
Its menu files live under that prefix, so nonstandard prefixes are not necessarily
searched by your desktop. The macOS app then lives in `PREFIX/Applications`.
Reinstall to update. Settings remain separate from application files: Linux uses
`$XDG_CONFIG_HOME/emberwood-tui` or `~/.config/emberwood-tui`; macOS uses
`~/Library/Application Support/Emberwood Terminal`. A candidate
game must run `--version` successfully before the previous application is replaced.

To uninstall the default Linux installation, remove `~/.local/bin/emberwood-tui`,
`~/.local/share/emberwood-tui`,
`~/.local/share/applications/emberwood-terminal.desktop`, and
`~/.local/share/icons/hicolor/512x512/apps/emberwood-terminal.png`.
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

- `dist/emberwood-tui-1.0.0.tar.gz`
- `dist/install.sh`
- `dist/SHA256SUMS`

Attach all three to the matching `v1.0.0` GitHub release. The version in `VERSION`
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
