#!/usr/bin/env python3
"""Test installation in disposable Ubuntu Base with real apt and network-denied play.

Needs Linux x86-64, bubblewrap, tar and tools/audit-requirements.txt on the host.
No host packages, groups or application launchers are changed.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import time
import urllib.request
from pathlib import Path
from types import SimpleNamespace

import pexpect
import pyte
from ansi_audit import Terminal, TerminalScreen

ROOT = Path(__file__).resolve().parents[1]
BASE_URL = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-amd64.tar.gz"
BASE_SHA = "e77b6f10c2590cef872b33ee9f635a0e3fd1f57fb074c0e52b5c7f56147a0c86"


def download(url, path):
    with urllib.request.urlopen(url, timeout=60) as source, path.open("wb") as target:
        shutil.copyfileobj(source, target)


def play(command, output):
    child = pexpect.spawn(
        command[0],
        command[1:],
        dimensions=(40, 120),
        encoding="utf-8",
        codec_errors="replace",
    )
    raw = ""
    offset = 0
    screen = TerminalScreen(120, 40)
    stream = pyte.Stream(screen)

    def wait_for(expected):
        nonlocal raw, offset
        until = time.monotonic() + 30
        while time.monotonic() < until:
            try:
                fragment = child.read_nonblocking(131072, timeout=0.1)
            except pexpect.TIMEOUT:
                continue
            raw += fragment
            if "\x1b[6n" in fragment:
                child.send("\x1b[1;1R")
            marker = raw.rfind("\x1b[?7h")
            if marker >= 0 and marker + len("\x1b[?7h") > offset:
                end = marker + len("\x1b[?7h")
                stream.feed(raw[offset:end])
                offset = end
                if expected in "\n".join(screen.display):
                    return
        raise AssertionError(f"Minimal installed game did not show {expected!r}")

    def capture(name):
        context = SimpleNamespace(
            screen=screen,
            state=dict,
            complete_offset=offset,
            directory=output,
            text=lambda: "\n".join(screen.display),
            raw=raw,
            frames=[],
        )
        Terminal.save(context, name)

    try:
        wait_for("Enter:")
        capture("01-title")
        for key, expected, label in [
            ("\r", "Steering: STOP", "02-playing"),
            ("d", "Steering: RIGHT", "03-right"),
            ("s", "Steering: STOP", "04-stopped"),
            ("\x1a", "Paused", "05-suspend-paused"),
            ("p", "Steering: STOP", "06-resumed"),
        ]:
            child.send(key)
            wait_for(expected)
            capture(label)
        child.send("q")
        child.expect(pexpect.EOF, timeout=10)
        raw += child.before
        child.close()
        assert child.exitstatus == 0
        assert "\x1b[?1049l" in raw
    finally:
        if child.isalive():
            child.terminate(force=True)
        child.close()
        (output / "terminal.ansi").write_text(raw)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--archive", type=Path)
    source.add_argument("--installer-url")
    parser.add_argument(
        "--output", type=Path, default=ROOT / "build/quality-review/minimal-final"
    )
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(ROOT):
        parser.error("Keep output inside this checkout for relative evidence paths")
    if os.uname().machine != "x86_64" or not shutil.which("bwrap"):
        parser.error("Needs Linux x86-64 and bubblewrap")
    output.mkdir(parents=True, exist_ok=True)
    base = ROOT / "build/quality-review/ubuntu-base-24.04.5.tar.gz"
    if not base.exists():
        download(BASE_URL, base)
    assert hashlib.sha256(base.read_bytes()).hexdigest() == BASE_SHA
    with tempfile.TemporaryDirectory(prefix="rootfs-", dir=output) as temporary:
        fs = Path(temporary)
        subprocess.run(
            ["tar", "--no-same-owner", "-xzf", str(base), "-C", str(fs)], check=True
        )
        # A single mapped GID cannot represent the optional font/log groups.
        # Adapt only these groups in the disposable image; package scripts remain real.
        groups = fs / "etc/group"
        rows = []
        for line in groups.read_text().splitlines():
            fields = line.split(":")
            if fields[0] in {"staff", "adm"}:
                fields[2] = "0"
            rows.append(":".join(fields))
        groups.write_text("\n".join(rows) + "\n")
        (fs / "etc/apt/apt.conf.d/99-emberwood-audit").write_text(
            'APT::Sandbox::User "root";\n'
        )
        command = [
            "bwrap",
            "--unshare-user",
            "--uid",
            "0",
            "--gid",
            "0",
            "--bind",
            str(fs),
            "/",
            "--dev",
            "/dev",
            "--proc",
            "/proc",
            "--ro-bind",
            "/etc/resolv.conf",
            "/etc/resolv.conf",
            "--chdir",
            "/root",
        ]
        for key, value in {
            "HOME": "/root",
            "PATH": "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM": "xterm-256color",
            "COLORTERM": "truecolor",
            "LANG": "C.UTF-8",
        }.items():
            command.extend(["--setenv", key, value])
        for key in [
            "DISPLAY",
            "WAYLAND_DISPLAY",
            "JAVA_HOME",
            "XDG_CACHE_HOME",
            "XDG_CONFIG_HOME",
            "EMBERWOOD_TUI_JAVA_HOME",
            "EMBERWOOD_TUI_CACHE_DIR",
            "TAR_OPTIONS",
        ]:
            command.extend(["--unsetenv", key])
        before = subprocess.check_output(
            command
            + [
                "--",
                "/bin/bash",
                "-c",
                'for x in curl wget fc-match java xterm; do command -v "$x" || :; done; test ! -s /etc/ssl/certs/ca-certificates.crt',
            ],
            text=True,
        )
        assert not before.strip(), before
        if args.archive:
            subprocess.run(
                [
                    "tar",
                    "--no-same-owner",
                    "-xzf",
                    str(args.archive.resolve()),
                    "-C",
                    str(fs / "opt"),
                ],
                check=True,
            )
            installer = next((fs / "opt").glob("emberwood-tui-*/install.sh"))
        else:
            installer = fs / "tmp/install.sh"
            download(args.installer_url, installer)
        started = time.monotonic()
        with (output / "install.log").open("w") as log:
            subprocess.run(
                command + ["--", "/bin/bash", "/" + str(installer.relative_to(fs))],
                stdout=log,
                stderr=subprocess.STDOUT,
                check=True,
                timeout=600,
            )
        elapsed = time.monotonic() - started
        app = fs / "root/.local/share/emberwood-tui"
        version = subprocess.check_output(
            command
            + ["--", "/root/.local/bin/emberwood-tui", "--offline", "--version"],
            text=True,
        )
        jar_hash = hashlib.sha256(
            (app / "build/libs/emberwood-tui.jar").read_bytes()
        ).hexdigest()
        assert (
            jar_hash
            == hashlib.sha256(
                (ROOT / "build/libs/emberwood-tui.jar").read_bytes()
            ).hexdigest()
        )
        assert (
            "Terminal=true"
            in (
                fs / "root/.local/share/applications/emberwood-terminal.desktop"
            ).read_text()
        )
        assert (
            fs / "root/.local/share/icons/hicolor/512x512/apps/emberwood-terminal.png"
        ).read_bytes() == (ROOT / "packaging/emberwood-terminal.png").read_bytes()
        play(
            command
            + ["--unshare-net", "--", "/root/.local/bin/emberwood-tui", "--offline"],
            output,
        )
        result = {
            "status": "PASSED",
            "ubuntu_base_url": BASE_URL,
            "ubuntu_base_sha256": BASE_SHA,
            "installer_sha256": hashlib.sha256(installer.read_bytes()).hexdigest(),
            "jar_sha256": jar_hash,
            "version": version.strip(),
            "install_seconds": round(elapsed, 3),
            "missing_before": [
                "curl",
                "wget",
                "CA certificates",
                "fontconfig",
                "Java",
                "desktop terminal",
            ],
            "actual_package_install": True,
            "host_packages_modified": False,
            "isolation_adaptations": [
                "APT sandbox user root",
                "staff/adm GIDs mapped to root in disposable image",
            ],
            "offline_play": "Network namespace disabled: title/start/right/stop/CtrlZ/pause/resume/Q; complete ANSI frames retained",
            "desktop_and_icon": "Terminal=true entry and original icon bytes; GUI activation separately checked on host",
            "source": str(args.archive) if args.archive else args.installer_url,
        }
        (output / "results.json").write_text(json.dumps(result, indent=2) + "\n")
        print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
