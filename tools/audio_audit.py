#!/usr/bin/env python3
"""Capture the game's actual PipeWire output while ANSI keys toggle sound.

This adds a recording link from this test process's playback node only. It never
changes speaker volume, mute, default devices, or links belonging to other apps.
Requires Linux PipeWire tools and the ANSI audit's Python dependencies.
"""

from __future__ import annotations

import array
import json
import math
import os
import shutil
import signal
import subprocess
import time
from pathlib import Path

from ansi_audit import Terminal

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "build/audio-audit"


def command(*arguments: str) -> str:
    return subprocess.check_output(arguments, text=True, timeout=10)


def objects() -> list[dict]:
    return json.loads(command("pw-dump"))


def properties(item: dict) -> dict:
    return item.get("info", {}).get("props", {})


def playback_port(process_id: int) -> int:
    snapshot = objects()
    clients = {
        item["id"]
        for item in snapshot
        if item["type"] == "PipeWire:Interface:Client"
        and str(properties(item).get("application.process.id")) == str(process_id)
    }
    nodes = {
        item["id"]
        for item in snapshot
        if item["type"] == "PipeWire:Interface:Node"
        and properties(item).get("client.id") in clients
        and properties(item).get("media.class") == "Stream/Output/Audio"
    }
    for item in snapshot:
        props = properties(item)
        if (
            item["type"] == "PipeWire:Interface:Port"
            and props.get("node.id") in nodes
            and props.get("port.direction") == "out"
            and props.get("audio.channel") in ("FL", "MONO")
        ):
            return item["id"]
    raise RuntimeError(
        f"No playback port belongs to this game's Java PID {process_id}."
    )


def capture_port(name: str) -> int | None:
    snapshot = objects()
    nodes = {
        item["id"]
        for item in snapshot
        if item["type"] == "PipeWire:Interface:Node"
        and properties(item).get("node.name") == name
    }
    return next(
        (
            item["id"]
            for item in snapshot
            if item["type"] == "PipeWire:Interface:Port"
            and properties(item).get("node.id") in nodes
            and properties(item).get("port.direction") == "in"
        ),
        None,
    )


def capture(terminal: Terminal, label: str, jump: bool) -> dict:
    filename = OUTPUT / f"{label}.pcm"
    node_name = f"emberwood-audio-audit-{os.getpid()}"
    output_port = playback_port(terminal.child.pid)
    with (OUTPUT / f"{label}.log").open("w") as log:
        recording = subprocess.Popen(
            [
                "pw-record",
                "--target",
                "0",
                "--rate",
                "22050",
                "--channels",
                "1",
                "--format",
                "s16",
                "--raw",
                "--properties",
                f"{{ node.name = {node_name} node.autoconnect = false }}",
                str(filename),
            ],
            stdout=log,
            stderr=log,
        )
        try:
            deadline = time.monotonic() + 5
            input_port = None
            while time.monotonic() < deadline:
                input_port = capture_port(node_name)
                if input_port is not None:
                    break
                terminal.pump(0.05)
            if input_port is None:
                raise RuntimeError(
                    "PipeWire did not expose the isolated recording port."
                )
            command("pw-link", str(output_port), str(input_port))
            terminal.pump(0.3)
            terminal.save(f"{label}-before")
            if jump:
                terminal.key("Space", wait=0.2)
            terminal.save(f"{label}-during")
            terminal.pump(0.8)
            terminal.save(f"{label}-after")
        finally:
            recording.send_signal(signal.SIGINT)
            try:
                recording.wait(timeout=3)
            except subprocess.TimeoutExpired:
                recording.kill()
                recording.wait(timeout=3)
    samples = array.array("h")
    samples.frombytes(filename.read_bytes())
    if len(samples) < 1000:
        raise RuntimeError(
            f"Only {len(samples)} PCM samples captured; see {label}.log."
        )
    return {
        "samples": len(samples),
        "peak": max(abs(value) for value in samples),
        "rms": math.sqrt(sum(value * value for value in samples) / len(samples)),
        "nonzero": sum(value != 0 for value in samples),
        "file": str(filename.relative_to(ROOT)),
    }


def main() -> None:
    for tool in ("pw-record", "pw-link", "pw-dump", "wpctl"):
        if shutil.which(tool) is None:
            raise SystemExit(
                f"UNVERIFIED: {tool} is unavailable; audio output audit needs Linux PipeWire."
            )
    OUTPUT.mkdir(parents=True, exist_ok=True)
    before_volume = command("wpctl", "get-volume", "@DEFAULT_AUDIO_SINK@")
    terminal = Terminal("playing", "audio-output")
    try:
        terminal.pump(0.5)
        if not terminal.state()["audioAvailable"]:
            raise RuntimeError("Java could not open its playback device.")
        if terminal.state()["muted"]:
            terminal.key("M")
        result = {"baseline": capture(terminal, "baseline", jump=False)}
        result["enabled"] = capture(terminal, "enabled", jump=True)
        terminal.key("M")
        result["muted"] = capture(terminal, "muted", jump=True)
        terminal.key("M")
        result["restored"] = capture(terminal, "restored", jump=True)
        if result["enabled"]["peak"] < 200 or result["restored"]["peak"] < 200:
            raise AssertionError(
                "Jump did not reach the actual audio stream with sound enabled/restored."
            )
        if result["baseline"]["peak"] != 0 or result["muted"]["peak"] != 0:
            raise AssertionError(
                "The isolated baseline/muted playback stream was not silent."
            )
        after_volume = command("wpctl", "get-volume", "@DEFAULT_AUDIO_SINK@")
        if after_volume != before_volume:
            raise AssertionError(
                "Speaker volume changed during the test; investigate external audio activity."
            )
        result["speaker_state_before_and_after"] = before_volume.strip()
        result["status"] = "PASSED"
        result["limitation"] = (
            "Captured Java playback PCM; physical loudspeaker audibility and macOS audio were not tested."
        )
        (OUTPUT / "results.json").write_text(json.dumps(result, indent=2) + "\n")
        print(json.dumps(result, indent=2))
    finally:
        terminal.close()


if __name__ == "__main__":
    main()
