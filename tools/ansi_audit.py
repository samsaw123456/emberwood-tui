#!/usr/bin/env python3
"""Exercise actual tui4j ANSI input/output through a PTY; retain every frame.

Requires pexpect, pyte and Pillow. Build `testClasses fatJar` first. AuditHost is
only a fixture launcher; all tested commands enter through terminal bytes.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import re
import shutil
import time
from pathlib import Path

import pexpect
import pyte
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = (ROOT / os.environ.get("EMBERWOOD_ANSI_OUTPUT", "build/ansi-audit")).resolve()
BINDINGS = {
    "A": "a",
    "D": "d",
    "Left": "\x1b[D",
    "Right": "\x1b[C",
    "S": "s",
    "Down": "\x1b[B",
    "Space": " ",
    "W": "w",
    "Up": "\x1b[A",
    "X": "x",
    "J": "j",
    "R": "r",
    "P": "p",
    "Esc": "\x1b",
    "Enter": "\r",
    "Tab": "\t",
    "M": "m",
    "G": "g",
    "Q": "q",
    "Ctrl+C": "\x03",
    "Ctrl+Z": "\x1a",
}
ROWS: list[dict] = []
CASE_FILTER: set[tuple[str, str]] | None = None


def require(condition: bool, explanation: str) -> None:
    if not condition:
        raise AssertionError(explanation)


def rgb(value: str, default: tuple[int, int, int]) -> tuple[int, int, int]:
    palette = {
        "black": "000000",
        "red": "cd0000",
        "green": "00cd00",
        "brown": "cdcd00",
        "blue": "0000ee",
        "magenta": "cd00cd",
        "cyan": "00cdcd",
        "white": "e5e5e5",
    }
    value = palette.get(value, value)
    return (
        tuple(bytes.fromhex(value))
        if re.fullmatch(r"[0-9a-fA-F]{6}", value)
        else default
    )


class TerminalScreen(pyte.Screen):
    """Preserve the primary PTY buffer when tui4j uses ANSI alternate screen."""

    def __init__(self, columns: int, lines: int):
        super().__init__(columns, lines)
        self.primary = None

    def set_mode(self, *modes: int, **kwargs) -> None:
        if kwargs.get("private") and 1049 in modes and self.primary is None:
            self.primary = (copy.deepcopy(self.buffer), copy.deepcopy(self.cursor))
            self.erase_in_display(2)
            self.cursor_position()
        super().set_mode(*modes, **kwargs)

    def reset_mode(self, *modes: int, **kwargs) -> None:
        super().reset_mode(*modes, **kwargs)
        if kwargs.get("private") and 1049 in modes and self.primary is not None:
            self.buffer, self.cursor = self.primary
            self.primary = None
            self.dirty.update(range(self.lines))


class Terminal:
    def __init__(
        self,
        scenario: str,
        name: str,
        columns: int = 120,
        rows: int = 40,
        color_mode: str = "truecolor",
        literal_suspend: bool = False,
    ):
        self.directory = OUTPUT / name
        self.directory.mkdir(parents=True, exist_ok=True)
        self.telemetry = self.directory / "state.json"
        self.telemetry.unlink(missing_ok=True)
        shutil.rmtree(self.directory / f"settings-{scenario}", ignore_errors=True)
        self.screen = TerminalScreen(columns, rows)
        self.stream = pyte.Stream(self.screen)
        self.raw = ""
        self.pending_output = ""
        self.complete_offset = 0
        self.frames: list[str] = []
        self.key_latencies: list[float] = []
        java_home = os.environ.get("EMBERWOOD_JAVA_HOME") or os.environ.get("JAVA_HOME")
        java = str(Path(java_home) / "bin/java") if java_home else "java"
        env = dict(
            os.environ,
            TERM="xterm-256color",
            COLORTERM="truecolor",
            LANG="C.UTF-8",
            EMBERWOOD_TUI_COLOR=color_mode,
        )
        arguments = [
            "-Xms32m",
            "-Xmx256m",
            "-Djava.awt.headless=true",
            "--enable-native-access=ALL-UNNAMED",
            "-cp",
            "build/classes/kotlin/test:build/libs/emberwood-tui.jar",
            "emberwood.AuditHostKt",
            scenario,
            str(self.telemetry),
        ]
        executable = java
        if literal_suspend:
            # Configure the slave before Java/JLine starts. Mutating the master
            # termios after startup does not reliably disable VSUSP on Darwin.
            arguments = [
                "-c",
                'stty susp undef || exit 2; exec "$@"',
                "emberwood-literal-suspend",
                java,
                *arguments,
            ]
            executable = "sh"
        self.child = pexpect.spawn(
            executable,
            arguments,
            cwd=str(ROOT),
            env=env,
            encoding="utf-8",
            codec_errors="replace",
            dimensions=(rows, columns),
            timeout=1,
        )
        self.child.delaybeforesend = 0
        try:
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                self.pump(0.08)
                if (
                    self.telemetry.exists()
                    and "Emberwood" in self.text()
                    and self.screen.buffer[0][min(columns, 180) - 1].bg != "default"
                ):
                    # tui4j first paints its fallback viewport, then delivers SIGWINCH.
                    # Wait for the requested width and a settled complete frame.
                    self.pump(0.4)
                    return
                if not self.child.isalive():
                    raise AssertionError(
                        f"AuditHost exited during startup: {self.raw[-2000:]}"
                    )
            raise AssertionError(f"AuditHost did not become ready: {self.raw[-2000:]}")
        except BaseException:
            self.close()
            raise

    def pump(self, duration: float) -> None:
        deadline = time.monotonic() + duration
        while time.monotonic() < deadline:
            try:
                data = self.child.read_nonblocking(
                    131072, timeout=min(0.025, max(0.001, deadline - time.monotonic()))
                )
                self.raw += data
                self.pending_output += data
                # tui4j restores auto-wrap after each complete RendererFlush.
                # Commit only finished paints so a capture cannot combine a new
                # HUD with world pixels from the preceding frame.
                marker = "\x1b[?7h"
                complete = self.pending_output.rfind(marker)
                if complete >= 0:
                    end = complete + len(marker)
                    self.stream.feed(self.pending_output[:end])
                    self.complete_offset += end
                    self.pending_output = self.pending_output[end:]
                if "\x1b[6n" in data:
                    self.child.send("\x1b[1;1R")
            except pexpect.TIMEOUT:
                continue
            except pexpect.EOF:
                self.finish_output()
                return

    def finish_output(self) -> None:
        # At EOF no partial repaint remains in flight. Decode the restoration
        # suffix too, so the real primary screen replaces the game's last image.
        if self.pending_output:
            self.stream.feed(self.pending_output)
            self.complete_offset += len(self.pending_output)
            self.pending_output = ""

    def key(self, name: str, wait: float = 0.16) -> None:
        self.send_keys(BINDINGS[name], wait=wait)

    def send_keys(self, raw: str, expected_keys: int = 1, wait: float = 0.16) -> None:
        before = self.state()["keyCount"]
        started = time.monotonic()
        self.child.send(raw)
        deadline = started + 5
        while self.state()["keyCount"] < before + expected_keys:
            self.pump(0.025)
            require(
                self.child.isalive(), "Game exited before acknowledging physical input"
            )
            require(
                time.monotonic() < deadline,
                f"Physical input was not acknowledged: {raw!r}",
            )
        self.key_latencies.append(time.monotonic() - started)
        self.pump(wait)

    def state(self) -> dict:
        # A read-only observer may catch the fixture's replace operation between
        # unlink and rename on filesystems that cannot guarantee atomic replace.
        deadline = time.monotonic() + 0.25
        while True:
            try:
                return json.loads(self.telemetry.read_text())
            except (FileNotFoundError, json.JSONDecodeError):
                if time.monotonic() >= deadline:
                    raise
                time.sleep(0.001)

    def text(self) -> str:
        return "\n".join(self.screen.display)

    def save(
        self,
        label: str,
        *,
        screen: TerminalScreen | None = None,
        observation: dict | None = None,
        complete_offset: int | None = None,
    ) -> dict:
        screen = self.screen if screen is None else screen
        state = self.state() if observation is None else dict(observation)
        # The raw stream is cumulative. This character offset reproduces exactly
        # the completed frame used by this screenshot, even after later inputs.
        state["ansi_complete_character_offset"] = (
            self.complete_offset if complete_offset is None else complete_offset
        )
        (self.directory / f"{label}.json").write_text(
            json.dumps(state, indent=2) + "\n"
        )
        (self.directory / f"{label}.txt").write_text("\n".join(screen.display) + "\n")
        (self.directory / "terminal.ansi").write_text(self.raw)
        image = Image.new("RGB", (screen.columns * 8, screen.lines * 16), "black")
        draw = ImageDraw.Draw(image)
        font_path = next(
            path
            for path in (
                Path("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"),
                Path("/System/Library/Fonts/Menlo.ttc"),
                ROOT / "src/main/resources/assets/fonts/DejaVuSans.ttf",
            )
            if path.exists()
        )
        font = ImageFont.truetype(str(font_path), 12)
        for y in range(screen.lines):
            for x in range(screen.columns):
                cell = screen.buffer[y][x]
                fg, bg = rgb(cell.fg, (230, 230, 230)), rgb(cell.bg, (0, 0, 0))
                if cell.reverse:
                    fg, bg = bg, fg
                draw.rectangle((x * 8, y * 16, x * 8 + 7, y * 16 + 15), fill=bg)
                if cell.data == "▀":
                    draw.rectangle((x * 8, y * 16, x * 8 + 7, y * 16 + 7), fill=fg)
                elif cell.data == "▄":
                    draw.rectangle((x * 8, y * 16 + 8, x * 8 + 7, y * 16 + 15), fill=fg)
                elif cell.data == "█":
                    draw.rectangle((x * 8, y * 16, x * 8 + 7, y * 16 + 15), fill=fg)
                elif cell.data.strip():
                    draw.text((x * 8, y * 16), cell.data, font=font, fill=fg)
        name = f"{label}.png"
        image.save(self.directory / name)
        self.frames.append(str((self.directory / name).relative_to(ROOT)))
        return state

    def fox(self) -> tuple[float, float]:
        # Original Sunny Land orange palette, independently observed in emitted
        # cells. The flat fixture's campfire/acorns do not use these fur colors.
        points = []
        for y in range(4, self.screen.lines - 3):
            for x in range(self.screen.columns):
                cell = self.screen.buffer[y][x]
                for lower, color in (
                    (False, cell.fg if cell.data == "▀" else cell.bg),
                    (True, cell.bg),
                ):
                    if color.lower() in {"b5422d", "e87838"}:
                        points.append((x, y * 2 + int(lower)))
        require(
            bool(points),
            "No original orange fox pixels found in the rendered playfield",
        )
        return (
            sum(p[0] for p in points) / len(points),
            sum(p[1] for p in points) / len(points),
        )

    def close(self) -> None:
        if self.child.isalive():
            self.child.send("q")
            self.pump(0.25)
        if self.child.isalive():
            self.child.terminate(force=True)
        self.child.close()
        (self.directory / "terminal.ansi").write_text(self.raw)


def case(
    scenario: str,
    binding: str,
    expectation: str,
    exercise,
    suffix: str = "",
    color_mode: str = "truecolor",
    reuse: Terminal | None = None,
) -> None:
    if CASE_FILTER is not None and (scenario, binding) not in CASE_FILTER:
        return
    name = re.sub(r"[^a-z0-9]+", "-", f"{scenario}-{binding}-{suffix}".lower()).strip(
        "-"
    )
    row = {
        "state": scenario,
        "binding": binding,
        "expected": expectation,
        "status": "FAILED",
        "evidence": [],
    }
    terminal = reuse
    try:
        if terminal is None:
            terminal = (
                Terminal(scenario, name, columns=60, rows=20, color_mode=color_mode)
                if scenario == "small"
                else Terminal(
                    scenario,
                    name,
                    color_mode=color_mode,
                    literal_suspend=binding == "Ctrl+Z literal",
                )
            )
        else:
            terminal.directory = OUTPUT / name
            terminal.directory.mkdir(parents=True, exist_ok=True)
            terminal.frames = []
            terminal.key_latencies = []
        before = terminal.save("before")
        exercise(terminal)
        after = terminal.save("after")
        row["status"] = "PASSED"
        row["actual"] = (
            "Process exited and emitted primary-screen restoration"
            if binding in {"Q", "Ctrl+C"}
            else f"{before['state']} to {after['state']}; fox ({before['x']:.1f}, {before['y']:.1f}) to ({after['x']:.1f}, {after['y']:.1f}); {len(terminal.frames)} captured frames"
        )
    except (
        AssertionError,
        OSError,
        ValueError,
        StopIteration,
        pexpect.ExceptionPexpect,
    ) as error:
        row["actual"] = str(error)
        if terminal is not None:
            try:
                terminal.save("failure")
            except (OSError, ValueError) as capture_error:
                row["actual"] += f"; failed to capture final state: {capture_error}"
    finally:
        if terminal is not None:
            row["evidence"] = terminal.frames
            row["key_acknowledgment_seconds"] = terminal.key_latencies
            if reuse is None:
                terminal.close()
    existing = next(
        (
            i
            for i, old in enumerate(ROWS)
            if (old["state"], old["binding"]) == (scenario, binding)
        ),
        None,
    )
    if existing is None:
        ROWS.append(row)
    else:
        ROWS[existing] = row
    print(f"{row['status']:6} {scenario:12} {binding:12} {row['actual']}", flush=True)
    (OUTPUT / "results.json").write_text(json.dumps(ROWS, indent=2) + "\n")


def movement(terminal: Terminal, binding: str, direction: int) -> None:
    before, old = terminal.state(), terminal.fox()
    terminal.key(binding, 0.2)
    wait_for(
        terminal,
        lambda: (
            (terminal.state()["x"] - before["x"]) * direction > 65
            and (terminal.fox()[0] - old[0]) * direction > 4
        ),
        "Steering must produce the expected visible direction",
        timeout=5,
    )
    during, new = terminal.save("during"), terminal.fox()
    require(
        (new[0] - old[0]) * direction > 4,
        f"Fox pixels must move {'right' if direction > 0 else 'left'}: {old} -> {new}",
    )
    require(
        (during["x"] - before["x"]) * direction > 65,
        "World motion must agree with visible motion",
    )
    require(
        during["direction"] == direction,
        "Steering HUD state must match physical binding",
    )
    terminal.pump(0.16)
    require(
        (terminal.state()["x"] - during["x"]) * direction > 15,
        "Portable steering must continue after key release",
    )
    terminal.key("S", 0.1)
    wait_for(
        terminal,
        lambda: (
            abs(terminal.state()["vx"]) < 0.1 and "Steering: STOP" in terminal.text()
        ),
        "Stop must brake and render stopped state",
    )
    stopped = terminal.save("stopped")
    require(
        abs(stopped["vx"]) < 0.1 and stopped["direction"] == 0,
        "Stop must brake and clear steering",
    )


def stop(terminal: Terminal, binding: str) -> None:
    terminal.key("D", 0.22)
    terminal.save("moving")
    terminal.key(binding, 0.1)
    wait_for(
        terminal,
        lambda: (
            abs(terminal.state()["vx"]) < 0.1 and "Steering: STOP" in terminal.text()
        ),
        "Brake must stop motion and update HUD",
    )
    stopped = terminal.save("stopped")
    terminal.pump(0.2)
    require(
        abs(terminal.state()["x"] - stopped["x"]) < 0.1,
        "Fox must remain stopped after braking",
    )
    require("Steering: STOP" in terminal.text(), "Rendered HUD must say STOP")


def jump(terminal: Terminal, binding: str) -> None:
    old = terminal.fox()
    terminal.key(binding, 0.07)
    wait_for(
        terminal,
        lambda: terminal.state()["y"] < 500 and terminal.fox()[1] < old[1] - 2,
        "Jump must visibly rise",
    )
    first, pixels = terminal.save("first-jump"), terminal.fox()
    require(
        first["y"] < 500 and pixels[1] < old[1] - 2,
        f"Jump must visibly move up: {old} -> {pixels}",
    )
    terminal.pump(0.1)
    terminal.key(binding, 0.12)
    second = terminal.save("double-jump")
    require(
        second["y"] < first["y"] - 20 and second["vy"] < 0,
        "Fresh second press must double-jump",
    )
    wait_for(
        terminal,
        lambda: terminal.state()["grounded"] and abs(terminal.fox()[1] - old[1]) < 2,
        "Jump must visibly land and restore grounded state",
        timeout=5,
    )


def disabled(terminal: Terminal, binding: str) -> None:
    before = terminal.state()
    terminal.key(binding, 0.12)
    after = terminal.state()
    for key in ("state", "direction", "shots", "stage", "weapon", "armor", "deaths"):
        require(
            after[key] == before[key],
            f"Disabled {binding} changed {key}: {before[key]} -> {after[key]}",
        )
    if before["state"] not in {"PLAYING", "RESPAWNING"}:
        require(
            abs(after["x"] - before["x"]) < 0.01
            and abs(after["y"] - before["y"]) < 0.01,
            "Disabled key moved the fox",
        )


def transition(terminal: Terminal, binding: str, expected: str) -> None:
    terminal.key(binding, 0.1)
    wait_for(
        terminal,
        lambda: (
            terminal.state()["state"] == expected
            and ("Paused" if expected == "PAUSED" else "Steering:") in terminal.text()
        ),
        f"{binding} must render the {expected} interface",
    )
    require(
        terminal.state()["state"] == expected,
        f"Expected {expected}, got {terminal.state()['state']}",
    )
    require(terminal.state()["direction"] == 0, "State transition must clear steering")
    if expected == "PAUSED":
        require("Paused" in terminal.text(), "Pause menu must actually be rendered")
    else:
        require(
            "Steering:" in terminal.text(), "Gameplay HUD must actually be rendered"
        )


def respawn_pause(terminal: Terminal, binding: str) -> None:
    transition(terminal, binding, "PAUSED")
    terminal.save("paused-return")
    terminal.pump(0.3)
    require(
        terminal.state()["state"] == "PAUSED",
        "Paused return-to-camp must remain frozen",
    )
    terminal.key(binding, 0.05)
    wait_for(
        terminal,
        lambda: (
            terminal.state()["state"] == "RESPAWNING"
            and "Returning to camp" in terminal.text()
        ),
        "Resume must restore the interrupted return-to-camp state",
    )
    terminal.save("resumed-return")
    wait_for(
        terminal,
        lambda: (
            terminal.state()["state"] == "PLAYING"
            and "Steering: STOP" in terminal.text()
        ),
        "Resumed return must visibly finish safely",
        timeout=7,
    )
    require(
        abs(terminal.state()["x"] - 100) < 0.1 and terminal.state()["direction"] == 0,
        "Return must finish at camp without stuck steering",
    )


def toggle(terminal: Terminal, binding: str, property_name: str) -> None:
    before = terminal.state()[property_name]
    terminal.key(binding)
    require(terminal.state()[property_name] != before, f"{property_name} must toggle")
    terminal.save("toggled")
    require(
        ("sound: off" if binding == "M" else "motion: reduced") in terminal.text(),
        "Setting change must be visible in terminal footer",
    )
    terminal.key(binding)
    require(
        terminal.state()[property_name] == before,
        f"Second {binding} must restore setting",
    )


def exit_command(terminal: Terminal, binding: str) -> None:
    # Quit can finish before the observer writes another snapshot. The external
    # process exit and restored terminal are the acknowledgment for this command.
    terminal.child.send(BINDINGS[binding])
    wait_for(
        terminal,
        lambda: not terminal.child.isalive(),
        f"{binding} must terminate the process",
    )
    terminal.finish_output()
    require("\x1b[?1049l" in terminal.raw, "Exit must restore primary terminal screen")
    require(
        terminal.screen.primary is None, "Exit must restore the recorded primary buffer"
    )
    modes = {}
    for parameters, action in re.findall(r"\x1b\[\?([0-9;]+)([hl])", terminal.raw):
        for parameter in parameters.split(";"):
            modes[parameter] = action
    require(modes.get("25") == "h", "Exit must restore the visible terminal cursor")
    require(modes.get("1004") == "l", "Exit must disable game focus reporting")
    require(
        modes.get("2004") == "l", "Exit must disable game bracketed-paste reporting"
    )


def dash(terminal: Terminal) -> None:
    old = terminal.state()["x"]
    terminal.key("X", 0.05)
    wait_for(
        terminal,
        lambda: (
            terminal.state()["x"] > old + 35 and "Dash: charging" in terminal.text()
        ),
        "Dash must visibly propel the fox and report cooldown",
    )
    during = terminal.save("dash")
    require(during["x"] > old + 35, "Dash must propel the fox")
    require("Dash: charging" in terminal.text(), "Dash cooldown must be visible")
    terminal.key("X", 0.1)
    terminal.save("repeat-during-cooldown")
    wait_for(
        terminal,
        lambda: "Dash: ready" in terminal.text(),
        "Dash must recover after cooldown",
    )
    face_left(terminal)
    terminal.save("facing-left")
    old_x, old_pixels = terminal.state()["x"], terminal.fox()
    terminal.key("X", 0)
    wait_for(
        terminal,
        lambda: (
            terminal.state()["x"] < old_x - 35 and terminal.fox()[0] < old_pixels[0] - 4
        ),
        "Facing left must produce a visibly screen-left dash",
    )
    terminal.save("left-dash")
    terminal.key("S", 0.05)
    wait_for(
        terminal,
        lambda: abs(terminal.state()["vx"]) < 0.1,
        "Left dash must recover to a stop",
    )


def face_left(terminal: Terminal) -> None:
    old_x = terminal.state()["x"]
    terminal.key("A", 0.05)
    wait_for(
        terminal,
        lambda: terminal.state()["x"] < old_x - 20,
        "Turning left must actually move the fox left",
    )
    terminal.key("S", 0.05)
    wait_for(
        terminal,
        lambda: abs(terminal.state()["vx"]) < 0.1,
        "Stop before aiming left must brake",
    )


PROJECTILE_COLORS = {
    "bow": {"b88c55", "c8d6a5", "f3e4bd"},
    "spear": {"ad7951", "d5b97d", "c6d3ca"},
    "seed_bomb": {"9d633c", "d3ae68", "71915b", "9aae6a"},
    "thorn_fan": {"b4ce78"},
    "sunwood_lance": {"ad7951", "d5b97d", "c6d3ca", "e2c16d"},
}


def projectile_pixels(
    terminal: Terminal, weapon: str, direction: int = 1
) -> set[tuple[int, int]]:
    fox_x, fox_y = terminal.fox()
    return {
        (x, y)
        for x, y in color_pixels(terminal, PROJECTILE_COLORS[weapon])
        if (x - fox_x) * direction > 6 and abs(y - fox_y) < 16
    }


def fire_visible(terminal: Terminal, weapon: str, direction: int = 1) -> None:
    baseline = projectile_pixels(terminal, weapon, direction)
    terminal.key("J", 0)
    wait_for(
        terminal,
        lambda: (
            terminal.state()["shots"] > 0
            and bool(projectile_pixels(terminal, weapon, direction) - baseline)
        ),
        f"{weapon} must produce a visibly separate projectile to the {'right' if direction > 0 else 'left'} after physical J input",
        timeout=2,
    )


def shoot(terminal: Terminal, armed: bool = True) -> None:
    if armed:
        fire_visible(terminal, terminal.state()["weapon"])
    else:
        terminal.key("J", 0.12)
        require(terminal.state()["shots"] == 0, "No weapon must mean no projectile")
    terminal.save("shot")
    wait_for(
        terminal,
        lambda: terminal.state()["shots"] == 0,
        "Projectile must expire after its range",
        timeout=5,
    )


def repeat_shoot(terminal: Terminal) -> None:
    baseline = projectile_pixels(terminal, "bow")
    terminal.send_keys("jjj", expected_keys=3, wait=0)
    wait_for(
        terminal,
        lambda: (
            terminal.state()["shots"] == 1
            and bool(projectile_pixels(terminal, "bow") - baseline)
        ),
        "A repeated J burst must make one visible arrow without bypassing cooldown",
    )
    terminal.save("repeat-burst")
    wait_for(
        terminal,
        lambda: terminal.state()["shots"] == 0,
        "Repeated fire must expire",
        timeout=5,
    )
    fire_visible(terminal, "bow")
    terminal.save("recovered-shot")


def reduced_direction(terminal: Terminal, binding: str, direction: int) -> None:
    terminal.key("G")
    terminal.save("reduced-before-movement")
    movement(terminal, binding, direction)
    terminal.key("G")


def retry(terminal: Terminal) -> None:
    terminal.key("D", 0.1)
    terminal.key("R", 0.1)
    require(
        terminal.state()["state"] == "RESPAWNING",
        "R must start visible return-to-camp transition",
    )
    require("Returning to camp" in terminal.text(), "Retry must have rendered feedback")
    terminal.save("returning")
    wait_for(
        terminal,
        lambda: (
            terminal.state()["state"] == "PLAYING"
            and "Steering: STOP" in terminal.text()
        ),
        "Retry must visibly restore the playing HUD",
        timeout=5,
    )
    state = terminal.state()
    require(
        state["state"] == "PLAYING"
        and abs(state["x"] - 100) < 0.1
        and state["direction"] == 0,
        "Retry must restore camp without stuck movement",
    )


def menu(terminal: Terminal, binding: str) -> None:
    if binding == "Up":
        terminal.key("Down")
        terminal.save("selected-before-up")
    before = next(line for line in terminal.screen.display if line.startswith(" >"))
    terminal.key(binding)
    after = next(line for line in terminal.screen.display if line.startswith(" >"))
    require(before != after, f"{binding} must visibly move menu selection")


def equipment(terminal: Terminal, locked: bool = False) -> None:
    before = terminal.state()["weapon"]
    terminal.key("Enter")
    require(
        (terminal.state()["weapon"] == before) == locked,
        "Enter must toggle owned equipment and reject locked items",
    )
    require(
        ("locked" if locked else "owned") in terminal.text(),
        "Equipment status must be rendered",
    )
    if not locked:
        terminal.save("unequipped")
        terminal.key("Enter")
        require(
            terminal.state()["weapon"] == before, "Second Enter must restore equipment"
        )


def focus(terminal: Terminal) -> None:
    terminal.key("D", 0.12)
    terminal.child.send("\x1b[O")
    wait_for(
        terminal,
        lambda: terminal.state()["state"] == "PAUSED" and "Paused" in terminal.text(),
        "Focus loss must render the pause menu",
    )
    require(
        terminal.state()["state"] == "PAUSED" and terminal.state()["direction"] == 0,
        "Focus loss must pause and clear steering",
    )
    terminal.save("blurred")
    terminal.child.send("\x1b[I")
    terminal.pump(0.1)
    require(
        terminal.state()["state"] == "PAUSED",
        "Regaining focus must not resume automatically",
    )
    terminal.key("P", 0.2)
    state = terminal.state()
    terminal.pump(0.2)
    require(
        abs(terminal.state()["x"] - state["x"]) < 0.1,
        "Resume after focus loss must not retain held movement",
    )


def suspend_pause(terminal: Terminal, scenario: str, literal: bool = False) -> None:
    before = terminal.state()
    if scenario == "playing":
        terminal.key("D", 0.12)
        wait_for(
            terminal,
            lambda: "Steering: RIGHT" in terminal.text(),
            "Steering must visibly run before Ctrl+Z clears it",
        )
        terminal.save("moving")
    if literal:
        terminal.key("Ctrl+Z", 0)
        require(
            terminal.state()["blurCount"] == before["blurCount"],
            "Literal Ctrl+Z must reach the key parser without a suspend signal",
        )
    else:
        # The terminal driver normally turns this physical byte into TSTP;
        # a signal becomes BlurMessage, not an acknowledged KeyPressMessage.
        terminal.child.send(BINDINGS["Ctrl+Z"])
        wait_for(
            terminal,
            lambda: terminal.state()["blurCount"] > before["blurCount"],
            "The physical suspend byte must reach the application's signal filter",
        )
    expected = (
        "PAUSED" if before["state"] in {"PLAYING", "RESPAWNING"} else before["state"]
    )
    wait_for(
        terminal,
        lambda: (
            terminal.state()["state"] == expected
            and terminal.state()["direction"] == 0
            and (
                "Paused" in terminal.text()
                if expected == "PAUSED" and scenario != "small"
                else True
            )
        ),
        "Ctrl+Z must pause safely or preserve an already inactive state",
    )
    terminal.save("paused-or-unchanged")
    terminal.child.send(BINDINGS["Ctrl+Z"] * 2)
    terminal.pump(0.15)
    require(
        terminal.state()["state"] == expected,
        "Repeated Ctrl+Z must never resume the game",
    )
    # A static screen alone cannot prove its reader/render loop survived.
    toggle(terminal, "M", "muted")
    if before["state"] in {"PLAYING", "RESPAWNING"}:
        terminal.key("P", 0)
        wait_for(
            terminal,
            lambda: (
                terminal.state()["state"] == "PLAYING"
                and "Steering: STOP" in terminal.text()
            ),
            "P must recover from Ctrl+Z pause with stopped steering",
            timeout=7,
        )
        terminal.save("resumed")


def resize(terminal: Terminal) -> None:
    terminal.key("D", 0.12)
    terminal.child.setwinsize(20, 60)
    terminal.screen.resize(20, 60)
    wait_for(
        terminal,
        lambda: "Enlarge to at least" in terminal.text(),
        "Resize must show the small-terminal recovery screen",
    )
    require(
        "Enlarge to at least" in terminal.text(),
        "Small terminal must show readable recovery instruction",
    )
    require(
        terminal.state()["state"] == "PAUSED" and terminal.state()["direction"] == 0,
        "Small resize must pause and clear input",
    )
    terminal.save("small")
    terminal.key("P")
    require(
        terminal.state()["state"] == "PAUSED", "Cannot resume into too-small playfield"
    )
    terminal.child.setwinsize(40, 120)
    terminal.screen.resize(40, 120)
    wait_for(
        terminal,
        lambda: "Paused" in terminal.text(),
        "Enlarging must restore paused menu",
    )
    terminal.key("P")
    require(terminal.state()["state"] == "PLAYING", "Resume must work after enlarging")


def opposing(terminal: Terminal) -> None:
    terminal.send_keys("ad", expected_keys=2, wait=0.2)
    require(
        terminal.state()["direction"] == 1 and "Steering: RIGHT" in terminal.text(),
        "Last opposing key must determine direction",
    )
    terminal.save("right-last")
    terminal.send_keys("da", expected_keys=2, wait=0.2)
    require(
        terminal.state()["direction"] == -1 and "Steering: LEFT" in terminal.text(),
        "Reversed order must steer left",
    )
    terminal.save("left-last")
    for _ in range(5):
        terminal.key("D", 0.035)
    require(
        terminal.state()["direction"] == 1,
        "Keyboard autorepeat must maintain direction",
    )
    terminal.key("S", 0.05)
    wait_for(
        terminal,
        lambda: (
            terminal.state()["direction"] == 0
            and abs(terminal.state()["vx"]) < 0.1
            and "Steering: STOP" in terminal.text()
        ),
        "Stop after held/repeated input must visibly recover",
    )
    stopped = terminal.save("stopped-after-repeat")
    terminal.pump(0.2)
    require(
        abs(terminal.state()["x"] - stopped["x"]) < 0.1,
        "Stopping after repeated input must remain stationary",
    )


def simultaneous(terminal: Terminal) -> None:
    old = terminal.fox()
    terminal.send_keys("dw", expected_keys=2, wait=0)
    wait_for(
        terminal,
        lambda: (
            terminal.state()["direction"] == 1
            and terminal.state()["y"] < 500
            and terminal.fox()[0] > old[0] + 2
            and terminal.fox()[1] < old[1] - 2
        ),
        "Combined input must produce a completed frame visibly moving right and up",
    )
    state, pixels = terminal.save("run-and-jump"), terminal.fox()
    require(
        state["direction"] == 1 and state["y"] < 500,
        "Simultaneous direction+jump must execute both commands",
    )
    require(
        pixels[0] > old[0] + 2 and pixels[1] < old[1] - 2,
        "Combined input must visibly move right and up",
    )
    terminal.key("P")
    require(
        terminal.state()["state"] == "PAUSED" and terminal.state()["direction"] == 0,
        "Pause during held movement/jump must clear steering",
    )
    terminal.save("paused-mid-jump")
    terminal.key("P")
    wait_for(
        terminal,
        lambda: (
            terminal.state()["grounded"]
            and terminal.state()["direction"] == 0
            and abs(terminal.fox()[1] - old[1]) < 2
        ),
        "Resume must visibly land without latent extra movement",
        timeout=5,
    )


def color_pixels(terminal: Terminal, colors: set[str]) -> list[tuple[int, int]]:
    points = []
    for y in range(3, terminal.screen.lines - 4):
        for x in range(terminal.screen.columns):
            cell = terminal.screen.buffer[y][x]
            upper = cell.fg if cell.data == "▀" else cell.bg
            for lower, color in ((False, upper), (True, cell.bg)):
                if color.lower() in colors:
                    points.append((x, y * 2 + int(lower)))
    return points


def wait_for(terminal: Terminal, predicate, message: str, timeout: float = 3) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        terminal.pump(0.04)
        if predicate():
            return
    raise AssertionError(message)


def motion_effect(terminal: Terminal) -> None:
    snow = {"dce6df"}
    wait_for(
        terminal,
        lambda: bool(color_pixels(terminal, snow)),
        "Frost snow must visibly appear before reduced motion",
    )
    terminal.save("snow-full-motion")
    terminal.key("Space", 0.12)
    require(
        terminal.state()["particles"] > 0,
        "Jump should produce original particle feedback",
    )
    terminal.save("jump-particles")
    terminal.key("G", 0.15)
    require(terminal.state()["particles"] == 0, "Reduced motion must clear particles")
    require(
        not color_pixels(terminal, snow),
        "Reduced motion must remove actual rendered snow pixels",
    )
    terminal.save("snow-and-particles-removed")
    terminal.key("G", 0.15)
    wait_for(
        terminal,
        lambda: bool(color_pixels(terminal, snow)),
        "Restoring full motion must restore rendered snow",
    )
    terminal.save("snow-restored")


def motion_camera(terminal: Terminal) -> None:
    # Calibrate against a real reduced-motion frame, not a simulation coordinate.
    # Telemetry can advance while the last complete paint still predates tracking.
    terminal.key("G", 0)
    terminal.key("D", 0.3)
    wait_for(
        terminal,
        lambda: terminal.state()["x"] > 430 and "motion: reduced" in terminal.text(),
        "Running must reach the camera tracking region in reduced motion",
        timeout=5,
    )
    terminal.save("steady-reference")
    centered_pixels = terminal.fox()
    terminal.key("G", 0)
    wait_for(
        terminal,
        lambda: (
            "motion: full" in terminal.text()
            and terminal.fox()[0] > centered_pixels[0] + 1.5
            and terminal.state()["x"] - 380 - terminal.state()["cameraX"] > 5
        ),
        "Full motion must visibly ease behind the running fox",
        timeout=5,
    )
    before = terminal.save("smooth-camera")
    old_pixels = terminal.fox()
    terminal.key("G", 0)
    wait_for(
        terminal,
        lambda: (
            "motion: reduced" in terminal.text()
            and terminal.fox()[0] < old_pixels[0] - 1
        ),
        "Camera snap must visibly recenter the running fox",
        timeout=5,
    )
    after = terminal.save("steady-camera")
    require(
        abs(after["cameraX"] - (after["x"] - 380)) < 1,
        "Reduced motion must remove camera easing",
    )
    require(
        after["x"] > before["x"],
        "Camera recentering must not reverse actual running direction",
    )
    terminal.key("S", 0.3)


def indexed_color(terminal: Terminal) -> None:
    require(";5;" in terminal.raw, "256-color mode must emit indexed ANSI colors")
    require(
        not re.search(r"\x1b\[(?:38|48);2;", terminal.raw),
        "256-color mode must not emit unsupported truecolor SGR",
    )
    foreground = rgb(terminal.screen.buffer[0][1].fg, (0, 0, 0))
    background = rgb(terminal.screen.buffer[0][1].bg, (0, 0, 0))
    require(
        sum(foreground) - sum(background) > 250,
        "Indexed title text must retain strong contrast",
    )
    old = terminal.state()["x"]
    terminal.key("D", 0.6)
    require(terminal.state()["x"] > old + 60, "Indexed rendering must retain movement")
    require(
        "Steering: RIGHT" in terminal.text(), "Indexed HUD must visibly report movement"
    )
    terminal.save("indexed-running")
    terminal.key("S", 0.3)
    terminal.key("P", 0.2)
    require("Paused" in terminal.text(), "Indexed equipment menu must render")


def unknown_input(terminal: Terminal) -> None:
    before = terminal.state()
    for label, raw in (
        ("uppercase", "ADWXJ"),
        ("paste-chord", "\x1b[200~dwxj\x1b[201~"),
        ("paste-single", "\x1b[200~d\x1b[201~"),
    ):
        terminal.send_keys(
            raw, expected_keys=5 if label == "uppercase" else 1, wait=0.18
        )
        after = terminal.save(label)
        require(
            after["direction"] == 0
            and after["shots"] == 0
            and abs(after["x"] - before["x"]) < 0.01
            and abs(after["y"] - before["y"]) < 0.01,
            f"Unsupported {label} must not activate player commands",
        )


def menu_edges(terminal: Terminal) -> None:
    initial = next(line for line in terminal.screen.display if line.startswith(" >"))
    terminal.key("Up")
    require(
        next(line for line in terminal.screen.display if line.startswith(" >"))
        == initial,
        "Up at the first item must stay on the first item",
    )
    terminal.save("up-clamped")
    for binding in ("Down", "Tab"):
        intermediate = None
        for _ in range(13):
            terminal.key(binding, 0.045)
            selected = next(
                line for line in terminal.screen.display if line.startswith(" >")
            )
            if selected != initial and intermediate is None:
                # Retain an observed change without pausing the rapid key burst
                # for PNG encoding. Final input acknowledgement can precede its
                # completed paint, especially on the macOS Intel runner.
                intermediate = (
                    copy.deepcopy(terminal.screen),
                    terminal.state(),
                    terminal.complete_offset,
                )
        require(
            intermediate is not None,
            f"Repeated {binding} must visibly leave the initial selection",
        )
        wait_for(
            terminal,
            lambda: (
                next(line for line in terminal.screen.display if line.startswith(" >"))
                == initial
            ),
            f"Thirteen repeated {binding} presses must wrap to the first item",
        )
        screen, observation, offset = intermediate
        terminal.save(
            f"{binding.lower()}-traversing",
            screen=screen,
            observation=observation,
            complete_offset=offset,
        )
        terminal.save(f"{binding.lower()}-wrapped")


def equipment_status(terminal: Terminal, status: str) -> None:
    wait_for(
        terminal,
        lambda: any(
            line.startswith(" >") and line.rstrip().endswith(status)
            for line in terminal.screen.display
        ),
        f"Selected equipment must visibly report {status}",
    )


def every_equipment(
    terminal: Terminal, index: int, item_id: str, category: str
) -> None:
    for _ in range(index):
        selected = next(
            line for line in terminal.screen.display if line.startswith(" >")
        )
        terminal.key("Down", 0.04)
        wait_for(
            terminal,
            lambda selected=selected: (
                next(line for line in terminal.screen.display if line.startswith(" >"))
                != selected
            ),
            "Equipment navigation must visibly update the selected item",
        )
    terminal.save("selected")
    field = "weapon" if category == "weapon" else "armor"
    if terminal.state()[field] == item_id:
        terminal.key("Enter")
        require(terminal.state()[field] == "", "Equipped item must be removable")
        equipment_status(terminal, "owned")
        terminal.save("removed")
    terminal.key("Enter")
    require(terminal.state()[field] == item_id, f"Enter must equip selected {item_id}")
    equipment_status(terminal, "equipped")
    terminal.save("equipped")
    terminal.key("Enter")
    require(terminal.state()[field] == "", "Second Enter must remove selected item")
    equipment_status(terminal, "owned")
    terminal.save("removed-after-equip")
    terminal.key("Enter")
    require(
        terminal.state()[field] == item_id, "Third Enter must restore selected item"
    )
    equipment_status(terminal, "equipped")
    if category == "weapon":
        terminal.key("P")
        fire_visible(terminal, item_id)
        terminal.save("fired")
        wait_for(
            terminal,
            lambda: terminal.state()["shots"] == 0,
            "First weapon shot must expire",
            timeout=5,
        )
        face_left(terminal)
        fire_visible(terminal, item_id, -1)
        terminal.save("fired-left")


def run() -> None:
    for binding, direction in (("A", -1), ("Left", -1), ("D", 1), ("Right", 1)):
        case(
            "playing",
            binding,
            "Fox visibly moves in named screen direction; latched steering and stop work",
            lambda t, b=binding, d=direction: movement(t, b, d),
        )
    for binding, direction in (("A", -1), ("Left", -1), ("D", 1), ("Right", 1)):
        case(
            "playing",
            f"{binding} reduced motion",
            "Reduced motion preserves named screen direction and explicit stopping",
            lambda t, b=binding, d=direction: reduced_direction(t, b, d),
        )
    for binding in ("S", "Down"):
        case(
            "playing",
            binding,
            "Brake to rest and remain stopped",
            lambda t, b=binding: stop(t, b),
        )
    for binding in ("Space", "W", "Up"):
        case(
            "playing",
            binding,
            "Visible upward jump, second press double-jumps, then lands",
            lambda t, b=binding: jump(t, b),
        )
    case(
        "playing",
        "X",
        "Dash visibly moves both right and left, respects cooldown, and recovers",
        dash,
    )
    case("playing", "J", "Projectile appears and expires", shoot)
    case(
        "playing",
        "J repeated",
        "Repeated presses respect cooldown; later fire recovers",
        repeat_shoot,
    )
    case(
        "no_weapon",
        "J",
        "No projectile without earned weapon",
        lambda t: shoot(t, False),
    )
    case("playing", "R", "Return to camp clears steering", retry)
    for state in ("playing", "respawning"):
        for binding in ("P", "Esc"):
            case(
                state,
                binding,
                "Pause return-to-camp, resume its timer, then safely return to camp"
                if state == "respawning"
                else "Pause and clear input",
                (lambda t, b=binding: respawn_pause(t, b))
                if state == "respawning"
                else (lambda t, b=binding: transition(t, b, "PAUSED")),
            )
    for binding in ("P", "Esc"):
        case(
            "paused",
            binding,
            "Resume without latent movement",
            lambda t, b=binding: transition(t, b, "PLAYING"),
        )
    for binding in ("Up", "Down", "Tab"):
        case(
            "paused",
            binding,
            "Move visible equipment selection",
            lambda t, b=binding: menu(t, b),
        )
    case("paused", "Enter", "Remove and re-equip owned bow", equipment)
    case(
        "locked_pause",
        "Enter",
        "Locked equipment remains unavailable",
        lambda t: equipment(t, True),
    )
    for state in ("title", "complete", "won"):
        case(
            state,
            "Enter",
            "Start/advance/restart into gameplay",
            lambda t: transition(t, "Enter", "PLAYING"),
        )
    active = {
        "title": {"Enter"},
        "playing": {
            "A",
            "D",
            "Left",
            "Right",
            "S",
            "Down",
            "Space",
            "W",
            "Up",
            "X",
            "J",
            "R",
            "P",
            "Esc",
        },
        "paused": {"P", "Esc", "Up", "Down", "Tab", "Enter"},
        "respawning": {"P", "Esc"},
        "complete": {"Enter"},
        "won": {"Enter"},
        "small": set(),
    }
    for state in active:
        case(
            state,
            "Ctrl+Z",
            "Pause without toggling resume; inactive states stay unchanged; rendering and input recover",
            lambda t, s=state: suspend_pause(t, s),
        )
    case(
        "playing",
        "Ctrl+Z literal",
        "A terminal sending literal Ctrl+Z also pauses and clears steering; P resumes",
        lambda t: suspend_pause(t, "playing", literal=True),
    )
    for state, available in active.items():
        # Inert controls and restored toggles share a live state. Each row still
        # records its own physical input and before/after render; raw ANSI stays
        # cumulative so every capture can be reconstructed from the real stream.
        shared = (
            None
            if state == "respawning" or CASE_FILTER is not None
            else (
                Terminal(state, f"shared-{state}", columns=60, rows=20)
                if state == "small"
                else Terminal(state, f"shared-{state}")
            )
        )
        try:
            for binding in sorted(
                BINDINGS.keys() - available - {"M", "G", "Q", "Ctrl+C", "Ctrl+Z"}
            ):
                case(
                    state,
                    binding,
                    "Disabled in this state; no state/action leakage",
                    lambda t, b=binding: disabled(t, b),
                    "disabled",
                    reuse=shared,
                )
            for binding, property_name in (("M", "muted"), ("G", "gentle")):
                case(
                    state,
                    binding,
                    "Toggle setting with visible feedback and restore on second press",
                    lambda t, b=binding, p=property_name: toggle(t, b, p),
                    reuse=shared,
                )
        finally:
            if shared is not None:
                shared.close()
        for binding in ("Q", "Ctrl+C"):
            case(
                state,
                binding,
                "Exit and restore terminal screen",
                lambda t, b=binding: exit_command(t, b),
            )
    case(
        "playing",
        "focus loss/regain",
        "Pause on blur, no automatic resume or stuck movement",
        focus,
    )
    case("playing", "resize", "Small-size pause and resize recovery", resize)
    case(
        "playing",
        "opposing/repeated",
        "Last direction wins and stop recovers after autorepeat",
        opposing,
    )
    case(
        "playing",
        "D+W and pause",
        "Combined direction/jump input works and pause clears held steering",
        simultaneous,
    )
    case(
        "paused",
        "Up/Down/Tab boundaries",
        "Up clamps at first item; repeated Down and Tab traverse and wrap all items",
        menu_edges,
    )
    case(
        "playing",
        "uppercase/bracketed paste",
        "Unsupported uppercase keys and pasted text remain inert",
        unknown_input,
    )
    case(
        "motion",
        "G rendered effects",
        "Reduced motion visibly removes snow/particles; restoring full motion restores snow",
        motion_effect,
    )
    case(
        "motion",
        "G camera recentering",
        "Reduced motion removes camera easing while the fox continues running right",
        motion_camera,
    )
    case(
        "playing",
        "256-color rendering",
        "Indexed palette retains readable HUD, colored world, running and pause menu",
        indexed_color,
        color_mode="256",
    )
    items = [
        ("bow", "weapon"),
        ("armor", "armor"),
        ("spear", "weapon"),
        ("bark_armor", "armor"),
        ("seed_bomb", "weapon"),
        ("ironwood_armor", "armor"),
        ("reed_mail", "armor"),
        ("thorn_fan", "weapon"),
        ("frost_armor", "armor"),
        ("stoneplate", "armor"),
        ("copperbark_armor", "armor"),
        ("sunwood_lance", "weapon"),
        ("sunleaf_armor", "armor"),
    ]
    for index, (item_id, category) in enumerate(items):
        case(
            "paused",
            f"Enter [{item_id}]",
            "Equip, remove, restore; weapon visibly fires right and left after resume",
            lambda t, i=index, item=item_id, c=category: every_equipment(t, i, item, c),
        )


def contact_sheets() -> None:
    directory = OUTPUT / "contact-sheets"
    directory.mkdir(exist_ok=True)
    for old in directory.glob("controls-*.png"):
        old.unlink()
    font = ImageFont.truetype(
        str(ROOT / "src/main/resources/assets/fonts/DejaVuSans.ttf"), 14
    )
    # Retain every frame: directional movement, first/second jump, restore, and
    # equipment removal are separate observable steps, not expendable samples.
    frames = [
        (number, row, path)
        for number, row in enumerate(ROWS, 1)
        for path in row["evidence"]
    ]
    manifest = []
    for offset in range(0, len(frames), 15):
        group = frames[offset : offset + 15]
        sheet = Image.new("RGB", (1440, ((len(group) + 2) // 3) * 350), (15, 20, 22))
        draw = ImageDraw.Draw(sheet)
        page = f"controls-{offset // 15 + 1:02}.png"
        page_frames = []
        for index, (number, row, path) in enumerate(group):
            x, y = index % 3 * 480, index // 3 * 350
            draw.text(
                (x + 5, y + 2),
                f"{number}. {row['state']} | {row['binding']} | {Path(path).stem}",
                font=font,
                fill="white" if row["status"] == "PASSED" else "#ff8a8a",
            )
            picture = Image.open(ROOT / path).convert("RGB").resize((480, 320))
            sheet.paste(picture, (x, y + 28))
            page_frames.append(path)
        sheet.save(directory / page)
        manifest.append({"page": page, "captures": page_frames})
    (directory / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


def report(write_inventory: bool = True) -> None:
    lines = [
        "# Physical terminal control audit",
        "",
        f"Generated by `python3 tools/ansi_audit.py` against a running tui4j process in a real PTY. Every listed key is sent as ANSI input. Before/during/after PNGs are reconstructed from completed ANSI paints; each snapshot records its exact character offset in the retained raw stream. The decoder honors primary/alternate screen switching. Raw ANSI and state records remain under `{OUTPUT.relative_to(ROOT)}`. The fixture host sets starting states; it does not call input handlers directly.",
        "",
        "Steering is intentionally latched: ordinary ANSI terminals send key presses but no releases. A/D or arrows select direction, S/Down stops; X replaces modifier-only Shift. Ctrl+Z pauses without toggling resume; P/Esc resumes. Both terminal-generated suspend signals and literal Ctrl+Z bytes are checked. Binding letters denote unshifted lowercase keys. No mouse, controller, touch, manual camera keys, uppercase letter aliases, or key-release protocol is implemented. Terminal font and audio-device behavior require separate host checks.",
        "",
        "| State | Physical binding | Expected result | Actual result | Status | Evidence |",
        "|---|---|---|---|---|---|",
    ]
    for row in ROWS:
        evidence = row["evidence"][-1] if row["evidence"] else "startup failed"
        actual = row["actual"].replace("|", "/").replace("\n", " ")[:300]
        lines.append(
            f"| {row['state']} | {row['binding']} | {row['expected']} | {actual} | {row['status']} | `{evidence}` |"
        )
    lines.extend(
        [
            "",
            "## Limits",
            "",
            "M rows prove terminal feedback and the mute preference state. Persistence and actual PCM output/silence are independently checked; see [the verification record](VERIFICATION.md) and `tools/audio_audit.py`. Contact sheets include every captured frame, and the manual visual review is recorded in [evidence notes](evidence/README.md). macOS and terminal-specific font/device behavior are not established by a Linux PTY audit.",
            "",
        ]
    )
    if write_inventory:
        (ROOT / "docs" / "CONTROLS.md").write_text("\n".join(lines))
    contact_sheets()
    failed = [row for row in ROWS if row["status"] != "PASSED"]
    print(
        f"\n{len(ROWS) - len(failed)}/{len(ROWS)} audit rows passed. Evidence: {OUTPUT}",
        flush=True,
    )
    if failed:
        raise SystemExit(1)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--quick",
        action="store_true",
        help="Only four direction bindings for harness diagnosis",
    )
    parser.add_argument(
        "--retry-failed",
        action="store_true",
        help="Rerun failed rows from the last audit and regenerate its complete report",
    )
    parser.add_argument(
        "--case",
        nargs=2,
        action="append",
        metavar=("STATE", "BINDING"),
        help="Run a named case; repeat for multiple cases. Uses a selected/ evidence subdirectory and leaves the complete inventory untouched.",
    )
    options = parser.parse_args()
    if options.case:
        if options.quick or options.retry_failed:
            parser.error("--case cannot be combined with --quick or --retry-failed")
        CASE_FILTER = {tuple(selected) for selected in options.case}
        OUTPUT = OUTPUT / "selected"
    OUTPUT.mkdir(parents=True, exist_ok=True)
    jar = ROOT / "build/libs/emberwood-tui.jar"
    jar_hash = hashlib.sha256(jar.read_bytes()).hexdigest()
    metadata_path = OUTPUT / "run.json"
    if options.retry_failed:
        metadata = json.loads(metadata_path.read_text())
        require(
            metadata["jar_sha256"] == jar_hash,
            "The release JAR changed; rerun the complete audit",
        )
        ROWS.extend(json.loads((OUTPUT / "results.json").read_text()))
        CASE_FILTER = {
            (row["state"], row["binding"]) for row in ROWS if row["status"] != "PASSED"
        }
    else:
        metadata = {"jar_sha256": jar_hash, "runs": []}
    started = time.monotonic()
    if options.quick:
        for key, sign in (("A", -1), ("Left", -1), ("D", 1), ("Right", 1)):
            case(
                "playing",
                key,
                "Visible screen direction and explicit stopping",
                lambda t, b=key, d=sign: movement(t, b, d),
            )
    else:
        run()
    if options.case:
        require(
            {(row["state"], row["binding"]) for row in ROWS} == CASE_FILTER,
            "One or more selected cases were not found in the inventory",
        )
    require(
        hashlib.sha256(jar.read_bytes()).hexdigest() == jar_hash,
        "The release JAR changed during the audit",
    )
    metadata["runs"].append(
        {
            "mode": "retry-failed"
            if options.retry_failed
            else "quick"
            if options.quick
            else "selected"
            if options.case
            else "complete",
            "duration_seconds": round(time.monotonic() - started, 2),
            "rows": len(ROWS),
            "passed": sum(row["status"] == "PASSED" for row in ROWS),
        }
    )
    metadata_path.write_text(json.dumps(metadata, indent=2) + "\n")
    report(write_inventory=not options.case)
