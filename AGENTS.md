# Emberwood Terminal

Independent terminal port; never edit ../aie or ../emberwood-kt. Preserve original artwork/licenses and authoritative levels.tsv geometry. Do not reintroduce removed chests, bonus shields, or companion.

JVM 25, Kotlin 2.4.21, Gradle 9.9.1, tui4j 0.3.5. Run `./emberwood-tui --build`, `./gradlew check`, and `python3 tools/ansi_audit.py` (see verification docs for environment). Keep control inventory, actual ANSI evidence and gameplay checklist current. Never equate simulation tests with physical control proof, nor claim unavailable macOS/audio hardware verified.

World +x means screen-right, +y down. Physics belongs in Game. TUI4J owns terminal events and rendering. Standard ANSI sends presses only: movement is explicitly latched until S/Down or a state boundary; do not pretend to detect key-up. X replaces modifier-only Shift dash. Menu text is terminal text, artwork uses ANSI half blocks. Keep the game immediately playable; no marketing landing screen.

Use the shared TerminalSession for production and audit hosts. Ctrl+Z pauses without toggling resume; preserve the complete native terminal settings and prevent interrupt-time output flushing during play. Run `python3 tools/terminal_lifecycle_audit.py` after lifecycle changes; ANSI escape cleanup alone does not prove exact terminal restoration. Keep source-build/package operations separate from source-launch audits so build locks do not invalidate startup timing.

Synchronize visual assertions with the required observable result in completed ANSI paints. A key acknowledgement or fixed delay does not establish that a new frame has been presented. Preserve failed traces alongside explained rechecks; never relax the expected player-facing behavior to make an audit pass.

Installer prerequisite handling lives in install.sh and is shared with bootstrap.sh. Offline checks never install packages. For graphical Linux installs, a terminal executable alone is insufficient: prove that the desktop launcher can select it, or provide a recognized fallback. See docs/QUALITY_REVIEW.md for the session-derived criteria and evidence.
