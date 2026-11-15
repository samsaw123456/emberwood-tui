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
import shlex
import shutil
import signal
import struct
import subprocess
import tarfile
import tempfile
import termios
import time
from collections.abc import Callable
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = (ROOT / "VERSION").read_text().strip()


def terminal_probe(
    command: Path,
    env: dict[str, str],
    toggle: bool,
    while_open: Callable[[], None] | None = None,
) -> str:
    """Run the installed public CLI through its actual terminal, with isolated preferences."""
    # Keep the slave open in the parent so post-exit tcgetattr also works on
    # macOS. Capture before forking: the child must not race this baseline.
    descriptor, slave = pty.openpty()
    fcntl.ioctl(slave, termios.TIOCSWINSZ, struct.pack("HHHH", 40, 120, 0, 0))
    before = termios.tcgetattr(slave)
    child = os.fork()
    if child == 0:
        os.close(descriptor)
        os.setsid()
        fcntl.ioctl(slave, termios.TIOCSCTTY, 0)
        for target in (0, 1, 2):
            os.dup2(slave, target)
        if slave > 2:
            os.close(slave)
        os.execve(str(command), [str(command), "--offline"], env)
    output = ""
    reaped = False
    try:
        deadline = time.monotonic() + 20
        phase = "title"
        sound_key_at = 0.0
        while time.monotonic() < deadline:
            readable, _, _ = select.select([descriptor], [], [], 0.1)
            if readable:
                fragment = os.read(descriptor, 131072).decode("utf-8", errors="replace")
                output += fragment
                if "\x1b[6n" in fragment:
                    os.write(descriptor, b"\x1b[1;1R")
            if (
                phase == "title"
                and "Enter:" in output
                and (toggle or "sound: off" in output)
            ):
                if while_open:
                    while_open()
                output = ""
                os.write(descriptor, b"\x1a")
                sound_key_at = time.monotonic() + 0.25
                phase = "suspend"
            elif phase == "suspend" and time.monotonic() >= sound_key_at:
                output = ""
                os.write(descriptor, b"m")
                phase = "mute" if toggle else "unmute"
            elif phase == "unmute" and any(
                state in output for state in ("sound: on", "sound: unavailable")
            ):
                # No audio device is required for the visible input-recovery
                # check. Restore the saved preference before leaving the app.
                output = ""
                os.write(descriptor, b"m")
                phase = "mute"
            elif phase == "mute" and "sound: off" in output:
                os.write(descriptor, b"q")
                break
        else:
            raise AssertionError(
                f"Installed terminal did not recover after Ctrl+Z ({phase}): {output[-2000:]}"
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
                after = termios.tcgetattr(slave)
                assert after == before, (
                    f"Terminal settings changed: {before!r} -> {after!r}"
                )
                return output
            time.sleep(0.05)
        raise AssertionError("Installed terminal did not exit after Q.")
    finally:
        os.close(descriptor)
        os.close(slave)
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
    shutil.copy2(ROOT / "install.sh", source / "install.sh")
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
    shutil.copy2(ROOT / "install.sh", fixture / "install.sh")
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


def dependency_preflight(temp: Path) -> list[str]:
    """Exercise real detection with an isolated PATH; never run a host package manager."""
    utilities = [
        "bash",
        "uname",
        "dirname",
        "mktemp",
        "mkdir",
        "rmdir",
        "cp",
        "mv",
        "rm",
        "chmod",
        "touch",
        "cat",
        "head",
        "date",
        "sleep",
        "tr",
        "ls",
        "find",
        "xargs",
        "sed",
        "tar",
        "gzip",
        "awk",
        "sha256sum",
        "shasum",
        "curl",
        "wget",
        "fc-match",
        "sips",
        "iconutil",
        "open",
    ]
    commands = {name: shutil.which(name) for name in utilities}
    fixture = temp / "dependency-checks"
    fixture.mkdir()
    standalone = fixture / "install.sh"
    shutil.copy2(ROOT / "install.sh", standalone)

    def environment(name: str, absent: set[str]) -> tuple[Path, Path, dict[str, str]]:
        directory = fixture / name
        directory.mkdir()
        for utility, executable in commands.items():
            if executable and utility not in absent:
                (directory / utility).symlink_to(executable)
        log = directory / "package-calls"
        env = os.environ | {"PATH": str(directory)}
        env.pop("DISPLAY", None)
        env.pop("WAYLAND_DISPLAY", None)
        return directory, log, env

    if platform.system() == "Darwin":
        _, _, env = environment("missing-mac-icon-tool", {"iconutil"})
        prefix = fixture / "unchanged-mac-install"
        output = run(str(standalone), "--prefix", str(prefix), env=env, success=False)
        assert "Missing macOS system tools: iconutil" in output, output
        assert not prefix.exists()
        return [
            "Missing macOS iconutil is diagnosed before installation; no Homebrew or game files are touched."
        ]

    absent = {
        "mktemp",
        "find",
        "xargs",
        "sed",
        "tar",
        "gzip",
        "awk",
        "sha256sum",
        "shasum",
        "curl",
        "wget",
        "fc-match",
    }
    link = shlex.quote(shutil.which("ln") or "/bin/ln")

    def package_manager(directory: Path, log: Path, manager: str, mode: str = "repair"):
        repair = "\n".join(
            f"{link} -sf {shlex.quote(executable)} {shlex.quote(str(directory / utility))}"
            for utility, executable in commands.items()
            if executable and utility in absent
        )
        executable = directory / manager
        executable.write_text(
            "#!/bin/bash\nset -eu\n"
            f"printf '%s\\n' \"$*\" >> {shlex.quote(str(log))}\n"
            "[[ ${1:-} != update ]] || exit 0\n"
            + (
                "exit 42\n"
                if mode == "fail"
                else "exit 0\n"
                if mode == "ineffective"
                else (
                    "[[ ${DEBIAN_FRONTEND:-} == noninteractive ]] || exit 88\n"
                    if manager == "apt-get"
                    else ""
                )
                + repair
                + "\n"
            )
        )
        executable.chmod(0o755)
        sudo = directory / "sudo"
        sudo.write_text(
            '#!/bin/bash\nwhile [[ ${1:-} == *=* ]]; do export "$1"; shift; done\nexec "$@"\n'
        )
        sudo.chmod(0o755)

    font_packages = {
        "apt-get": "fonts-dejavu-core",
        "dnf": "dejavu-sans-mono-fonts",
        "pacman": "ttf-dejavu",
        "zypper": "dejavu-fonts",
    }
    for manager, fonts in font_packages.items():
        directory, log, env = environment(manager, absent)
        package_manager(directory, log, manager)
        run(str(standalone), "--dependencies-only", env=env)
        calls = log.read_text().splitlines()
        install = shlex.split(calls[-1])
        for package in (
            "coreutils",
            "findutils",
            "sed",
            "tar",
            "gzip",
            "gawk",
            "curl",
            "fontconfig",
            fonts,
        ):
            assert install.count(package) == 1, calls
        assert "unzip" not in install, calls
        if manager == "pacman":
            assert install[:3] == ["-S", "--needed", "--noconfirm"], calls
            assert all("-Sy" not in call and "-Su" not in call for call in calls)
        run(str(standalone), "--dependencies-only", env=env)
        assert log.read_text().splitlines() == calls, (
            "Prepared launch repeated package changes"
        )

    _, log, env = environment(
        "offline", {"tar", "gzip", "awk", "sha256sum", "shasum", "curl", "wget"}
    )
    run(str(standalone), "--dependencies-only", "--offline", env=env)
    assert not log.exists()
    directory, log, env = environment("offline-missing-runtime", {"fc-match"})
    package_manager(directory, log, "apt-get")
    output = run(
        str(standalone), "--dependencies-only", "--offline", env=env, success=False
    )
    assert "Offline play is missing" in output and not log.exists(), output

    for mode in ("fail", "ineffective"):
        directory, log, env = environment(mode, {"curl", "wget"})
        package_manager(directory, log, "apt-get", mode=mode)
        prefix = fixture / f"existing-{mode}"
        prefix.mkdir()
        previous = prefix / "previous-game"
        previous.write_text("unchanged")
        output = run(str(standalone), "--prefix", str(prefix), env=env, success=False)
        assert (
            "failed" in output if mode == "fail" else "still unavailable" in output
        ), output
        assert previous.read_text() == "unchanged"
        assert list(prefix.iterdir()) == [previous]

    directory, log, env = environment("no-manager", {"curl", "wget"})
    output = run(str(standalone), "--dependencies-only", env=env, success=False)
    assert "No supported package manager" in output and not log.exists(), output
    if os.geteuid() != 0:
        package_manager(directory, log, "apt-get")
        (directory / "sudo").unlink()
        output = run(str(standalone), "--dependencies-only", env=env, success=False)
        assert "requires sudo or an administrator" in output and not log.exists(), (
            output
        )

    directory, log, env = environment("desktop-terminal", set())
    kitty = directory / "kitty"
    kitty.write_text("#!/bin/bash\nexit 0\n")
    kitty.chmod(0o755)
    curl = directory / "curl"
    if curl.exists():
        curl.unlink()
    curl.write_text("#!/bin/bash\nprintf 'fixture download stopped\\n' >&2\nexit 23\n")
    curl.chmod(0o755)
    prefix = fixture / "desktop-prefix"
    output = run(str(standalone), "--prefix", str(prefix), env=env, success=False)
    assert "fixture download stopped" in output and not log.exists(), output
    package_manager(directory, log, "apt-get", mode="ineffective")
    manager = directory / "apt-get"
    # This fixture supplies only a terminal; no graphical application is opened.
    manager.write_text(
        manager.read_text().removesuffix("exit 0\n")
        + f"printf '#!/bin/bash\\nexit 0\\n' > {shlex.quote(str(directory / 'xterm'))}\n"
        + f"{shlex.quote(commands['chmod'])} +x {shlex.quote(str(directory / 'xterm'))}\n"
    )
    output = run(
        str(standalone),
        "--prefix",
        str(prefix),
        env=env | {"DISPLAY": ":fixture"},
        success=False,
    )
    assert "fixture download stopped" in output and not prefix.exists(), output
    assert shlex.split(log.read_text().splitlines()[-1])[-1] == "xterm"

    evidence = [
        "Missing tools/font support trigger the expected apt, dnf, pacman and zypper package commands exactly once; a repeated preflight changes nothing (isolated PATH and package-manager fixtures).",
        "Offline preflight succeeds without download/hash/archive tools and never invokes a package manager, including when runtime prerequisites are missing.",
        "Failed or ineffective dependency installation leaves existing game files untouched; missing package manager/elevation produces a precise failure.",
        "A graphical install with only an unregistered kitty terminal requests xterm; a headless install does not request graphical packages (isolated fixtures).",
    ]
    gio = shutil.which("gio")
    if gio:
        probe = fixture / "gio-terminal"
        probe.mkdir()
        desktop = probe / "harmless.desktop"
        desktop.write_text(
            "[Desktop Entry]\nType=Application\nName=Emberwood terminal probe\n"
            "Exec=/bin/true\nTerminal=true\n"
        )
        marker = probe / "called"
        terminal_script = (
            '#!/bin/bash\nprintf "%s\\n" "$@" > '
            + shlex.quote(str(marker))
            + "\nexit 0\n"
        )
        (probe / "kitty").write_text(terminal_script)
        (probe / "kitty").chmod(0o755)
        gio_env = os.environ | {
            "PATH": str(probe),
            "DISPLAY": "",
            "WAYLAND_DISPLAY": "",
            "DBUS_SESSION_BUS_ADDRESS": "unix:path=" + str(probe / "no-session"),
        }
        run(gio, "launch", str(desktop), env=gio_env, success=False)
        assert not marker.exists(), "GIO unexpectedly dispatched an unregistered kitty"
        (probe / "xterm").write_text(terminal_script)
        (probe / "xterm").chmod(0o755)
        run(gio, "launch", str(desktop), env=gio_env)
        deadline = time.monotonic() + 3
        while not marker.exists() and time.monotonic() < deadline:
            time.sleep(0.02)
        assert marker.read_text().splitlines() == ["-e", "/bin/true"]
        evidence.append(
            "Actual GIO Terminal=true lookup fails in a kitty-only PATH and dispatches the supplied xterm fallback with the expected harmless command; no desktop preferences change."
        )
    return evidence


def interrupted_install(temp: Path, prefix: Path, env: dict[str, str]) -> None:
    """Terminate during staging and verify that an existing installation survives."""
    fixture = temp / "interrupted-install"
    fixture.mkdir()
    marker = fixture / "copy-started"
    real_cp = shutil.which("cp")
    assert real_cp
    copy = fixture / "cp"
    copy.write_text(
        "#!/bin/bash\nset -eu\n"
        f"printf ready > {shlex.quote(str(marker))}\n"
        "sleep 30\n"
        f'exec {shlex.quote(real_cp)} "$@"\n'
    )
    copy.chmod(0o755)
    jar = prefix / "share/emberwood-tui/build/libs/emberwood-tui.jar"
    before = hashlib.sha256(jar.read_bytes()).hexdigest()
    process = subprocess.Popen(
        [str(ROOT / "install.sh"), "--prefix", str(prefix)],
        env=env | {"PATH": str(fixture) + os.pathsep + env["PATH"]},
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        start_new_session=True,
    )
    try:
        deadline = time.monotonic() + 20
        while (
            not marker.exists()
            and process.poll() is None
            and time.monotonic() < deadline
        ):
            time.sleep(0.05)
        assert marker.exists(), "Installer did not reach the staging copy"
        os.killpg(process.pid, signal.SIGTERM)
        output, _ = process.communicate(timeout=10)
        assert process.returncode in (143, -signal.SIGTERM), output
        assert hashlib.sha256(jar.read_bytes()).hexdigest() == before
        assert not (prefix / "share/.emberwood-tui-install.lock").exists()
        assert not list((prefix / "share").glob(".emberwood-tui.*"))
    finally:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait(timeout=5)


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
        ("--offline",),
        ("--dependencies-only", "--prefix", "/tmp/unused"),
    ):
        run("./install.sh", *args, success=False)
    run("./emberwood-tui", "--build", "unexpected", success=False)
    evidence.append(
        "Missing, relative and root prefixes, unknown options, invalid build arguments reject safely."
    )
    with tempfile.TemporaryDirectory(prefix="emberwood installer test ") as directory:
        temp = Path(directory)
        evidence.extend(dependency_preflight(temp))
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
        assert f"Emberwood Terminal {VERSION}" in version, version
        help_text = run(str(command), "--help", env=env)
        assert "Up/Down or Tab choose equipment while paused." in help_text
        assert "W/S choose equipment" not in help_text
        evidence.append(
            "Fresh install to a path with spaces, $, %, and backticks; installed executable runs --offline --version."
        )
        terminal_probe(
            command,
            env,
            toggle=True,
            while_open=lambda: run("./install.sh", "--prefix", str(prefix), env=env),
        )
        settings = (
            temp / "config/emberwood-tui/settings.properties"
            if platform.system() == "Linux"
            else temp
            / "java-user/Library/Application Support/Emberwood Terminal/settings.properties"
        )
        assert "muted=true" in settings.read_text()
        saved_settings = settings.read_bytes()
        evidence.append(
            "Installed public game remained open in a real PTY during reinstall; afterward Ctrl+Z preserved input, M changed rendered sound state, and Q restored every termios flag/speed/control byte exactly."
        )
        data = prefix / "share/emberwood-tui"
        assert (data / "build/libs/emberwood-tui.jar").stat().st_size > 10000
        notices = json.loads((ROOT / "packaging/licenses/sources.json").read_text())
        for notice in notices:
            installed = data / "packaging/licenses" / notice["file"]
            assert (
                hashlib.sha256(installed.read_bytes()).hexdigest() == notice["sha256"]
            )
        for filename in (".gitattributes", "AGENTS.md"):
            assert (data / filename).read_bytes() == (ROOT / filename).read_bytes()
        evidence.append(
            f"All {len(notices)} runtime license/notice files, .gitattributes and AGENTS.md install byte-for-byte unchanged."
        )
        tasks = json.loads((data / ".zed/tasks.json").read_text())
        assert tasks == json.loads((ROOT / ".zed/tasks.json").read_text())
        assert len(tasks) == 3
        assert {task["label"] for task in tasks} == {
            "Play Emberwood Terminal",
            "Build Emberwood Terminal",
            "Check Emberwood Terminal",
        }
        play = next(
            task for task in tasks if task["label"] == "Play Emberwood Terminal"
        )
        assert play["reveal_target"] == "center"
        evidence.append(
            "All three installed Zed task objects exactly match source; Play retains reveal_target=center. Installed help correctly names Up/Down/Tab equipment navigation."
        )
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
                assert f"Emberwood Terminal {VERSION}" in result, result
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
        interrupted_install(temp, prefix, env)
        evidence.append(
            "SIGTERM during staging removes temporary files and the install lock without changing the previous JAR."
        )
        install_lock = prefix / "share/.emberwood-tui-install.lock"
        install_lock.mkdir()
        (install_lock / "pid").write_text(str(os.getpid()))
        output = run("./install.sh", "--prefix", str(prefix), env=env, success=False)
        assert "Another installation is active" in output and marker.exists(), output
        departed = subprocess.Popen(["/bin/sh", "-c", "exit 0"])
        departed.wait(timeout=5)
        (install_lock / "pid").write_text(str(departed.pid))
        run("./install.sh", "--prefix", str(prefix), env=env)
        assert not marker.exists()
        run(str(command), "--offline", "--version", env=env)
        assert not (prefix / "share/.emberwood-tui.previous").exists()
        assert not (prefix / "share/.emberwood-tui-install.lock").exists()
        evidence.append(
            "A live install lock rejects safely; a stale PID lock recovers automatically. Repeat installation replaces obsolete files, preserves the runnable command, and removes backup/lock."
        )
        assert settings.read_bytes() == saved_settings
        terminal_probe(command, env, toggle=False)
        evidence.append(
            "A real saved mute preference survives reinstall; the next public PTY launch loads it, recovers from Ctrl+Z, visibly toggles M twice, and restores exact terminal settings on Q."
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
