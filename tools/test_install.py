#!/usr/bin/env python3
"""Exercise the real installer in an isolated prefix; never alter the real app menu."""

from __future__ import annotations

import errno
import fcntl
import hashlib
import json
import os
import platform
import pty
import select
import shutil
import signal
import struct
import subprocess
import tarfile
import tempfile
import termios
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def terminal_probe(command: Path, env: dict[str, str], toggle: bool) -> str:
    """Run the installed public CLI through its actual terminal, with isolated preferences."""
    child, descriptor = pty.fork()
    if child == 0:
        os.execve(str(command), [str(command), "--offline"], env)
    output = ""
    reaped = False
    try:
        fcntl.ioctl(descriptor, termios.TIOCSWINSZ, struct.pack("HHHH", 40, 120, 0, 0))
        deadline = time.monotonic() + 20
        ready = False
        while time.monotonic() < deadline:
            readable, _, _ = select.select([descriptor], [], [], 0.1)
            if readable:
                fragment = os.read(descriptor, 131072).decode("utf-8", errors="replace")
                output += fragment
                if "\x1b[6n" in fragment:
                    os.write(descriptor, b"\x1b[1;1R")
            if not ready and "Enter:" in output:
                ready = True
                if toggle:
                    output = ""
                    os.write(descriptor, b"m")
            if ready and "sound: off" in output:
                os.write(descriptor, b"q")
                break
        else:
            raise AssertionError(
                f"Installed terminal did not show saved mute state: {output[-2000:]}"
            )
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            # Keep draining while tui4j flushes its final frame and restores the PTY.
            # Waiting without reading can block a renderer on a full terminal buffer.
            readable, _, _ = select.select([descriptor], [], [], 0.05)
            if readable:
                try:
                    fragment = os.read(descriptor, 131072).decode(
                        "utf-8", errors="replace"
                    )
                    output += fragment
                    if "\x1b[6n" in fragment:
                        os.write(descriptor, b"\x1b[1;1R")
                except OSError as error:
                    # Linux reports EIO once the child closes its terminal.
                    if error.errno != errno.EIO:
                        raise
            done, status = os.waitpid(child, os.WNOHANG)
            if done:
                reaped = True
                assert os.waitstatus_to_exitcode(status) == 0
                return output
            time.sleep(0.05)
        raise AssertionError("Installed terminal did not exit after Q.")
    finally:
        os.close(descriptor)
        if not reaped:
            os.kill(child, signal.SIGKILL)
            os.waitpid(child, 0)


def run(*arguments: str, env: dict[str, str] | None = None, success: bool = True):
    result = subprocess.run(
        arguments,
        cwd=ROOT,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=240,
        check=False,
    )
    if (result.returncode == 0) != success:
        raise AssertionError(
            f"{arguments!r}: exit {result.returncode}\n{result.stdout}"
        )
    return result.stdout


def source_launch_concurrency(temp: Path) -> None:
    """A slow fake compiler exposes a partial JAR; neither real launcher may run it."""
    source = temp / "concurrent-source"
    (source / "tools").mkdir(parents=True)
    (source / "src/main").mkdir(parents=True)
    runtime = temp / "fixture-jdk/bin"
    runtime.mkdir(parents=True)
    shutil.copy2(ROOT / "emberwood-tui", source / "emberwood-tui")
    shutil.copy2(ROOT / "tools/bootstrap.sh", source / "tools/bootstrap.sh")
    for name in (
        "build.gradle.kts",
        "settings.gradle.kts",
        "gradle.properties",
        "VERSION",
    ):
        (source / name).write_text("fixture\n")
    java = runtime / "java"
    java.write_text(
        '#!/bin/bash\nset -eu\ncase ${1:-} in\n-version) printf \'openjdk version "25.0.5"\\n\'; exit 0;;\n--list-modules) printf \'java.desktop@25.0.5\\n\'; exit 0;;\nesac\nwhile (($#)); do\nif [[ $1 == -jar ]]; then [[ $(cat "$2") == complete ]] || exit 9; printf "fixture game launched\\n"; exit 0; fi\nshift\ndone\nexit 8\n'
    )
    java.chmod(0o755)
    for name in ("javac", "jlink"):
        executable = runtime / name
        executable.write_text("#!/bin/bash\nexit 0\n")
        executable.chmod(0o755)
    gradle = source / "gradlew"
    gradle.write_text(
        "#!/bin/bash\nset -eu\nmkdir -p build/libs\n"
        'printf "partial" > build/libs/emberwood-tui.jar\n'
        'printf "build\\n" >> build-calls\ntouch build-started\nsleep 2\n'
        'printf "complete" > build/libs/emberwood-tui.jar\n'
    )
    gradle.chmod(0o755)
    env = os.environ | {"EMBERWOOD_TUI_JAVA_HOME": str(runtime.parent)}
    arguments = [str(source / "emberwood-tui"), "--version"]
    first = subprocess.Popen(
        arguments, env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT
    )
    second = None
    try:
        deadline = time.monotonic() + 5
        while not (source / "build-started").exists() and time.monotonic() < deadline:
            time.sleep(0.02)
        assert (source / "build-started").exists(), "Fixture build never started"
        second = subprocess.Popen(
            arguments,
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
        for process in (first, second):
            output, _ = process.communicate(timeout=10)
            assert process.returncode == 0 and "fixture game launched" in output, output
        assert (source / "build-calls").read_text().splitlines() == ["build"]
        assert not (source / ".gradle/emberwood-launch.lock").exists()
    finally:
        for process in (first, second):
            if process is not None and process.poll() is None:
                process.kill()
                process.wait(timeout=5)


def verified_runtime_download(temp: Path) -> None:
    """Accept a pinned archive even when its cache path requires hash-name escaping."""
    fixture = temp / "runtime-download"
    (fixture / "tools").mkdir(parents=True)
    shutil.copy2(ROOT / "tools/bootstrap.sh", fixture / "tools/bootstrap.sh")
    archive = fixture / "runtime.tar.gz"
    with tarfile.open(archive, "w:gz") as output:
        output.add(temp / "fixture-jdk", arcname="jdk-25.0.5+8")
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    target_os = "mac" if platform.system() == "Darwin" else "linux"
    target_arch = "x64" if platform.machine() in ("x86_64", "amd64") else "aarch64"
    (fixture / "tools/jdk-downloads.tsv").write_text(
        f"{target_os}\t{target_arch}\t{digest}\thttps://fixture.invalid/runtime.tar.gz\n"
    )
    fakebin = fixture / "fakebin"
    fakebin.mkdir()
    curl = fakebin / "curl"
    curl.write_text(
        "#!/bin/bash\nset -eu\nwhile (($#)); do\n"
        'if [[ $1 == --output ]]; then cp -- "$EMBERWOOD_AUDIT_ARCHIVE" "$2"; exit 0; fi\n'
        "shift\ndone\nexit 2\n"
    )
    curl.chmod(0o755)
    cache = temp / r"verified cache \ literal"
    env = os.environ | {
        "EMBERWOOD_TUI_CACHE_DIR": str(cache),
        "EMBERWOOD_AUDIT_ARCHIVE": str(archive),
        "PATH": str(fakebin) + os.pathsep + os.environ["PATH"],
    }
    env.pop("EMBERWOOD_TUI_JAVA_HOME", None)
    bootstrap = str(fixture / "tools/bootstrap.sh")
    runtime = run(bootstrap, env=env).splitlines()[-1]
    assert runtime == str(cache / f"temurin-25.0.5+8-{target_os}-{target_arch}/jdk")
    assert run(bootstrap, "--offline", env=env).strip() == runtime
    assert not list(cache.glob("*/download.*"))
    assert not list(cache.glob("*/install.lock"))


def main() -> None:
    evidence: list[str] = []
    run(
        "bash",
        "-n",
        "install.sh",
        "emberwood-tui",
        "tools/bootstrap.sh",
        "tools/package.sh",
    )
    evidence.append("All four shell entry points parse with bash -n.")
    for args in (
        ("--prefix",),
        ("--prefix", "relative"),
        ("--prefix", "/"),
        ("--unknown",),
    ):
        run("./install.sh", *args, success=False)
    run("./emberwood-tui", "--build", "unexpected", success=False)
    evidence.append(
        "Missing, relative and root prefixes, unknown options, invalid build arguments reject safely."
    )
    with tempfile.TemporaryDirectory(prefix="emberwood installer test ") as directory:
        temp = Path(directory)
        source_launch_concurrency(temp)
        evidence.append(
            "Concurrent source launches wait for one complete build; neither executes a partial JAR, and the source lock is removed."
        )
        verified_runtime_download(temp)
        evidence.append(
            "A valid pinned runtime archive installs to a cache path containing spaces and a backslash, runs offline, and leaves no download or lock directory."
        )
        env = os.environ.copy()
        env.update(
            TERM="xterm-256color",
            COLORTERM="truecolor",
            LANG="C.UTF-8",
            XDG_CONFIG_HOME=str(temp / "config"),
        )
        if platform.system() == "Darwin":
            # Keep Java's macOS preference path isolated without changing HOME.
            java_home_option = f'"-Duser.home={temp / "java-user"}"'
            env["JAVA_TOOL_OPTIONS"] = (
                env.get("JAVA_TOOL_OPTIONS", "") + " " + java_home_option
            ).strip()
        if "EMBERWOOD_TUI_JAVA_HOME" not in env:
            env["EMBERWOOD_TUI_JAVA_HOME"] = run(
                "./tools/bootstrap.sh", "--offline"
            ).strip()
        bad_env = env | {"EMBERWOOD_TUI_JAVA_HOME": str(temp / "missing-java")}
        run("./tools/bootstrap.sh", env=bad_env, success=False)
        evidence.append("An invalid explicit Java path fails before installation.")
        cold_env = env | {"EMBERWOOD_TUI_CACHE_DIR": str(temp / "cold-cache")}
        cold_env.pop("EMBERWOOD_TUI_JAVA_HOME", None)
        output = run("./tools/bootstrap.sh", "--offline", env=cold_env, success=False)
        assert "not cached" in output
        # Feed a damaged download through the actual checksum path, in a private cache.
        fakebin = temp / "fakebin"
        fakebin.mkdir()
        fakecurl = fakebin / "curl"
        fakecurl.write_text(
            "#!/bin/bash\nset -eu\nwhile (($#)); do\n"
            'if [[ $1 == --output ]]; then printf "corrupted archive" > "$2"; exit 0; fi\n'
            "shift\ndone\nexit 2\n"
        )
        fakecurl.chmod(0o755)
        cold_env["PATH"] = str(fakebin) + os.pathsep + env["PATH"]
        output = run("./tools/bootstrap.sh", env=cold_env, success=False)
        assert "checksum did not match" in output, output
        assert not list((temp / "cold-cache").glob("*/jdk"))
        assert not list((temp / "cold-cache").glob("*/install.lock"))
        evidence.append(
            "Cold offline launch rejects; corrupted Java download fails checksum and leaves no runtime or stale lock."
        )
        # Use punctuation meaningful to shell/Desktop Exec parsers, as well as spaces.
        prefix = temp / "prefix with spaces $cash %percent `literal`"
        output = run("./install.sh", "--prefix", str(prefix), env=env)
        assert "Installed Emberwood Terminal." in output, output
        command = prefix / "bin/emberwood-tui"
        assert command.stat().st_mode & 0o111
        version = run(str(command), "--offline", "--version", env=env)
        assert "Emberwood" in version and "1.0.0" in version, version
        evidence.append(
            "Fresh install to a path with spaces, $, %, and backticks; installed executable runs --offline --version."
        )
        terminal_probe(command, env, toggle=True)
        settings = (
            temp / "config/emberwood-tui/settings.properties"
            if platform.system() == "Linux"
            else temp
            / "java-user/Library/Application Support/Emberwood Terminal/settings.properties"
        )
        assert "muted=true" in settings.read_text()
        saved_settings = settings.read_bytes()
        evidence.append(
            "Installed public game launched through a real PTY; M changed rendered sound state, saved preferences, and Q exited cleanly."
        )
        data = prefix / "share/emberwood-tui"
        assert (data / "build/libs/emberwood-tui.jar").stat().st_size > 10000
        if platform.system() == "Linux":
            desktop = prefix / "share/applications/emberwood-terminal.desktop"
            text = desktop.read_text()
            assert "Terminal=true" in text and "Name=Emberwood Terminal" in text
            assert "--offline --desktop" in text
            assert (
                prefix / "share/icons/hicolor/512x512/apps/emberwood-terminal.png"
            ).stat().st_size > 1000
            if shutil.which("desktop-file-validate"):
                run("desktop-file-validate", str(desktop))
                evidence.append(
                    "Linux desktop entry validates; original PNG icon installed; Terminal=true."
                )
            else:
                evidence.append(
                    "Linux desktop fields/icon inspected; desktop-file-validate unavailable."
                )
            if shutil.which("gio"):
                # Exercise the desktop parser and actual launch command without opening a GUI.
                # Terminal=true is separately required above; this probe runs --version in this terminal.
                probe = temp / "launch-probe.desktop"
                probe.write_text(
                    "\n".join(
                        line + " --version"
                        if line.startswith("Exec=")
                        else "Terminal=false"
                        if line == "Terminal=true"
                        else line
                        for line in text.splitlines()
                    )
                    + "\n"
                )
                result = run("gio", "launch", str(probe), env=env)
                assert "Emberwood Terminal 1.0.0" in result, result
                evidence.append(
                    "GIO parsed the generated Exec value and launched the installed Java entry point despite shell punctuation in its path."
                )
        else:
            app = prefix / "Applications/Emberwood Terminal.app"
            run("plutil", "-lint", str(app / "Contents/Info.plist"))
            assert (app / "Contents/Resources/emberwood.icns").stat().st_size > 1000
            run(
                "bash",
                "-n",
                str(app / "Contents/MacOS/launch"),
                str(app / "Contents/Resources/Play.command"),
            )
            evidence.append(
                "macOS Info.plist validates; icon and executable Terminal wrapper exist (GUI launch not tested by this script)."
            )
        marker = data / "obsolete-test-file"
        marker.write_text("Old installation must be cleanly replaced.")
        run("./install.sh", "--prefix", str(prefix), env=env)
        assert not marker.exists()
        run(str(command), "--offline", "--version", env=env)
        assert not (prefix / "share/.emberwood-tui.previous").exists()
        assert not (prefix / "share/.emberwood-tui-install.lock").exists()
        evidence.append(
            "Repeat installation replaces obsolete files, preserves runnable command, removes backup and lock."
        )
        assert settings.read_bytes() == saved_settings
        terminal_probe(command, env, toggle=False)
        evidence.append(
            "A real saved mute preference survives reinstall and is loaded by the installed game on its next PTY launch."
        )
        jar = data / "build/libs/emberwood-tui.jar"
        previous = jar.read_bytes()
        run("./install.sh", "--prefix", str(prefix), env=bad_env, success=False)
        assert jar.read_bytes() == previous
        evidence.append(
            "Failure to provision Java leaves the previous game byte-for-byte intact."
        )
    destination = ROOT / "build/installer-evidence.json"
    destination.parent.mkdir(exist_ok=True)
    destination.write_text(
        json.dumps({"platform": platform.platform(), "passed": evidence}, indent=2)
        + "\n"
    )
    print("\n".join(f"PASS {item}" for item in evidence))
    print(f"Evidence: {destination}")


if __name__ == "__main__":
    main()
