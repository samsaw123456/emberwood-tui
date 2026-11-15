#!/usr/bin/env python3
"""Play all original routes through a live PTY using only ordinary key presses.

Test-only telemetry is read for navigation. No teleport, collectible mutation,
forced boss phases, accelerated clock or direct InputFrame injection is used.
The complete emitted ANSI stream, command log, results and rendered captures are
saved under build/ansi-audit/campaign. Requires the same tools as ansi_audit.py.
"""

from __future__ import annotations

import errno
import gzip
import hashlib
import json
import math
import multiprocessing
import os
import re
import select
import time
from contextlib import ExitStack

import pexpect
from ansi_audit import ROOT, Terminal

FRAME_END = re.compile(r"\x1b\[\?7h\x1b\[\d+;1H")


class NavigationError(AssertionError):
    """An ordinary failed route attempt that may recover at a campfire."""


def levels() -> list[dict]:
    result: list[dict] = []
    current: dict = {}
    for line in (ROOT / "src/main/resources/levels.tsv").read_text().splitlines():
        row = line.split("\t")
        if row[0] == "LEVEL":
            current = {
                "name": row[2],
                "gate": float(row[5]),
                "route": [],
                "moving": [],
                "crumbles": [],
                "camps": [],
            }
            result.append(current)
        elif row[0] == "CAMP":
            current["camps"].append((float(row[1]), float(row[2])))
        elif row[0] == "ROUTE":
            x, y = float(row[2]), float(row[3])
            # The first opossum's patrol can touch the original route's ground
            # waypoint at another real-time phase. Keep the fox a body-width
            # away, then aim ten units earlier on the following broad shelf.
            if len(result) == 1 and x in (1250.0, 1540.0):
                x -= 10.0
            current["route"].append((row[1], x, y))
        elif row[0] == "PLATFORM" and row[1] == "moving":
            current["moving"].append(tuple(map(float, row[2:8])))
        elif row[0] == "PLATFORM" and row[1] == "crumble":
            current["crumbles"].append(tuple(map(float, row[2:6])))
    return result


class CampaignTerminal(Terminal):
    """Decode every ANSI byte in another process without delaying input.

    The ordinary game clock and keyboard path stay unchanged. Python's terminal
    decoder uses its own process so its GIL cannot stall navigation. The decoder
    retains actual screen state and writes captures on request.
    """

    def __init__(self):
        self.reader_started = False
        super().__init__("campaign", "campaign", columns=100, rows=36)
        context = multiprocessing.get_context("fork")
        self.connection, decoder_connection = context.Pipe()
        self.pending_chunks: list[str] = []
        self.saved: dict[str, dict] = {}
        self.reader_error: str | None = None
        self.reader = context.Process(
            target=self.read_output, args=(decoder_connection,), daemon=True
        )
        self.reader.start()
        decoder_connection.close()
        self.reader_started = True

    def read_output(self, connection) -> None:
        try:
            pending_capture: tuple[str, float] | None = None
            incomplete_frame = self.pending_output
            while True:
                if connection.poll():
                    action, label = connection.recv()
                    if action == "stop":
                        return
                    if action == "save":
                        # Telemetry is written during update, before tui4j has
                        # emitted that frame. Keep decoding the real output
                        # before recording the corresponding presentation.
                        pending_capture = (label, time.monotonic() + 0.15)
                readable, _, _ = select.select([self.child.child_fd], [], [], 0.01)
                if readable:
                    try:
                        raw = os.read(self.child.child_fd, 16384)
                    except OSError as error:
                        if error.errno == errno.EIO:
                            return
                        raise
                    if not raw:
                        return
                    data = self.child._decoder.decode(raw, final=False)
                    connection.send(("data", data))
                    incomplete_frame += data
                    # A frame can span several PTY reads. Only publish complete
                    # tui4j frames, whose final sequence restores autowrap and
                    # positions the cursor after the last rendered line.
                    while end := FRAME_END.search(incomplete_frame):
                        self.stream.feed(incomplete_frame[: end.end()])
                        self.complete_offset += end.end()
                        incomplete_frame = incomplete_frame[end.end() :]
                    if "\x1b[6n" in data:
                        self.child.send("\x1b[1;1R")
                if pending_capture and time.monotonic() >= pending_capture[1]:
                    label = pending_capture[0]
                    state = super().save(label)
                    connection.send(("saved", (label, state)))
                    pending_capture = None
        except (OSError, ValueError, EOFError, pexpect.ExceptionPexpect) as error:
            connection.send(("error", repr(error)))
        finally:
            connection.close()

    def receive(self) -> None:
        while self.connection.poll():
            try:
                kind, payload = self.connection.recv()
            except EOFError:
                return
            if kind == "data":
                self.pending_chunks.append(payload)
            elif kind == "saved":
                label, state = payload
                self.saved[label] = state
            elif kind == "error":
                self.reader_error = payload
        if self.reader_error is not None:
            raise RuntimeError(f"Campaign terminal decoder failed: {self.reader_error}")

    def pump(self, duration: float) -> None:
        if not self.reader_started:
            super().pump(duration)
        else:
            time.sleep(duration)
            self.receive()

    def state(self) -> dict:
        deadline = time.monotonic() + 0.25
        while True:
            try:
                return super().state()
            except (FileNotFoundError, json.JSONDecodeError):
                if time.monotonic() >= deadline:
                    raise
                time.sleep(0.002)

    def output_chunks(self) -> list[str]:
        self.receive()
        chunks = [self.raw, *self.pending_chunks]
        self.raw = ""
        self.pending_chunks.clear()
        return chunks

    def save(self, label: str) -> dict:
        self.connection.send(("save", label))
        deadline = time.monotonic() + 10
        while label not in self.saved:
            self.pump(0.002)
            if time.monotonic() >= deadline:
                raise TimeoutError(f"Terminal capture {label} did not complete")
        self.frames.append(str((self.directory / f"{label}.png").relative_to(ROOT)))
        return self.saved.pop(label)

    def close(self) -> None:
        if self.child.isalive():
            self.child.send("q")
            self.pump(0.25)
        if self.reader.is_alive():
            self.connection.send(("stop", ""))
        self.reader.join(timeout=2)
        if self.reader.is_alive():
            self.reader.terminate()
            self.reader.join(timeout=2)
        self.receive()
        if self.child.isalive():
            self.child.terminate(force=True)
        self.child.close()


class Campaign:
    def __init__(self):
        self.terminal = CampaignTerminal()
        self.output = self.terminal.directory
        self.artifact_sha256 = hashlib.sha256(
            (ROOT / "build/libs/emberwood-tui.jar").read_bytes()
        ).hexdigest()
        (self.output / "artifact.sha256").write_text(
            f"{self.artifact_sha256}  build/libs/emberwood-tui.jar\n"
        )
        self.files = ExitStack()
        self.archive = self.files.enter_context(
            gzip.open(self.output / "complete-terminal.ansi.gz", "wb", compresslevel=1)  # noqa: SIM115 - ExitStack owns stream
        )
        self.commands = self.files.enter_context(
            (self.output / "commands.jsonl").open("w")
        )
        self.events = self.files.enter_context((self.output / "events.jsonl").open("w"))
        self.state: dict = {}
        self.start_real = time.monotonic()
        self.last_report = self.start_real
        self.chapter_start = 0.0
        self.captured_mechanics: set[str] = set()
        self.poll()

    def poll(self) -> dict:
        self.terminal.pump(0.004)
        for chunk in self.terminal.output_chunks():
            self.archive.write(chunk.encode("utf-8"))
        self.state = self.terminal.state()
        if not self.terminal.child.isalive():
            raise RuntimeError("Game exited before the campaign finished")
        now = time.monotonic()
        if now - self.last_report > 25:
            self.last_report = now
            s = self.state
            print(
                f"Stage {s['stage'] + 1}: x={s['x']:.0f} y={s['y']:.0f} "
                f"acorns={s['acorns']} boss={s['bossPhase']} elapsed={s['elapsed']:.1f}s",
                flush=True,
            )
        return self.state

    def wait(self, predicate, timeout: float = 12) -> dict:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            state = self.poll()
            if predicate(state):
                return state
        raise TimeoutError(f"No expected response after {timeout}s: {self.state}")

    def key(self, key: str) -> None:
        previous_count = self.state.get("keyCount", 0)
        self.terminal.child.send(key)
        self.commands.write(
            json.dumps(
                {
                    "real": time.monotonic() - self.start_real,
                    "key": key,
                    "state": self.state,
                }
            )
            + "\n"
        )
        self.commands.flush()
        self.wait(lambda state: state.get("keyCount", 0) > previous_count, 2)

    def steer(self, target: float | None) -> None:
        distance = 0 if target is None else target - self.state["x"]
        # A terminal frame can contain several fixed physics steps. Leave
        # enough stopping distance for both inertia and that ordinary latency.
        brake = max(12.0, abs(self.state["vx"]) * 0.12)
        direction = -1 if distance < -brake else 1 if distance > brake else 0
        if self.state["direction"] != direction:
            self.key({-1: "a", 0: "s", 1: "d"}[direction])
            self.wait(
                lambda s: s["direction"] == direction or s["state"] != "PLAYING", 2
            )

    def event(self, name: str) -> None:
        self.events.write(json.dumps({"event": name, "state": self.state}) + "\n")
        self.events.flush()

    def capture(self, name: str) -> None:
        self.poll()
        expected = None
        if name.endswith("-complete"):
            expected = (
                "All twenty trails cleared!"
                if self.state["state"] == "WON"
                else "Trail cleared!"
            )
        elif match := re.fullmatch(r"level-\d+-boss-([12])", name):
            expected = f": {match[1]}/3 -"
        elif name == "victory":
            expected = "All twenty trails cleared!"
        deadline = time.monotonic() + 3
        while True:
            self.terminal.save(name)
            if (
                expected is None
                or expected in (self.output / f"{name}.txt").read_text()
            ):
                break
            if time.monotonic() >= deadline:
                raise AssertionError(
                    f"Rendered capture {name} never showed {expected!r}"
                )
        self.event(name)

    def healthy(self) -> bool:
        if self.state["state"] in ("WON", "CHAPTER_COMPLETE"):
            return False
        if self.state["state"] != "PLAYING":
            raise NavigationError(f"Unexpected {self.state['state']}: {self.state}")
        return True

    def walk(self, target: float) -> None:
        started = self.state["elapsed"]
        while True:
            self.poll()
            if not self.healthy():
                return
            self.steer(target)
            if abs(self.state["x"] - target) < 18 and abs(self.state["vx"]) < 50:
                return
            if self.state["elapsed"] - started > 5:
                raise NavigationError(f"Walk to {target} stuck: {self.state}")

    def follow(self, step: tuple[str, float, float], level: dict) -> None:
        kind, target_x, target_y = step
        if kind == "walk":
            self.walk(target_x)
            return
        # A checkpoint may already be the next route target. Do not queue a
        # jump and then accept its starting grounded frame as a landing.
        if (
            kind == "jump"
            and self.state["grounded"]
            and abs(self.state["x"] - target_x) < 18
            and abs(self.state["y"] - target_y) < 8
        ):
            return
        if kind != "bounce":
            self.steer(None)
            release = self.state["elapsed"]
            self.wait(lambda s: s["elapsed"] - release >= 2 / 60)
        launch_x = self.state["x"]
        moving = (
            next((p for p in level["moving"] if abs(p[0] - target_x) < 2), None)
            if kind == "moving"
            else None
        )
        clear_shelf = (
            moving is None
            and target_y < self.state["y"]
            and abs(target_x - launch_x) < 120
        )
        second_delay = 0.48 if abs(target_x - launch_x) > 260 else 0.28
        if kind != "bounce":
            self.key(" ")
            self.wait(
                lambda state: not state["grounded"] or state["state"] != "PLAYING", 2
            )
        started = self.state["elapsed"]
        second_jump = False
        while True:
            self.poll()
            if not self.healthy():
                return
            age = self.state["elapsed"] - started
            if age >= second_delay and not second_jump:
                self.key(" ")
                second_jump = True
            x, y = target_x, target_y
            if moving:
                px, py, width, _height, mx, my = moving
                phase = (
                    self.state.get(
                        "levelAge", self.state["elapsed"] - self.chapter_start
                    )
                    * 1.35
                )
                x, y = px + mx * math.sin(phase) + width / 2, py + my * math.sin(phase)
            self.steer(launch_x if clear_shelf and age < 4 / 60 else x)
            if kind == "mushroom" and self.state["vy"] < -700:
                return
            if (
                self.state["grounded"]
                and abs(self.state["x"] - x) < 18
                and abs(self.state["y"] - y) < 8
            ):
                return
            if age > 4:
                raise NavigationError(f"Route step {step} stuck: {self.state}")

    def boss(self) -> None:
        arena = self.state.get(
            "bossArenaMin", 2600.0 if self.state["stage"] == 2 else 3000.0
        )
        self.walk(arena + 60)
        previous_phase = ""
        jump_start: float | None = None
        escape = arena + 300
        first_jump = second_jump = False
        started = self.state["elapsed"]
        previous_health = 3
        captured_phases: set[str] = set()
        while self.state["bossPhase"] != "defeated":
            self.poll()
            if not self.healthy():
                raise NavigationError("Guardian exited without defeat")
            phase = self.state["bossPhase"]
            if phase != previous_phase:
                if phase == "warning":
                    landing = self.state["bossLanding"]
                    escape = landing + (210 if landing < arena + 400 else -210)
                elif phase == "rest":
                    jump_start = self.state["elapsed"] + 1 / 60
                    first_jump = second_jump = False
                previous_phase = phase
            if jump_start is not None:
                age = self.state["elapsed"] - jump_start
                if age >= 0 and not first_jump:
                    self.key(" ")
                    first_jump = True
                if age >= 20 / 60 and not second_jump:
                    self.key(" ")
                    second_jump = True
            target = (
                escape
                if phase in ("warning", "leap", "landing", "hurt")
                else self.state["bossX"]
            )
            self.steer(target)
            if phase in {"warning", "leap", "rest"} and phase not in captured_phases:
                self.capture(f"level-{self.state['stage'] + 1:02d}-boss-{phase}")
                captured_phases.add(phase)
            if self.state["bossHealth"] < previous_health:
                previous_health = self.state["bossHealth"]
                self.capture(
                    f"level-{self.state['stage'] + 1:02d}-boss-{previous_health}"
                )
            if self.state["elapsed"] - started > 45:
                raise NavigationError(f"Guardian timed out: {self.state}")
        self.steer(None)

    def run(self) -> None:
        self.capture("start")
        if self.state["state"] == "TITLE":
            self.key("\r")
            self.wait(lambda s: s["state"] == "PLAYING")
        for index, level in enumerate(levels()):
            assert self.state["stage"] == index, self.state
            step_index = 0
            retries = 0
            while True:
                try:
                    while step_index < len(level["route"]):
                        step = level["route"][step_index]
                        self.event(
                            f"level-{index + 1:02d}-step-{step_index:02d}-{step[0]}-start"
                        )
                        capture_mechanic = (
                            step[0] in {"moving", "mushroom", "bounce"}
                            and step[0] not in self.captured_mechanics
                        )
                        if capture_mechanic:
                            self.capture(f"mechanic-{step[0]}-before")
                        self.follow(step, level)
                        if capture_mechanic:
                            self.capture(f"mechanic-{step[0]}-after")
                            self.captured_mechanics.add(step[0])
                            if step[0] == "moving":
                                self.steer(None)
                                self.wait(lambda state: abs(state["vx"]) < 0.01)
                                self.capture("mechanic-moving-carry-before")
                                before = self.poll().copy()
                                self.wait(
                                    lambda state, started=before["elapsed"]: (
                                        state["elapsed"] - started >= 0.5
                                    )
                                )
                                after = self.state
                                platform = next(
                                    p
                                    for p in level["moving"]
                                    if abs(p[0] - step[1]) < 2
                                )
                                expected = platform[4] * (
                                    math.sin(after["levelAge"] * 1.35)
                                    - math.sin(before["levelAge"] * 1.35)
                                )
                                assert after["grounded"] and after["direction"] == 0
                                assert abs(after["x"] - before["x"] - expected) < 1
                                self.capture("mechanic-moving-carry-after")
                        on_crumble = any(
                            px <= step[1] <= px + width and abs(py - step[2]) < 2
                            for px, py, width, _height in level["crumbles"]
                        )
                        if on_crumble and "crumble" not in self.captured_mechanics:
                            self.captured_mechanics.add("crumble")
                            self.steer(None)
                            self.capture("mechanic-crumble-before")
                            self.wait(
                                lambda state, surface=step[2]: (
                                    not state["grounded"] and state["y"] > surface + 20
                                )
                            )
                            self.capture("mechanic-crumble-collapsed")
                            self.wait(lambda state: state["state"] == "RESPAWNING")
                            self.capture("mechanic-crumble-natural-fall")
                            raise NavigationError(
                                "Intentional crumble fall: verify natural camp recovery"
                            )
                        if (
                            on_crumble
                            and "crumble-restored" not in self.captured_mechanics
                        ):
                            self.captured_mechanics.add("crumble-restored")
                            self.capture("mechanic-crumble-restored")
                        step_index += 1
                    if (index + 1) % 3 == 0:
                        self.boss()
                        self.walk(level["gate"])
                    break
                except NavigationError:
                    if (
                        self.state["state"] not in {"PLAYING", "RESPAWNING"}
                        or retries >= 8
                    ):
                        raise
                    if self.state["state"] == "PLAYING":
                        previous_deaths = self.state["deaths"]
                        self.key("r")
                        self.wait(
                            lambda state, previous=previous_deaths: (
                                state["deaths"] > previous
                            )
                        )
                    retries += 1
                    self.capture(f"level-{index + 1:02d}-failure-{retries}")
                    self.wait(lambda state: state["state"] == "PLAYING")
                    self.capture(f"level-{index + 1:02d}-retry-{retries}")
                    camp = self.state["camp"]
                    cx, cy = level["camps"][camp]
                    if camp == 0:
                        step_index = 0
                    elif (index + 1) % 3 == 0 and cx >= self.state["bossArenaMin"]:
                        step_index = len(level["route"])
                    else:
                        step_index = min(
                            range(len(level["route"])),
                            key=lambda i: math.hypot(
                                level["route"][i][1] - cx, level["route"][i][2] - cy
                            ),
                        )
                    # A real player may wait at camp to change a patrol's phase.
                    rest_started = self.state["elapsed"]
                    self.wait(
                        lambda state, started=rest_started, delay=retries * 0.25: (
                            state["elapsed"] - started >= delay
                        )
                    )
            self.wait(lambda s: s["state"] in ("CHAPTER_COMPLETE", "WON"))
            assert self.state["acorns"] == 3, self.state
            if "secretFound" in self.state:
                assert self.state["secretFound"], self.state
            self.capture(f"level-{index + 1:02d}-complete")
            print(f"Completed {index + 1}/20 {level['name']}", flush=True)
            if index < 19:
                self.chapter_start = self.state["elapsed"]
                self.key("\r")
                self.wait(
                    lambda s, expected=index + 1: (
                        s["stage"] == expected and s["state"] == "PLAYING"
                    )
                )
        assert self.state["state"] == "WON", self.state
        self.capture("victory")
        gems = re.search(
            r"(\d+)/(\d+) gems \|", (self.output / "victory.txt").read_text()
        )
        assert gems is not None, "Victory screen must display the real gem totals"
        result = {
            "status": "passed",
            "artifact_sha256": self.artifact_sha256,
            "seconds_real": time.monotonic() - self.start_real,
            "chapters_completed": 20,
            "guardians_defeated": 6,
            "acorns_collected": 60,
            "fireflies_found": 20,
            "gems_collected": int(gems[1]),
            "gems_available": int(gems[2]),
            "state": self.state,
        }
        (self.output / "result.json").write_text(json.dumps(result, indent=2) + "\n")
        print(json.dumps(result, indent=2), flush=True)

    def close(self) -> None:
        self.terminal.close()
        for chunk in self.terminal.output_chunks():
            self.archive.write(chunk.encode("utf-8"))
        self.files.close()
        # Terminal.save's convenience file contains only startup in this
        # multiprocess reader. The complete, lossless stream is the gzip file.
        (self.output / "terminal.ansi").unlink(missing_ok=True)


def main() -> None:
    game = Campaign()
    try:
        game.run()
    except Exception as error:
        game.capture("failed")
        (game.output / "result.json").write_text(
            json.dumps(
                {"status": "failed", "error": str(error), "state": game.state}, indent=2
            )
            + "\n"
        )
        raise
    finally:
        game.close()


if __name__ == "__main__":
    main()
