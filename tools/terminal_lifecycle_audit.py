#!/usr/bin/env python3
"""Physical Ctrl+Z and exact stty probes through isolated interactive Bash."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shlex
import shutil
import subprocess
import sys
import termios
import time
from pathlib import Path

import pexpect
import pyte

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from ansi_audit import Terminal, TerminalScreen

OUT = ROOT / "build/terminal-lifecycle"
INSTALLED_LAUNCHER = None
PROMPT = "EMBERWOOD_AUDIT_SHELL> "


class ShellTerminal(Terminal):
    def __init__(self, name: str):
        self.directory = OUT / name
        self.directory.mkdir(parents=True, exist_ok=True)
        self.screen = TerminalScreen(120, 40)
        self.stream = pyte.Stream(self.screen)
        self.raw = ""
        self.pending_output = ""
        self.complete_offset = 0
        self.frames = []
        self.observation = {}
        self.in_game = False
        env = dict(
            os.environ,
            TERM="xterm-256color",
            COLORTERM="truecolor",
            LANG="C.UTF-8",
            PS1=PROMPT,
            PROMPT_COMMAND="",
            XDG_CONFIG_HOME=str(self.directory / "config"),
            EMBERWOOD_TUI_COLOR="truecolor",
        )
        env.pop("BASH_ENV", None)
        self.child = pexpect.spawn(
            "bash",
            ["--noprofile", "--norc", "-i"],
            cwd=str(ROOT),
            env=env,
            encoding="utf-8",
            codec_errors="replace",
            dimensions=(40, 120),
            timeout=1,
        )
        self.child.delaybeforesend = 0
        self.wait(lambda: PROMPT in self.text(), "initial shell prompt")

    def state(self):
        return dict(self.observation)

    def pump(self, duration):
        super().pump(duration)
        if not self.in_game:
            self.finish_output()

    def wait(self, predicate, reason, timeout=15):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            self.pump(0.03)
            if predicate():
                return
            assert self.child.isalive(), f"shell died while waiting for {reason}"
        raise AssertionError(f"Timed out: {reason}\n{self.text()}")

    def shell(self, command):
        assert not self.in_game
        start = len(self.raw)
        self.child.sendline(command)
        self.wait(lambda: PROMPT in self.raw[start:], "shell command completion")

    def input(self, data, seconds=0.2):
        self.child.send(data)
        self.pump(seconds)

    def cleanup(self):
        if self.child.isalive():
            if self.in_game:
                self.input("q", 0.3)
                self.in_game = False
                self.finish_output()
            self.child.sendline("exit")
            self.pump(0.2)
        if self.child.isalive():
            self.child.terminate(force=True)
        self.child.close()
        (self.directory / "terminal.ansi").write_text(self.raw)


def run_case(entry, scenario, quit_key, profile=None, suffix=""):
    profile = profile or ("default" if quit_key == "q" else "ixoff")
    name = f"{entry}-{scenario}-{'q' if quit_key == 'q' else 'ctrl-c'}{suffix}"
    t = ShellTerminal(name)
    result = {
        "entry": entry,
        "state": scenario,
        "exit": "Q" if quit_key == "q" else "Ctrl+C",
        "status": "FAILED",
        "ctrl_z_bytes": "1a",
        "initial_terminal_profile": profile,
    }
    try:
        t.shell("set +o emacs")
        if profile == "ixoff":
            t.shell("stty -ixon ixoff erase '^H'")
        before = t.directory / "before.stty"
        after = t.directory / "after.stty"
        t.shell(f"stty -g > {shlex.quote(str(before))}")
        baseline = before.read_text().strip()
        baseline_attributes = termios.tcgetattr(t.child.child_fd)
        assert (
            baseline_attributes[3] & termios.ECHO
            and baseline_attributes[3] & termios.ICANON
        )
        t.observation = {"phase": "shell before launch", "stty": baseline}
        t.save("00-shell")
        launcher = ROOT / "emberwood-tui" if entry == "source" else INSTALLED_LAUNCHER
        t.in_game = True
        t.child.sendline(f"{shlex.quote(str(launcher))} --offline")
        t.wait(
            lambda: (
                "Enter: start adventure" in t.text()
                and t.screen.primary is not None
                and t.screen.buffer[0][119].bg != "default"
            ),
            "actual title",
        )
        t.pump(0.4)
        assert termios.tcgetattr(t.child.child_fd)[3] & termios.NOFLSH, (
            "Game must preserve active ANSI writes when terminal signals arrive"
        )
        if scenario != "title":
            t.input("\r")
            t.wait(lambda: "Steering: STOP" in t.text(), "playing HUD")
        if scenario == "paused":
            t.input("p")
            t.wait(lambda: "Paused   P/Esc resume" in t.text(), "pause menu")
        if scenario == "playing":
            t.input("d", 0.15)
            t.wait(lambda: "Steering: RIGHT" in t.text(), "moving before suspend")
        t.observation = {"phase": "before physical Ctrl+Z", "game_state": scenario}
        t.save("01-before-ctrl-z")
        fg_before = os.tcgetpgrp(t.child.child_fd)
        t.input("\x1a", 0.45)
        fg_after = os.tcgetpgrp(t.child.child_fd)
        assert fg_after == fg_before, (
            "Ctrl+Z must use in-game pause, retaining foreground ownership"
        )
        expected = (
            "Enter: start adventure" if scenario == "title" else "Paused   P/Esc resume"
        )
        t.wait(
            lambda: expected in t.text(), "recoverable Ctrl+Z pause or unchanged title"
        )
        t.observation = {
            "phase": "after physical Ctrl+Z",
            "observed": expected,
            "foreground_pgid": fg_after,
        }
        t.save("02-after-ctrl-z")
        t.input("\x1a", 0.15)
        assert expected in t.text(), "Repeated Ctrl+Z must not resume"
        result["ctrl_z_behavior"] = (
            "title unchanged; Enter remains usable"
            if scenario == "title"
            else "paused; repeat remains paused; P resumes"
        )
        if scenario == "title":
            t.input("\r")
            t.wait(lambda: "Steering: STOP" in t.text(), "start after Ctrl+Z")
        else:
            t.input("p")
            t.wait(lambda: "Steering: STOP" in t.text(), "resume after Ctrl+Z")
        t.observation = {
            "phase": "recovered command after Ctrl+Z",
            "action": "Enter" if scenario == "title" else "P",
        }
        t.save("03-responsive")
        start = len(t.raw)
        t.input(quit_key, 0.35)
        t.wait(lambda: PROMPT in t.raw[start:], "return to actual interactive shell")
        t.in_game = False
        t.finish_output()
        assert t.screen.primary is None, "alternate screen remained active"
        assert "\ufffd" not in t.raw, "Signal truncated a UTF-8 terminal write"
        exit_status = t.directory / "exit-status.txt"
        t.shell(f"printf '%s\\n' \"$?\" > {shlex.quote(str(exit_status))}")
        assert exit_status.read_text() == "0\n", "Game did not exit successfully"
        t.shell(f"stty -g > {shlex.quote(str(after))}")
        restored = after.read_text().strip()
        restored_attributes = termios.tcgetattr(t.child.child_fd)
        result.update(
            stty_before=baseline,
            stty_after=restored,
            speed_before=baseline_attributes[4:6],
            speed_after=restored_attributes[4:6],
        )
        assert baseline == restored, f"stty changed: {baseline} != {restored}"
        assert baseline_attributes == restored_attributes, (
            "native termios attributes changed"
        )
        recovery = t.directory / "shell-recovered.txt"
        t.shell(f"printf 'ok\\n' > {shlex.quote(str(recovery))}")
        assert recovery.read_text() == "ok\n"
        result.update(
            status="PASSED",
            stty_before=baseline,
            stty_after=restored,
            exact_stty_restored=True,
            shell_command_recovered=True,
            primary_screen_restored=True,
            exit_status=0,
            foreground_before=fg_before,
            foreground_after_ctrl_z=fg_after,
        )
        t.observation = {
            "phase": "shell after quit",
            "stty": restored,
            "shell_command_recovered": True,
        }
        t.save("04-restored-shell")
    except (AssertionError, OSError, UnicodeError, pexpect.ExceptionPexpect) as error:
        result["error"] = str(error)
        foreground = os.tcgetpgrp(t.child.child_fd)
        result["failure_foreground_pid"] = foreground
        result["failure_termios"] = repr(termios.tcgetattr(t.child.child_fd))
        if foreground != t.child.pid:
            java_home = os.environ.get("JAVA_HOME")
            jcmd = (
                str(Path(java_home) / "bin/jcmd") if java_home else shutil.which("jcmd")
            )
            try:
                diagnostic = subprocess.run(
                    [jcmd or "jcmd", str(foreground), "Thread.print"],
                    capture_output=True,
                    text=True,
                    timeout=8,
                    check=False,
                )
                (t.directory / "failure-threads.txt").write_text(
                    diagnostic.stdout + diagnostic.stderr
                )
            except (OSError, subprocess.TimeoutExpired) as diagnostic_error:
                (t.directory / "failure-threads.txt").write_text(str(diagnostic_error))
        t.observation = {"phase": "failure", "error": str(error)}
        t.save("failure")
    finally:
        t.cleanup()
    result["captures"] = t.frames
    (t.directory / "result.json").write_text(json.dumps(result, indent=2) + "\n")
    print(
        name,
        result["status"],
        result.get("ctrl_z_behavior", result.get("error")),
        flush=True,
    )
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUT)
    parser.add_argument("--installed-launcher", type=Path)
    args = parser.parse_args()
    OUT = args.output.resolve()
    INSTALLED_LAUNCHER = args.installed_launcher
    if INSTALLED_LAUNCHER is not None:
        INSTALLED_LAUNCHER = INSTALLED_LAUNCHER.expanduser().absolute()
        assert INSTALLED_LAUNCHER.is_file(), "Installed launcher does not exist"
    results = []
    started = time.monotonic()
    for entry in ("source", "installed") if INSTALLED_LAUNCHER else ("source",):
        for scenario in ("title", "playing", "paused"):
            for quit_key in ("q", "\x03"):
                results.append(run_case(entry, scenario, quit_key))
        # A Ctrl+C arriving during a repaint after resume previously stranded
        # the native writer. Keep repeated default and customized termios probes.
        for profile in ("default", "ixoff"):
            for repeat in range(2):
                results.append(
                    run_case(
                        entry,
                        "playing",
                        "\x03",
                        profile,
                        f"-{profile}-repaint-{repeat + 1}",
                    )
                )
    report = {
        "jar_sha256": hashlib.sha256(
            (ROOT / "build/libs/emberwood-tui.jar").read_bytes()
        ).hexdigest(),
        "duration_seconds": round(time.monotonic() - started, 2),
        "results": results,
    }
    (OUT / "results.json").write_text(json.dumps(report, indent=2) + "\n")
    raise SystemExit(0 if all(r["status"] == "PASSED" for r in results) else 1)
