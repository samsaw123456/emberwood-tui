#!/usr/bin/env bash
# Per-user install. Also works as: curl -fsSL <release>/install.sh | bash
set -euo pipefail
version=1.0.1
repository=samsaw123456/emberwood-tui
prefix=${HOME}/.local
custom_prefix=false
dependencies_only=false
offline=false
fail() { printf '%s\n' "Emberwood Terminal: $*" >&2; exit 2; }
usage() {
    printf '%s\n' 'Usage: ./install.sh [--prefix PATH] | --dependencies-only [--offline]' \
        'Installs the game, private Java 25, command, and application-menu launcher for this user.' \
        'Missing Linux system prerequisites are installed with the system package manager.' \
        'Only missing packages need administrator access; system Java is unchanged.' \
        'Default prefix: ~/.local. --dependencies-only prepares prerequisites without installing the game.' \
        '--dependencies-only --offline checks play requirements without downloads or package changes.'
}
while (($#)); do
    case $1 in
        --prefix) (($# >= 2)) || fail '--prefix needs a directory.'; prefix=$2; custom_prefix=true; shift 2 ;;
        --dependencies-only) dependencies_only=true; shift ;;
        --offline) offline=true; shift ;;
        --help|-h) usage; exit 0 ;;
        *) fail "Unknown option: $1" ;;
    esac
done
$offline && ! $dependencies_only && fail '--offline is only supported with --dependencies-only. Installed play uses emberwood-tui --offline.'
$dependencies_only && $custom_prefix && fail '--dependencies-only does not accept --prefix.'
case $prefix in /*) ;; *) fail '--prefix must be an absolute path.' ;; esac
[[ $prefix != / && $prefix != *$'\n'* && $prefix != *$'\r'* ]] || fail 'Choose a normal user-owned installation directory.'
# Bash can identify the host even when coreutils (including uname) is missing.
host_system=${OSTYPE%%[0-9]*}
host_arch=${MACHTYPE%%-*}
if command -v uname >/dev/null; then host_system=$(uname -s); host_arch=$(uname -m); fi
case $host_system in Linux|linux-gnu) platform=linux ;; Darwin|darwin) platform=mac ;; *) fail 'Supported systems: glibc Linux and macOS.' ;; esac
case $host_arch in x86_64|amd64|arm64|aarch64) ;; *) fail 'Supported processors: x86-64 and ARM64.' ;; esac

# One authoritative preflight for the standalone installer and source/runtime
# bootstrap. It uses only Bash until any missing base utilities are prepared.
missing=()
need() {
    local item
    for item in "${missing[@]+${missing[@]}}"; do [[ $item != "$1" ]] || return 0; done
    missing+=("$1")
}
has_library() {
    local path
    for path in /lib/"$1" /lib64/"$1" /lib/*/"$1" /usr/lib/"$1" /usr/lib64/"$1" /usr/lib/*/"$1"; do
        [[ ! -r "$path" ]] || return 0
    done
    return 1
}
has_certificates() {
    local path
    for path in "${CURL_CA_BUNDLE:-}" "${SSL_CERT_FILE:-}" /etc/ssl/certs/ca-certificates.crt \
        /etc/pki/tls/certs/ca-bundle.crt /etc/ssl/ca-bundle.pem /etc/ssl/cert.pem; do
        [[ ! -s "$path" ]] || return 0
    done
    return 1
}
has_terminal() {
    local terminal
    # Terminal=true consumers need a registered dispatcher or a recognized
    # fallback; merely finding an unregistered kitty/foot binary is not enough.
    for terminal in xdg-terminal-exec x-terminal-emulator gnome-terminal kgx konsole \
        xfce4-terminal mate-terminal tilix xterm; do
        ! command -v "$terminal" >/dev/null || return 0
    done
    return 1
}
check_dependencies() {
    local utility
    missing=()
    for utility in uname dirname mktemp mkdir rmdir cp mv rm chmod touch cat head date sleep tr ls; do
        command -v "$utility" >/dev/null || need coreutils
    done
    for utility in find xargs; do command -v "$utility" >/dev/null || need findutils; done
    command -v sed >/dev/null || need sed
    if ! $offline; then
        command -v tar >/dev/null || need tar
        command -v gzip >/dev/null || need gzip
        command -v awk >/dev/null || need gawk
        command -v sha256sum >/dev/null || command -v shasum >/dev/null || need coreutils
        command -v curl >/dev/null || command -v wget >/dev/null || need curl
        [[ $platform != linux ]] || has_certificates || need ca-certificates
    fi
    if [[ $platform == linux ]]; then
        for utility in /lib/ld-musl-*.so.1; do
            [[ ! -e "$utility" ]] || fail 'This Java distribution needs glibc Linux; musl/Alpine is not supported.'
        done
        # Java's headless font manager still needs fontconfig and a font fallback.
        # FreeType is already bundled in the pinned Temurin runtime.
        if ! command -v fc-match >/dev/null || ! fc-match -f '%{file}' monospace >/dev/null 2>&1; then
            need fontconfig
            need fonts
        elif [[ -z $(fc-match -f '%{file}' monospace 2>/dev/null) ]]; then
            need fonts
        fi
        has_library libasound.so.2 || need alsa
        if ! $dependencies_only && [[ -n ${DISPLAY:-}${WAYLAND_DISPLAY:-} ]]; then
            has_terminal || need terminal
        fi
    elif ! $dependencies_only; then
        for utility in sips iconutil open; do
            command -v "$utility" >/dev/null || need "$utility"
        done
        [[ -d /System/Applications/Utilities/Terminal.app || -d /Applications/Utilities/Terminal.app ]] || need Terminal.app
    fi
}
install_dependencies() {
    local manager='' item package
    local packages=() elevated=()
    [[ $platform == linux ]] || fail "Missing macOS system tools: ${missing[*]}. Restore the built-in macOS tools or correct PATH; Homebrew is not needed."
    $offline && fail "Offline play is missing system prerequisites: ${missing[*]}. Run ./install.sh --dependencies-only online first."
    for item in apt-get dnf pacman zypper; do
        if command -v "$item" >/dev/null; then manager=$item; break; fi
    done
    [[ -n "$manager" ]] || fail "Missing prerequisites: ${missing[*]}. No supported package manager found (apt-get, dnf, pacman, zypper)."
    if ((EUID != 0)); then
        command -v sudo >/dev/null || fail "Missing prerequisites: ${missing[*]}. Installing system packages requires sudo or an administrator running this script with --dependencies-only."
        elevated=(sudo)
    fi
    printf '%s\n' "Preparing missing system prerequisites with $manager: ${missing[*]}" >&2
    if [[ $manager == apt-get ]]; then
        "${elevated[@]+${elevated[@]}}" apt-get update </dev/null >&2 || fail 'Could not refresh authenticated package indexes; check your configured repositories and network, then retry.'
    fi
    for item in "${missing[@]}"; do
        package=$item
        case $manager:$item in
            apt-get:alsa)
                if apt-cache show libasound2t64 >/dev/null 2>&1; then package=libasound2t64; else package=libasound2; fi ;;
            dnf:alsa|pacman:alsa) package=alsa-lib ;;
            zypper:alsa) package=libasound2 ;;
            apt-get:fonts) package=fonts-dejavu-core ;;
            dnf:fonts) package=dejavu-sans-mono-fonts ;;
            pacman:fonts) package=ttf-dejavu ;;
            zypper:fonts) package=dejavu-fonts ;;
            *:terminal) package=xterm ;;
        esac
        packages+=("$package")
    done
    case $manager in
        apt-get)
            if ((${#elevated[@]})); then
                sudo DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends "${packages[@]}" </dev/null >&2
            else
                DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends "${packages[@]}" </dev/null >&2
            fi ;;
        dnf) "${elevated[@]+${elevated[@]}}" dnf install -y "${packages[@]}" </dev/null >&2 ;;
        # Use the existing package database. -Sy alone risks a partial upgrade;
        # this installer must not force a whole-system upgrade either.
        pacman) "${elevated[@]+${elevated[@]}}" pacman -S --needed --noconfirm "${packages[@]}" </dev/null >&2 ;;
        zypper) "${elevated[@]+${elevated[@]}}" zypper --non-interactive install --no-recommends "${packages[@]}" </dev/null >&2 ;;
    esac || fail 'System package installation failed. No game files were replaced. Resolve the package-manager error and rerun the same command.'
    hash -r
    check_dependencies
    ((${#missing[@]} == 0)) || fail "The package manager finished but prerequisites are still unavailable: ${missing[*]}. Check PATH and package-manager output."
}
check_dependencies
if ((${#missing[@]})); then install_dependencies; fi
$dependencies_only && exit 0
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
cleanup() {
    rm -rf -- "$work"
    [[ -z "$staging" ]] || rm -rf -- "$staging"
    if [[ -n "$lock" ]]; then rm -f -- "$lock/pid"; rmdir -- "$lock" 2>/dev/null || true; fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP
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
    tar --no-same-owner -xzf "$work/$asset" -C "$work"
    source_dir=$work/emberwood-tui-$version
    [[ -x "$source_dir/emberwood-tui" && -f "$source_dir/build.gradle.kts" ]] || fail 'Incomplete release archive.'
fi
if [[ ! -f "$source_dir/.release-build" ]]; then
    # Rebuild changed files before copying, so a stale local JAR is never installed.
    "$source_dir/emberwood-tui" --version
fi
mkdir -p -- "$prefix/share" "$prefix/bin"
install_lock=$prefix/share/.emberwood-tui-install.lock
if ! mkdir -- "$install_lock" 2>/dev/null; then
    owner=''
    [[ ! -r "$install_lock/pid" ]] || owner=$(cat "$install_lock/pid")
    if [[ $owner =~ ^[0-9]+$ ]] && ! kill -0 "$owner" 2>/dev/null; then
        rm -f -- "$install_lock/pid"
        rmdir -- "$install_lock" 2>/dev/null || fail "Cannot recover interrupted installation lock: $install_lock"
        mkdir -- "$install_lock" 2>/dev/null || fail "Another installation started ($install_lock); retry when it finishes."
    else
        fail "Another installation is active ($install_lock). If an older installer was interrupted, remove its empty lock directory and retry."
    fi
fi
lock=$install_lock
printf '%s\n' "$$" > "$lock/pid"
target=$prefix/share/emberwood-tui
staging=$(mktemp -d "$prefix/share/.emberwood-tui.XXXXXX")
# Keep a clean portable application, excluding user checkouts, build caches, and audit output.
for entry in emberwood-tui install.sh VERSION LICENSE THIRD_PARTY.md README.md AGENTS.md .editorconfig .gitattributes .zed build.gradle.kts settings.gradle.kts gradle.properties gradlew gradlew.bat gradle src tools packaging docs; do
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
