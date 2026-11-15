#!/usr/bin/env bash
# Per-user install. Also works as: curl -fsSL <release>/install.sh | bash
set -euo pipefail
version=1.0.0
repository=samsaw123456/emberwood-tui
prefix=${HOME}/.local
custom_prefix=false
fail() { printf '%s\n' "Emberwood Terminal: $*" >&2; exit 2; }
usage() {
    printf '%s\n' 'Usage: ./install.sh [--prefix PATH]' \
        'Installs the game, private Java 25, command, and application-menu launcher for this user.' \
        'No administrator access or global Java changes. Default prefix: ~/.local.'
}
while (($#)); do
    case $1 in
        --prefix) (($# >= 2)) || fail '--prefix needs a directory.'; prefix=$2; custom_prefix=true; shift 2 ;;
        --help|-h) usage; exit 0 ;;
        *) fail "Unknown option: $1" ;;
    esac
done
case $prefix in /*) ;; *) fail '--prefix must be an absolute path.' ;; esac
[[ $prefix != / && $prefix != *$'\n'* && $prefix != *$'\r'* ]] || fail 'Choose a normal user-owned installation directory.'
case $(uname -s) in Linux) platform=linux ;; Darwin) platform=mac ;; *) fail 'Supported systems: Linux and macOS.' ;; esac
case $(uname -m) in x86_64|amd64|arm64|aarch64) ;; *) fail 'Supported processors: x86-64 and ARM64.' ;; esac
for utility in tar awk mktemp; do command -v "$utility" >/dev/null || fail "Missing system utility: $utility"; done
if command -v sha256sum >/dev/null; then hash_cmd=sha256sum
elif command -v shasum >/dev/null; then hash_cmd=shasum
else fail 'Install a SHA-256 utility (sha256sum or shasum).'; fi
download() {
    if command -v curl >/dev/null; then curl --fail --location --retry 3 --connect-timeout 20 --max-time 900 --output "$2" "$1"
    elif command -v wget >/dev/null; then wget --tries=3 --timeout=60 -O "$2" "$1"
    else fail 'Install curl or wget with your operating system package manager, then rerun this installer.'; fi
}
checksum() {
    # Hash stdin: filename escaping must never become part of the parsed digest.
    if [[ $hash_cmd == sha256sum ]]; then sha256sum < "$1" | awk '{print $1}'; else shasum -a 256 < "$1" | awk '{print $1}'; fi
}
work=$(mktemp -d "${TMPDIR:-/tmp}/emberwood-install.XXXXXX")
staging=''
lock=''
cleanup() { rm -rf -- "$work"; [[ -z "$staging" ]] || rm -rf -- "$staging"; [[ -z "$lock" ]] || rmdir -- "$lock" 2>/dev/null || true; }
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
source_dir=''
if [[ -n ${BASH_SOURCE[0]:-} && -f ${BASH_SOURCE[0]} ]]; then
    candidate=$(CDPATH="" cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
    if [[ -f "$candidate/tools/jdk-downloads.tsv" && -f "$candidate/build.gradle.kts" ]]; then source_dir=$candidate; fi
fi
if [[ -z "$source_dir" ]]; then
    asset=emberwood-tui-$version.tar.gz
    release=https://github.com/$repository/releases/download/v$version
    printf '%s\n' "Downloading Emberwood Terminal $version…"
    download "$release/$asset" "$work/$asset"
    download "$release/SHA256SUMS" "$work/SHA256SUMS"
    expected=$(awk -v name="$asset" '$2==name {print $1}' "$work/SHA256SUMS")
    [[ $expected =~ ^[[:xdigit:]]{64}$ ]] || fail 'Release checksum is missing or malformed.'
    [[ $(checksum "$work/$asset") == "$expected" ]] || fail 'Release checksum mismatch; nothing installed.'
    # Publisher-controlled, checksum-verified archive extracted into a fresh directory.
    tar -xzf "$work/$asset" -C "$work"
    source_dir=$work/emberwood-tui-$version
    [[ -x "$source_dir/emberwood-tui" && -f "$source_dir/build.gradle.kts" ]] || fail 'Incomplete release archive.'
fi
if [[ ! -f "$source_dir/.release-build" ]]; then
    # Rebuild changed files before copying, so a stale local JAR is never installed.
    "$source_dir/emberwood-tui" --version
fi
mkdir -p -- "$prefix/share" "$prefix/bin"
install_lock=$prefix/share/.emberwood-tui-install.lock
mkdir -- "$install_lock" 2>/dev/null || fail "Another installation is active ($install_lock). If it was interrupted, remove that empty directory and retry."
lock=$install_lock
target=$prefix/share/emberwood-tui
staging=$(mktemp -d "$prefix/share/.emberwood-tui.XXXXXX")
# Keep a clean portable application, excluding user checkouts, build caches, and audit output.
for entry in emberwood-tui install.sh VERSION LICENSE THIRD_PARTY.md README.md .editorconfig build.gradle.kts settings.gradle.kts gradle.properties gradlew gradlew.bat gradle src tools packaging docs; do
    [[ ! -e "$source_dir/$entry" ]] || cp -R -- "$source_dir/$entry" "$staging/$entry"
done
find "$staging/tools" -type d -name __pycache__ -prune -exec rm -rf -- {} +
if [[ -f "$source_dir/build/libs/emberwood-tui.jar" ]]; then
    mkdir -p "$staging/build/libs"
    cp -- "$source_dir/build/libs/emberwood-tui.jar" "$staging/build/libs/emberwood-tui.jar"
    touch "$staging/.release-build"
fi
[[ -f "$staging/tools/bootstrap.sh" ]] || fail 'Java bootstrap is missing from the distribution.'
chmod +x "$staging/emberwood-tui" "$staging/install.sh" "$staging/gradlew" "$staging/tools/bootstrap.sh"
# Prepare and validate before replacing an existing installation.
"$staging/emberwood-tui" --version
backup=$prefix/share/.emberwood-tui.previous
[[ ! -e "$backup" ]] || fail "A previous update backup exists at $backup; move it aside before installing."
if [[ -e "$target" ]]; then mv -- "$target" "$backup"; fi
if ! mv -- "$staging" "$target"; then
    [[ ! -e "$backup" ]] || mv -- "$backup" "$target"
    fail 'Could not put the prepared application in place.'
fi
staging=''
printf '#!/usr/bin/env bash\nexec %q "$@"\n' "$target/emberwood-tui" > "$work/command"
chmod +x "$work/command"
mv -- "$work/command" "$prefix/bin/emberwood-tui"
if [[ $platform == linux ]]; then
    apps=$prefix/share/applications
    icons=$prefix/share/icons/hicolor/512x512/apps
    mkdir -p -- "$apps" "$icons"
    cp -- "$target/packaging/emberwood-terminal.png" "$icons/emberwood-terminal.png"
    # Desktop strings are unescaped before Exec parsing: two layers of quoting.
    executable=''
    command_path=$prefix/bin/emberwood-tui
    # shellcheck disable=SC1003 # These are literal backslashes for Desktop Entry escaping.
    for ((character=0; character<${#command_path}; character++)); do
        case ${command_path:character:1} in
            '\') executable+='\\\\' ;;
            '"') executable+='\\"' ;;
            '$') executable+='\\$' ;;
            '`') executable+='\\`' ;;
            '%') executable+='%%' ;;
            *) executable+=${command_path:character:1} ;;
        esac
    done
    # Keep the executable itself fixed: desktop loaders inspect it before expanding %%.
    printf '%s\n' '[Desktop Entry]' 'Type=Application' 'Version=1.0' 'Name=Emberwood Terminal' \
        'Comment=Twenty forest adventures, inside your terminal' "Exec=/usr/bin/env bash \"$executable\" --offline --desktop" \
        'Icon=emberwood-terminal' 'Terminal=true' 'Categories=Game;AdventureGame;' 'StartupNotify=false' \
        > "$apps/emberwood-terminal.desktop"
    chmod 644 "$apps/emberwood-terminal.desktop"
    if command -v update-desktop-database >/dev/null; then update-desktop-database "$apps" || printf '%s\n' 'Menu cache refresh unavailable; log in again if the icon does not appear.' >&2; fi
    if command -v gtk-update-icon-cache >/dev/null; then gtk-update-icon-cache -f -t "$prefix/share/icons/hicolor" >/dev/null 2>&1 || printf '%s\n' 'Icon cache refresh unavailable; the icon file was installed.' >&2; fi
else
    apps=$HOME/Applications
    if $custom_prefix; then apps=$prefix/Applications; fi
    bundle=$apps/Emberwood\ Terminal.app
    mkdir -p "$bundle/Contents/MacOS" "$bundle/Contents/Resources"
    printf '%s\n' '<?xml version="1.0" encoding="UTF-8"?>' '<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">' \
        '<plist version="1.0"><dict>' '<key>CFBundleName</key><string>Emberwood Terminal</string>' \
        '<key>CFBundleIdentifier</key><string>org.emberwood.terminal</string>' \
        '<key>CFBundleExecutable</key><string>launch</string>' '<key>CFBundleIconFile</key><string>emberwood.icns</string>' \
        "<key>CFBundleShortVersionString</key><string>$version</string>" '<key>CFBundlePackageType</key><string>APPL</string>' \
        '</dict></plist>' > "$bundle/Contents/Info.plist"
    printf '#!/bin/bash\nexec %q --offline --desktop\n' "$target/emberwood-tui" > "$bundle/Contents/Resources/Play.command"
    cat > "$bundle/Contents/MacOS/launch" <<'MAC_LAUNCH'
#!/bin/bash
set -euo pipefail
contents=$(CDPATH="" cd -- "$(dirname -- "$0")/.." && pwd)
exec /usr/bin/open -a Terminal "$contents/Resources/Play.command"
MAC_LAUNCH
    chmod +x "$bundle/Contents/MacOS/launch" "$bundle/Contents/Resources/Play.command"
    iconset=$work/emberwood.iconset
    mkdir "$iconset"
    for size in 16 32 128 256 512; do
        sips -z "$size" "$size" "$target/packaging/emberwood-terminal.png" --out "$iconset/icon_${size}x${size}.png" >/dev/null
        if ((size < 512)); then sips -z "$((size*2))" "$((size*2))" "$target/packaging/emberwood-terminal.png" --out "$iconset/icon_${size}x${size}@2x.png" >/dev/null; fi
    done
    iconutil -c icns "$iconset" -o "$bundle/Contents/Resources/emberwood.icns"
    /System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister -f "$bundle" || printf '%s\n' 'Launch Services refresh unavailable; open the installed app in Applications directly.' >&2
fi
[[ ! -e "$backup" ]] || rm -rf -- "$backup"
printf '\n%s\n' 'Installed Emberwood Terminal.' "Play: $prefix/bin/emberwood-tui" 'Or open Emberwood Terminal from your application menu.'
# shellcheck disable=SC2016 # The instruction must contain the user's future $PATH.
case :$PATH: in *:"$prefix/bin":*) ;; *) printf 'To use the short command in new terminals, add this to your shell profile:\n  export PATH=%q:"$PATH"\n' "$prefix/bin" ;; esac
