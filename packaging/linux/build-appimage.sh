#!/usr/bin/env bash
#
# Builds an AppImage from the jpackage output.
#
# The AppImage is the least sandboxed of the three Linux formats — it is a self-contained
# filesystem image with no confinement of its own — so it is offered for distributions the deb and
# rpm do not cover, not as the recommended way to install. Prefer the Flatpak where you can: it is
# the only one of the three where Aegis genuinely cannot open a socket.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
out="$root/build/appimage"
app="$root/desktop/build/compose/binaries/main/app/aegis"

if [ ! -d "$app" ]; then
    echo "Run ./gradlew :desktop:createDistributable first" >&2
    exit 1
fi

rm -rf "$out"
mkdir -p "$out/AppDir/usr"
cp -r "$app"/* "$out/AppDir/usr/"

install -Dm644 "$root/packaging/linux/com.beemdevelopment.aegis.desktop" \
    "$out/AppDir/com.beemdevelopment.aegis.desktop"
install -Dm644 "$root/packaging/linux/com.beemdevelopment.aegis.metainfo.xml" \
    "$out/AppDir/usr/share/metainfo/com.beemdevelopment.aegis.metainfo.xml"
install -Dm644 "$root/desktop/src/main/resources/icons/aegis.png" \
    "$out/AppDir/com.beemdevelopment.aegis.png"

cat > "$out/AppDir/AppRun" <<'RUN'
#!/bin/sh
ulimit -c 0 2>/dev/null || true
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/bin/aegis" "$@"
RUN
chmod +x "$out/AppDir/AppRun"

if ! command -v appimagetool >/dev/null 2>&1; then
    echo "appimagetool is not installed; AppDir prepared at $out/AppDir" >&2
    exit 0
fi

# ARCH and a fixed mtime keep the output reproducible.
ARCH=x86_64 SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-0}" \
    appimagetool "$out/AppDir" "$out/Aegis-x86_64.AppImage"
echo "Wrote $out/Aegis-x86_64.AppImage"
