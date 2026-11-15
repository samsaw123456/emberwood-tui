#!/usr/bin/env bash
# Create the portable release used by the one-shot installer.
set -euo pipefail
root=$(CDPATH="" cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd -- "$root"
version=$(cat VERSION)
installer_version=$(awk -F= '$1=="version" {print $2; exit}' install.sh)
[[ "$version" == "$installer_version" ]] || { printf '%s\n' 'VERSION and install.sh disagree; update the installer before packaging.' >&2; exit 2; }
./emberwood-tui --build
mkdir -p dist
work=$(mktemp -d "${TMPDIR:-/tmp}/emberwood-package.XXXXXX")
trap 'rm -rf -- "$work"' EXIT
folder=emberwood-tui-$version
mkdir -p "$work/$folder/build/libs"
for entry in emberwood-tui install.sh VERSION LICENSE THIRD_PARTY.md README.md .editorconfig build.gradle.kts settings.gradle.kts gradle.properties gradlew gradlew.bat gradle src tools packaging docs; do
    cp -R -- "$entry" "$work/$folder/$entry"
done
find "$work/$folder/tools" -type d -name __pycache__ -prune -exec rm -rf -- {} +
cp build/libs/emberwood-tui.jar "$work/$folder/build/libs/"
touch "$work/$folder/.release-build"
tar -czf "dist/$folder.tar.gz" -C "$work" "$folder"
cp install.sh dist/install.sh
cd dist
if command -v sha256sum >/dev/null; then sha256sum "$folder.tar.gz" install.sh > SHA256SUMS
else shasum -a 256 "$folder.tar.gz" install.sh > SHA256SUMS; fi
printf '%s\n' "Ready: dist/$folder.tar.gz, dist/install.sh, dist/SHA256SUMS"
