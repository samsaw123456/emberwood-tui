#!/usr/bin/env bash
# Prints a validated JDK home. All progress/error messages go to stderr.
set -euo pipefail
script_dir=${BASH_SOURCE[0]%/*}
[[ $script_dir != "${BASH_SOURCE[0]}" ]] || script_dir=.
root=$(CDPATH="" cd -- "$script_dir/.." && pwd)
fail() { printf '%s\n' "Emberwood Terminal: $*" >&2; exit 2; }
if [[ ${1:-} == --offline ]]; then "$root/install.sh" --dependencies-only --offline >&2
else "$root/install.sh" --dependencies-only >&2; fi
valid_jdk() {
    [[ -x "$1/bin/java" && -x "$1/bin/javac" && -x "$1/bin/jlink" ]] || return 1
    local version
    version=$("$1/bin/java" -version 2>&1) || return 1
    [[ $version =~ version[[:space:]]+\"25[.\"] ]] || return 1
    local modules
    modules=$("$1/bin/java" --list-modules) || return 1
    [[ $modules == *java.desktop@* ]]
}
if [[ -n "${EMBERWOOD_TUI_JAVA_HOME:-}" ]]; then
    valid_jdk "$EMBERWOOD_TUI_JAVA_HOME" || fail 'EMBERWOOD_TUI_JAVA_HOME must point to a complete JDK 25 with desktop modules.'
    printf '%s\n' "$EMBERWOOD_TUI_JAVA_HOME"
    exit 0
fi
case $(uname -s) in Linux) os=linux ;; Darwin) os=mac ;; *) fail 'This launcher supports Linux and macOS.' ;; esac
case $(uname -m) in x86_64|amd64) arch=x64 ;; arm64|aarch64) arch=aarch64 ;; *) fail 'This processor is unsupported. Use x86-64 or ARM64.' ;; esac
cache=${EMBERWOOD_TUI_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/emberwood-tui}
cache=$cache/temurin-25.0.5+8-$os-$arch
jdk=$cache/jdk
if valid_jdk "$jdk"; then printf '%s\n' "$jdk"; exit 0; fi
[[ ${1:-} != --offline ]] || fail 'The private JDK is not cached. Run once online, or use a bundled app download.'
command -v tar >/dev/null || fail 'The system tar utility is required to unpack Java.'
if command -v sha256sum >/dev/null; then hash_cmd=sha256sum
elif command -v shasum >/dev/null; then hash_cmd=shasum
else fail 'A SHA-256 utility is required (sha256sum or shasum).'; fi
row=$(awk -F '\t' -v os="$os" -v arch="$arch" '$1==os && $2==arch {print $3 " " $4}' "$root/tools/jdk-downloads.tsv")
[[ -n "$row" ]] || fail 'The verified Java download is unavailable for this platform.'
expected=${row%% *}; url=${row#* }
mkdir -p -- "$cache" || fail "Cannot write to $cache. Set EMBERWOOD_TUI_CACHE_DIR to a writable directory."
lock=$cache/install.lock
attempt=0
while ! mkdir "$lock" 2>/dev/null; do
    if [[ -r "$lock/pid" ]]; then
        owner=$(cat "$lock/pid")
        if [[ $owner =~ ^[0-9]+$ ]] && ! kill -0 "$owner" 2>/dev/null; then rm -rf -- "$lock"; continue; fi
    elif [[ -n $(find "$lock" -maxdepth 0 -mmin +1 -print 2>/dev/null) ]]; then
        rm -rf -- "$lock"; continue
    fi
    ((attempt+=1))
    ((attempt < 900)) || fail 'Another Java preparation is still running. Try again when it finishes.'
    sleep 1
done
printf '%s\n' "$$" > "$lock/pid"
work=""
cleanup() { [[ -z "$work" ]] || rm -rf -- "$work"; rm -rf -- "$lock"; }
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP
if valid_jdk "$jdk"; then printf '%s\n' "$jdk"; exit 0; fi
work=$(mktemp -d "$cache/download.XXXXXX")
printf '%s\n' 'Preparing private Java 25 (first launch only). Your system Java is unchanged.' >&2
if command -v curl >/dev/null; then
    curl --fail --location --retry 3 --connect-timeout 20 --max-time 900 --output "$work/jdk.tar.gz" "$url" >&2 || fail 'Java download failed. Check your connection and try again.'
elif command -v wget >/dev/null; then
    wget --tries=3 --timeout=60 -O "$work/jdk.tar.gz" "$url" >&2 || fail 'Java download failed. Check your connection and try again.'
else fail 'Install curl or wget with your operating system package manager, or use the bundled app download.'; fi
if [[ $hash_cmd == sha256sum ]]; then actual=$(sha256sum < "$work/jdk.tar.gz"); else actual=$(shasum -a 256 < "$work/jdk.tar.gz"); fi
[[ ${actual%% *} == "$expected" ]] || fail 'Java checksum did not match. Nothing was installed; retry the download.'
mkdir "$work/unpacked"
# Trusted, pinned vendor archive; extraction occurs in a fresh private directory.
tar --no-same-owner -xzf "$work/jdk.tar.gz" -C "$work/unpacked" || fail 'Java archive could not be unpacked.'
source_home=$(find "$work/unpacked" -type f -path '*/bin/javac' -print | head -n 1)
[[ -n "$source_home" ]] || fail 'The Java archive contains no compiler.'
source_home=${source_home%/bin/javac}
valid_jdk "$source_home" || fail 'Downloaded Java could not run on this system.'
# Concurrent launchers never expose a partially extracted JDK.
if valid_jdk "$jdk"; then :
else
    if [[ -e "$jdk" ]]; then mv -- "$jdk" "$cache/incomplete-$(date +%s)-$$"; fi
    mv -- "$source_home" "$jdk"
fi
printf '%s\n' "$jdk"
