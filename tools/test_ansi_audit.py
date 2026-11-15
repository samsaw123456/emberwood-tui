#!/usr/bin/env python3
"""Check audit cleanup using real PTY children, independently of the game."""

from __future__ import annotations

import json
import os
import sys
import time

import pexpect
import pyte
from ansi_audit import OUTPUT, Terminal, TerminalScreen, close_case, require


def main() -> None:
    output = OUTPUT / "cleanup-regressions"
    results = []
    for mode in ("delayed", "unresponsive", "after-failure", "nonzero"):
        directory = output / mode
        directory.mkdir(parents=True, exist_ok=True)
        script = (
            "import os,tty,time;tty.setraw(0);print('READY',flush=True);os.read(0,1);"
        )
        if mode == "unresponsive":
            script += "time.sleep(60)"
        elif mode == "nonzero":
            script += "raise SystemExit(3)"
        else:
            script += "time.sleep(.7);print('clean exit',flush=True)"
        terminal = object.__new__(Terminal)
        terminal.directory = directory
        terminal.child = pexpect.spawn(
            sys.executable, ["-u", "-c", script], encoding="utf-8", timeout=5
        )
        terminal.child.delaybeforesend = 0
        terminal.child.expect("READY")
        terminal.screen = TerminalScreen(80, 24)
        terminal.stream = pyte.Stream(terminal.screen)
        terminal.raw = ""
        terminal.pending_output = ""
        terminal.complete_offset = 0
        original_failure = "Original control assertion failed"
        row = {
            "mode": mode,
            "status": "FAILED" if mode == "unresponsive" else "PASSED",
            "actual": original_failure if mode == "unresponsive" else "PTY started",
        }
        started = time.monotonic()
        close_case(terminal, row)
        row["seconds"] = round(time.monotonic() - started, 3)
        cleanup = json.loads((directory / "cleanup.json").read_text())
        row["cleanup"] = cleanup
        require(not terminal.child.isalive(), "Owned child remained alive")
        try:
            os.kill(terminal.child.pid, 0)
        except ProcessLookupError:
            pass
        else:
            raise AssertionError("Owned child was not reaped")
        if mode in ("delayed", "after-failure"):
            require(row["status"] == "PASSED", str(row))
            require(terminal.child.exitstatus == 0, str(cleanup))
            require("clean exit" in terminal.raw, "Final PTY output was not drained")
        else:
            require(row["status"] == "FAILED", str(row))
            require("cleanup:" in row["actual"], str(row))
            require(cleanup["forced"] == (mode == "unresponsive"), str(cleanup))
            if mode == "unresponsive":
                require(row["actual"].startswith(original_failure), str(row))
            else:
                require(cleanup["exit_status"] == 3, str(cleanup))
        results.append(row)
        print(f"PASSED cleanup regression {mode}: expected {row['status']}")
    java_home = output / "failing-java"
    (java_home / "bin").mkdir(parents=True, exist_ok=True)
    java = java_home / "bin/java"
    java.write_text("#!/bin/sh\nprintf 'intentional startup failure\\n'\nexit 3\n")
    java.chmod(0o755)
    previous_java = os.environ.get("EMBERWOOD_JAVA_HOME")
    os.environ["EMBERWOOD_JAVA_HOME"] = str(java_home)
    try:
        try:
            Terminal("playing", "cleanup-regressions/startup-nonzero")
        except AssertionError as error:
            message = str(error)
            require("AuditHost exited during startup" in message, message)
            require("intentional startup failure" in message, message)
            require("cleanup:" in message and "exit=3" in message, message)
            require(error.__cause__ is not None, "Original startup cause was lost")
            results.append(
                {"mode": "startup-nonzero", "status": "FAILED", "actual": message}
            )
        else:
            raise AssertionError("Failing Java shim was accepted as a ready game")
    finally:
        if previous_java is None:
            os.environ.pop("EMBERWOOD_JAVA_HOME")
        else:
            os.environ["EMBERWOOD_JAVA_HOME"] = previous_java
    print("PASSED cleanup regression startup-nonzero: both causes preserved")
    (output / "results.json").write_text(
        json.dumps({"status": "PASSED", "cases": results}, indent=2) + "\n"
    )


if __name__ == "__main__":
    main()
