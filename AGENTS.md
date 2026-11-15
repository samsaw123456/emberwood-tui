# Emberwood Terminal

Independent terminal port; never edit ../aie or ../emberwood-kt. Preserve original artwork/licenses and authoritative levels.tsv geometry. Do not reintroduce removed chests, bonus shields, or companion.

JVM 25, Kotlin 2.4.21, Gradle 9.9.1, tui4j 0.3.5. Run `./emberwood-tui --build`, `./gradlew check`, and `python3 tools/ansi_audit.py` (see verification docs for environment). Keep control inventory, actual ANSI evidence and gameplay checklist current. Never equate simulation tests with physical control proof, nor claim unavailable macOS/audio hardware verified.

World +x means screen-right, +y down. Physics belongs in Game. TUI4J owns terminal events and rendering. Standard ANSI sends presses only: movement is explicitly latched until S/Down or a state boundary; do not pretend to detect key-up. X replaces modifier-only Shift dash. Menu text is terminal text, artwork uses ANSI half blocks. Keep the game immediately playable; no marketing landing screen.
