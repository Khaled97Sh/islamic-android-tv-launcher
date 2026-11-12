#!/bin/sh
# One-step install: puts the launcher on the TV, grants its permissions and makes the Home button open it.
#
#   tools/install.sh <tv-ip> [path/to/app.apk]   install (APK: the given file, else your local release build,
#                                                else the latest GitHub release is downloaded)
#   tools/install.sh --undo <tv-ip>              give the Home button back to the stock Google TV home
#
# Needs adb on your computer and USB debugging switched on in the TV's developer options.
set -eu

PKG=com.khaled.tvlauncher
REPO=Khaled97Sh/islamic-android-tv-launcher
# Stock homes that outrank any installed launcher. Only the ones present on the TV are touched.
STOCK_HOMES="com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith com.google.android.tvlauncher"

usage() { sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }
say() { printf '%s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

UNDO=0
[ "${1:-}" = "--undo" ] && { UNDO=1; shift; }
[ -n "${1:-}" ] || usage
case "$1" in *:*) TV="$1" ;; *) TV="$1:5555" ;; esac
APK="${2:-}"

command -v adb >/dev/null 2>&1 || die "adb not found. Install Android platform-tools first."

tv() { adb -s "$TV" shell "$@" 2>&1 | tr -d '\r'; }
installed() { [ -n "$(tv pm path "$1" | grep '^package:' || true)" ]; }
home_now() {
    tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
}

adb connect "$TV" >/dev/null 2>&1 || true
STATE="$(adb -s "$TV" get-state 2>&1 || true)"
case "$STATE" in
    device) ;;
    *unauthorized*) die "the TV has not authorized this computer. Accept the 'Allow USB debugging' box on the TV, then run again." ;;
    *) die "cannot reach $TV. Check the TV IP, that both are on the same network, and that USB debugging is on." ;;
esac

if [ "$UNDO" = 1 ]; then
    for p in $STOCK_HOMES; do
        if installed "$p"; then tv pm enable "$p" >/dev/null; say "enabled $p"; fi
    done
    say "Home button now opens: $(home_now)"
    exit 0
fi

# 1. Find the APK.
if [ -z "$APK" ]; then
    LOCAL="$(dirname "$0")/../app/build/outputs/apk/release/app-release.apk"
    if [ -f "$LOCAL" ]; then
        APK="$LOCAL"
    else
        command -v curl >/dev/null 2>&1 || die "no APK given and curl not found. Pass the APK path as the second argument."
        say "No local build found, downloading the latest release (about 85 MB)..."
        URL="$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" |
            grep -o '"browser_download_url": *"[^"]*\.apk"' | head -1 | sed 's/.*"\(https[^"]*\)"/\1/')"
        [ -n "$URL" ] || die "could not find an APK in the latest release. Download it from https://github.com/$REPO/releases and pass its path."
        APK="${TMPDIR:-/tmp}/islamic-tv-launcher-latest.apk"
        curl -fL --progress-bar -o "$APK" "$URL" || die "download failed."
    fi
fi
[ -f "$APK" ] || die "APK not found: $APK"

# 2. Install and grant the two permissions the adhan needs.
say "Installing $(basename "$APK")..."
OUT="$(adb -s "$TV" install -r "$APK" 2>&1)" || true
case "$OUT" in
    *Success*) ;;
    *INSTALL_FAILED_UPDATE_INCOMPATIBLE*) die "a build signed with another key is already on the TV. Run 'adb -s $TV uninstall $PKG' (this clears your widget choices), then run again." ;;
    *) die "install failed: $OUT" ;;
esac
tv appops set "$PKG" SYSTEM_ALERT_WINDOW allow >/dev/null
tv appops set "$PKG" SCHEDULE_EXACT_ALARM allow >/dev/null

# 3. Home button: the stock home outranks any app, so switch it off and take the Home role.
#    Done only after the install succeeded, so the TV is never left without a home screen.
for p in $STOCK_HOMES; do
    if installed "$p"; then tv pm disable-user --user 0 "$p" >/dev/null; say "disabled stock home $p"; fi
done
tv cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null || true

# 4. Open it once (arms the first adhan alarm) and check who owns Home.
tv am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
HOME_NOW="$(home_now)"
case "$HOME_NOW" in
    "$PKG"/*)
        say "Done. The Home button opens the launcher."
        say "Undo any time with: $0 --undo ${TV%:5555}"
        ;;
    android/*)
        say ""
        say "Installed. The TV is now asking which home app to use:"
        say "press Home on the remote, choose 'Islamic Tv Launcher' and pick 'Always'."
        ;;
    *)
        say ""
        say "Installed, but the Home button still opens: $HOME_NOW"
        say "This TV uses a home app this script does not know, so it was left alone."
        say "If that package is the TV maker's home screen (not Settings or a system package), hand Home over with:"
        say "  adb -s $TV shell pm disable-user --user 0 ${HOME_NOW%%/*}"
        say "  adb -s $TV shell cmd role add-role-holder --user 0 android.app.role.HOME $PKG"
        say "and please report the package name at https://github.com/$REPO/issues"
        exit 2
        ;;
esac
