#!/usr/bin/env python3
"""Measure the public JAR through a Linux PTY; never build or use fixture classes.

Requires pexpect and pyte from tools/audit-requirements.txt. CPU is measured for
Java alone; emitted ANSI paints are distinct from the game's simulation ticks.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import re
import subprocess
import time
from pathlib import Path

import pexpect
import pyte

ROOT = Path(__file__).resolve().parents[1]


class Measurement:
    def __init__(
        self,
        output: Path,
        columns: int = 100,
        rows: int = 36,
        heap_mib: int = 256,
        single_cpu: bool = False,
    ):
        self.output = output
        self.output.mkdir(parents=True, exist_ok=True)
        self.columns = columns
        self.rows = rows
        self.cpu = min(os.sched_getaffinity(0)) if single_cpu else None
        java_home = subprocess.check_output(
            [str(ROOT / "tools/bootstrap.sh"), "--offline"], text=True
        ).strip()
        self.jar = ROOT / "build/libs/emberwood-tui.jar"
        self.command = [
            str(Path(java_home) / "bin/java"),
            "-Xms32m",
            f"-Xmx{heap_mib}m",
            "--enable-native-access=ALL-UNNAMED",
            "-Djava.awt.headless=true",
            "-jar",
            str(self.jar),
        ]
        self.child = pexpect.spawn(
            self.command[0],
            self.command[1:],
            cwd=str(ROOT),
            env=os.environ
            | {
                "TERM": "xterm-256color",
                "COLORTERM": "truecolor",
                "LANG": "C.UTF-8",
                "XDG_CONFIG_HOME": str(self.output / "settings"),
            },
            encoding=None,
            dimensions=(rows, columns),
            timeout=1,
            preexec_fn=(
                (lambda: os.sched_setaffinity(0, {self.cpu})) if single_cpu else None
            ),
        )
        self.child.delaybeforesend = 0
        self.raw: list[bytes] = []
        self.parsed_chunks = 0
        self.screen = pyte.Screen(columns, rows)
        self.stream = pyte.ByteStream(self.screen)
        self.clock_ticks = os.sysconf("SC_CLK_TCK")

    def stats(self) -> dict:
        fields = (
            Path(f"/proc/{self.child.pid}/stat").read_text().split(") ", 1)[1].split()
        )
        status = Path(f"/proc/{self.child.pid}/status").read_text()

        def kib(label: str) -> int:
            match = re.search(rf"^{label}:\s+(\d+)", status, re.MULTILINE)
            assert match, f"Linux process status has no {label} field"
            return int(match.group(1))

        return {
            "cpu_seconds": (int(fields[11]) + int(fields[12])) / self.clock_ticks,
            "rss_kib": kib("VmRSS"),
            "lifetime_peak_rss_kib": kib("VmHWM"),
        }

    def pump(self, duration: float) -> bytes:
        raw = []
        end = time.monotonic() + duration
        while time.monotonic() < end:
            try:
                data = self.child.read_nonblocking(
                    131072, timeout=min(0.025, max(0.001, end - time.monotonic()))
                )
                raw.append(data)
                self.raw.append(data)
                if b"\x1b[6n" in data:
                    self.child.send(b"\x1b[1;1R")
            except pexpect.TIMEOUT:
                pass
            except pexpect.EOF:
                break
        return b"".join(raw)

    def text(self) -> str:
        # Parse outside the measured intervals so Python rendering cannot throttle
        # the game's output; retain the stream state between observations.
        self.stream.feed(b"".join(self.raw[self.parsed_chunks :]))
        self.parsed_chunks = len(self.raw)
        return "\n".join(self.screen.display)

    def sample(
        self,
        label: str,
        duration: float,
        events: list[tuple[float, bytes]] | None = None,
    ) -> dict:
        events = events or []
        chunks = []
        samples = []
        start_stats = self.stats()
        started = time.monotonic()
        pending = list(events)
        while time.monotonic() - started < duration:
            elapsed = time.monotonic() - started
            while pending and pending[0][0] <= elapsed:
                _, key = pending.pop(0)
                self.child.send(key)
            chunks.append(self.pump(min(0.1, max(0.001, duration - elapsed))))
            samples.append(
                {
                    "elapsed_seconds": round(time.monotonic() - started, 4),
                    **self.stats(),
                }
            )
        elapsed = time.monotonic() - started
        end_stats = self.stats()
        raw = b"".join(chunks)
        # tui4j RendererFlush brackets each emitted paint with DEC autowrap
        # disable/enable. Identical views produce no paint at all.
        paints = raw.count(b"\x1b[?7h")
        cpu_seconds = end_stats["cpu_seconds"] - start_stats["cpu_seconds"]
        result = {
            "state": label,
            "duration_seconds": round(elapsed, 4),
            "cpu_seconds": round(cpu_seconds, 4),
            "cpu_percent_one_core": round(cpu_seconds / elapsed * 100, 2),
            "peak_sampled_rss_mib": round(
                max(item["rss_kib"] for item in samples) / 1024, 2
            ),
            "lifetime_peak_rss_mib": round(
                end_stats["lifetime_peak_rss_kib"] / 1024, 2
            ),
            "emitted_paints": paints,
            "emitted_paints_per_second": round(paints / elapsed, 3),
            "ansi_bytes": len(raw),
            "ansi_kib_per_second": round(len(raw) / 1024 / elapsed, 2),
            "physical_inputs": [
                {"seconds": seconds, "bytes": repr(key)} for seconds, key in events
            ],
        }
        (self.output / f"{label}.ansi").write_bytes(raw)
        (self.output / f"{label}.txt").write_text(self.text() + "\n")
        (self.output / f"{label}-samples.json").write_text(
            json.dumps(samples, indent=2) + "\n"
        )
        print(json.dumps(result), flush=True)
        return result

    def run(self, duration: float) -> None:
        started = time.monotonic()
        while time.monotonic() - started < 20:
            self.pump(0.1)
            if b"Enter:" in b"".join(self.raw):
                break
        else:
            raise AssertionError("Public JAR title did not appear")
        self.pump(5)
        assert "Enter: start adventure" in self.text()
        results = [self.sample("title", duration)]
        self.child.send(b"\r")
        self.pump(1)
        assert "Steering: STOP" in self.text() and "Paused" not in self.text()
        events = []
        for base in range(0, int(duration), 4):
            events.extend(
                [
                    (base, b"\x1b[C"),
                    (base + 0.3, b"s"),
                    (base + 1, b" "),
                    (base + 2, b"\x1b[D"),
                    (base + 2.3, b"\x1b[B"),
                ]
            )
        results.append(
            self.sample("active", duration, [e for e in events if e[0] < duration])
        )
        assert "Paused" not in self.text() and "Hearts 3/3" in self.text()
        self.child.send(b"p")
        self.pump(1)
        assert "Paused" in self.text()
        results.append(self.sample("paused", duration))
        self.child.send(b"q")
        self.pump(2)
        self.child.close()
        assert self.child.exitstatus == 0
        report = {
            "artifact_sha256": hashlib.sha256(self.jar.read_bytes()).hexdigest(),
            "command": self.command,
            "terminal": f"real PTY, {self.columns} columns x {self.rows} rows, truecolor",
            "single_cpu_affinity": self.cpu,
            "warmup_seconds": 5,
            "cpu_definition": "Linux /proc/PID/stat utime+stime delta over wall time. 100% is one fully used CPU core.",
            "rss_definition": "Per-state VmRSS sampled every 0.1s; VmHWM is separate lifetime peak.",
            "frame_definition": "ANSI paints counted by DEC autowrap-enable terminator ESC[?7h. Static unchanged screens are deduplicated by tui4j; emitted paints are not simulation ticks.",
            "states": results,
            "exit_status": self.child.exitstatus,
        }
        (self.output / "results.json").write_text(json.dumps(report, indent=2) + "\n")

    def close(self) -> None:
        if self.child.isalive():
            self.child.send(b"q")
            self.pump(0.4)
        if self.child.isalive():
            self.child.terminate(force=True)
        self.child.close()
        (self.output / "terminal.ansi").write_bytes(b"".join(self.raw))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--duration", type=float, default=15, help="Seconds per state")
    parser.add_argument("--output", type=Path, default=ROOT / "build/performance")
    parser.add_argument("--columns", type=int, default=100)
    parser.add_argument("--rows", type=int, default=36)
    parser.add_argument("--heap-mib", type=int, default=256)
    parser.add_argument("--single-cpu", action="store_true")
    args = parser.parse_args()
    if platform.system() != "Linux":
        parser.error(
            "CPU/RSS measurement requires Linux /proc; this is not a macOS benchmark"
        )
    if not 3 <= args.duration <= 60:
        parser.error("Choose 3 to 60 seconds per state")
    if not 80 <= args.columns <= 180 or not 28 <= args.rows <= 65:
        parser.error("Choose 80–180 columns and 28–65 rows")
    if not 64 <= args.heap_mib <= 256:
        parser.error("Choose a 64–256 MiB heap")
    measurement = Measurement(
        args.output.resolve(),
        args.columns,
        args.rows,
        args.heap_mib,
        args.single_cpu,
    )
    try:
        measurement.run(args.duration)
    finally:
        measurement.close()


if __name__ == "__main__":
    main()
